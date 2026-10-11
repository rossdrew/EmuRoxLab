package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.debug.cpu.CpuDebugFrame;
import com.rox.debug.report.DebugReport;
import com.rox.debug.report.DebugReportWriter;
import com.rox.input.Controller;
import com.rox.input.ControllerConfigLoader;
import com.rox.input.ControllerConfiguration;
import com.rox.input.KeyboardController;
import com.rox.save.BatterySaveManager;
import com.rox.save.SavePaths;
import com.rox.video.SwingVideoOutput;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual visual smoke test: loads a real {@code .nes} ROM file and shows its video output in a real
 * window until the window is closed, or up to a fixed duration if one is given, whichever comes
 * first - not a unit test, run it directly and play.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.RomVideoSmokeDemo path/to/rom.nes [path/to/controllers.properties] [seconds]</pre>
 */
public final class RomVideoSmokeDemo {
    private RomVideoSmokeDemo(){
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1){
            System.err.println("Usage: RomVideoSmokeDemo <path-to-rom.nes> [path-to-controllers.properties] [seconds, or omit to run until the window is closed]");
            System.exit(1);
            return;
        }

        final Path romPath = Path.of(args[0]);
        final ControllerConfiguration controllers = loadControllers(args.length >= 2 ? args[1] : null);
        final Integer runSeconds = args.length >= 3 ? Integer.parseInt(args[2]) : null;
        if (runSeconds != null && runSeconds < 0){
            System.err.println("Seconds must be non-negative");
            System.exit(1);
            return;
        }

        final Cartridge cartridge = RomLoader.load(romPath);

        //mirrors inserting a cartridge whose battery was already charged - silent, no prompt, exactly
        //like real hardware. Only battery-backed carts pay any cost here (see Cartridge.write()'s
        //own no-op-by-default onPrgRamWrite hook)
        final BatterySaveManager batterySaveManager;
        if (cartridge.rom().hasBattery()){
            final Path batterySaveFile = SavePaths.batterySaveFile(romPath);
            BatterySaveManager.loadIfPresent(batterySaveFile, cartridge);
            batterySaveManager = BatterySaveManager.start(batterySaveFile, cartridge);
        } else {
            batterySaveManager = null;
        }

        final CountDownLatch windowClosed = new CountDownLatch(1);
        //Swing components must only be constructed on the EDT - see PpuDebugViewerDemo's own comment.
        //invokeLater + our own retried-on-interrupt latch, not invokeAndWait, since invokeAndWait's own
        //InterruptedException doesn't cancel the already-posted EDT task - the window could still get
        //constructed asynchronously afterward with nothing left to close it, leaking a visible JFrame
        final SwingVideoOutput[] videoOutputHolder = new SwingVideoOutput[1];
        final CountDownLatch videoOutputConstructed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            videoOutputHolder[0] = new SwingVideoOutput(windowClosed::countDown);
            videoOutputConstructed.countDown();
        });
        boolean constructionInterrupted = false;
        while (true){
            try {
                videoOutputConstructed.await();
                break;
            } catch (InterruptedException e){
                constructionInterrupted = true;
            }
        }
        final SwingVideoOutput videoOutput = videoOutputHolder[0];
        if (constructionInterrupted){
            Thread.currentThread().interrupt();
        }
        attachKeyboardControllers(videoOutput, controllers);
        //set on the EDT once the NES exists, disposed alongside the video window - a visible JFrame keeps
        //a non-daemon EDT alive, so leaving it would hang the JVM on exit
        final AtomicReference<CpuDebugFrame> cpuDebugFrameHolder = new AtomicReference<>();

        //outer try/finally so the window is always closed, even if NES construction itself throws
        //(e.g. no audio line available) - a visible, undisposed JFrame keeps a non-daemon EDT thread
        //alive, so skipping close() here would hang the JVM on exit instead of surfacing the error
        try {
            final NES nes = new NES(videoOutput, controllers, cartridge);
            SwingUtilities.invokeLater(() -> {
                final CpuDebugFrame cpuDebugFrame = new CpuDebugFrame(new NesCpuDebugSource(nes));
                cpuDebugFrameHolder.set(cpuDebugFrame);
                videoOutput.addToolButton("CPU state (F11)", KeyEvent.VK_F11, cpuDebugFrame::showWindow);
                videoOutput.addToolButton("Flag issue (F12)", KeyEvent.VK_F12, () -> flagIssue(nes, videoOutput, romPath));
            });

            System.out.println("Showing " + romPath
                    + (runSeconds != null ? " for up to " + runSeconds + " seconds" : "")
                    + " (close the window to stop" + (runSeconds != null ? " early" : "") + ")...");
            final Thread nesThread = new Thread(nes::powerOn);
            nesThread.start();
            boolean interrupted = false;
            try {
                if (runSeconds != null){
                    windowClosed.await(runSeconds, TimeUnit.SECONDS);
                } else {
                    windowClosed.await();
                }
            } catch (InterruptedException e){
                interrupted = true;
            } finally {
                nes.powerOff();
                //retry until nesThread has genuinely terminated - a single interrupted join() would
                //otherwise return early without actually waiting, leaving the emulation thread running
                while (nesThread.isAlive()){
                    try {
                        nesThread.join();
                    } catch (InterruptedException e){
                        interrupted = true;
                    }
                }
            }
            if (interrupted){
                Thread.currentThread().interrupt();
            }
        } finally {
            SwingUtilities.invokeLater(() -> {
                final CpuDebugFrame cpuDebugFrame = cpuDebugFrameHolder.get();
                if (cpuDebugFrame != null){
                    cpuDebugFrame.dispose();
                }
            });
            videoOutput.close();
            //only reached once the emulation thread (and thus all gamepad polling and cartridge
            //writes) has genuinely terminated - see the inner finally's join loop above. Nested in its
            //own finally so a thrown IOException from gamepad cleanup can't skip the save manager's
            //own shutdown-time flush
            try {
                ControllerConfigLoader.closeConnectedGamepads();
            } finally {
                if (batterySaveManager != null){
                    batterySaveManager.stop();
                }
            }
        }
        System.out.println("Done.");
    }

    /**
     * The "Flag issue" button/F12 (runs on the EDT): freeze the game, capture everything, ask what went
     * wrong, then unfreeze - and, unless cancelled, write it all to a {@code debug-snapshot-<timestamp>}
     * folder beside the ROM's saves.
     */
    private static void flagIssue(final NES nes, final SwingVideoOutput videoOutput, final Path romPath){
        //already paused from the CPU state window: capture there, and leave it paused afterwards
        final boolean alreadyPaused = nes.isPaused();
        if (!alreadyPaused && !nes.pause()){
            return; //nothing running to flag - not started yet, or already shutting down
        }
        final DebugReport report;
        try {
            final LocalDateTime capturedAt = LocalDateTime.now();
            final DebugReport captured = DebugCapture.capture(nes, romPath.getFileName().toString(),
                    videoOutput.lastPresentedFrame(), capturedAt);
            final Optional<String> description = videoOutput.askForIssueDescription();
            if (description.isEmpty()){
                return;
            }
            report = captured.withDescription(description.get());
        } finally {
            if (!alreadyPaused){
                nes.resume();
            }
        }
        //everything in the report is already a copy, so the game carries on while it's written - and
        //off the EDT, so slow storage can't freeze the window. Not a daemon thread: closing the window
        //mid-write still lets the write finish before the JVM exits
        new Thread(() -> writeFlaggedIssue(report, romPath, videoOutput), "flagged-issue-writer").start();
    }

    private static void writeFlaggedIssue(final DebugReport report, final Path romPath, final SwingVideoOutput videoOutput){
        try {
            final Path directory = SavePaths.reserveDebugSnapshotDirectory(romPath, report.capturedAt());
            DebugReportWriter.write(directory, report);
            System.out.println("Flagged issue saved to " + directory);
            SwingUtilities.invokeLater(() -> videoOutput.showStatus("flagged issue saved to " + directory.getFileName()));
        } catch (IOException e){
            final String message = "Couldn't write a flagged issue under " + SavePaths.saveDirectory(romPath) + ":\n" + e.getMessage();
            System.err.println(message);
            SwingUtilities.invokeLater(() -> videoOutput.showError("Couldn't save flagged issue", message));
        }
    }

    private static ControllerConfiguration loadControllers(final String configPathArg) throws IOException {
        if (configPathArg == null){
            System.out.println("No controller config given - running with no controller input (see controllers.properties.example)");
            return ControllerConfiguration.NONE;
        }
        return ControllerConfigLoader.load(Path.of(configPathArg));
    }

    private static void attachKeyboardControllers(final SwingVideoOutput videoOutput, final ControllerConfiguration controllers){
        final Controller[] players = {controllers.player1(), controllers.player2(), controllers.player3(), controllers.player4()};
        for (final Controller controller : players){
            if (controller instanceof KeyboardController keyboardController){
                videoOutput.addKeyListener(keyboardController);
            }
        }
    }
}
