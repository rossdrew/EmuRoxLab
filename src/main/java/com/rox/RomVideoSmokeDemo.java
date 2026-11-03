package com.rox;

import com.formdev.flatlaf.FlatDarkLaf;
import com.rox.cartridge.RomLoader;
import com.rox.debug.DockedDebugWindow;
import com.rox.debug.breakpoints.BreakpointsPanel;
import com.rox.debug.cpu.CpuStatePanel;
import com.rox.debug.labels.FileLabelStore;
import com.rox.debug.labels.LabelFileException;
import com.rox.debug.labels.LabelWatchModel;
import com.rox.debug.labels.LabelWatchPanel;
import com.rox.debug.labels.MemoryLabel;
import com.rox.debug.memory.MemoryViewerPanel;
import com.rox.debug.report.DebugReport;
import com.rox.debug.report.DebugReportWriter;
import com.rox.input.Controller;
import com.rox.input.ControllerConfigLoader;
import com.rox.input.ControllerConfiguration;
import com.rox.input.KeyboardController;
import com.rox.save.SavePaths;
import com.rox.roundabout.timing.FrameTimes;
import com.rox.roundabout.debugger.MemoryDebugger;
import com.rox.video.SwingVideoOutput;
import io.github.andrewauclair.moderndocking.ext.ui.DockingUI;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manual visual smoke test: loads a real {@code .nes} ROM file and shows its video output in a real
 * window until the window is closed, or up to a fixed duration if one is given, whichever comes
 * first - not a unit test, run it directly and play.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.RomVideoSmokeDemo path/to/rom.nes [path/to/controllers.properties] [seconds]</pre>
 */
public final class RomVideoSmokeDemo {
    private static final int CPU_STATE_REFRESH_MILLIS = 100;
    //slower than the CPU view: a region can be 32KB, and memory changes are easier to follow at this pace
    private static final int MEMORY_VIEWER_REFRESH_MILLIS = 250;
    /** Frame intervals each Roundabout frame-timing graph keeps: ten seconds at 60 fps. */
    private static final int ROUNDABOUT_FRAME_HISTORY = 600;
    private static final float ROUNDABOUT_FRAME_BUDGET_MS = 1000 / 60f;
    private static final List<String> ROM_MENU = List.of("File", "Open", "ROM");
    private static final List<String> DEBUG_MENU = List.of("Debug");

    private final SwingVideoOutput videoOutput;
    /** frames finished by the emulation and frames drawn by Swing, for Roundabout's frame-timing graphs */
    private final List<FrameTimes> frameTimings;
    private final GameSwitcher switcher;
    /** greyed out while no game is loaded - EDT only */
    private final List<JMenuItem> debugItems = new ArrayList<>();
    /** where Browse starts when no game is loaded: the last folder a ROM was opened from - EDT only */
    private Path lastRomDirectory;
    /** created with the first game, kept for later ones - EDT only */
    private DockedDebugWindow debugger;
    /** Roundabout was open when the last game closed, so opens again on the next */
    private volatile boolean reopenRoundabout;
    /** the Roundabout memory debugger, while one is open - its own GLFW window on its own thread */
    private final AtomicReference<OpenRoundabout> roundabout = new AtomicReference<>();

    private record OpenRoundabout(MemoryDebugger debugger, Thread thread) {
    }

    private RomVideoSmokeDemo(final SwingVideoOutput videoOutput, final ControllerConfiguration controllers,
                              final List<FrameTimes> frameTimings){
        this.videoOutput = videoOutput;
        this.frameTimings = frameTimings;
        this.switcher = new GameSwitcher(RomLoader::load,
                (rom, cartridge) -> GameSession.start(rom, cartridge, c -> new NES(videoOutput, controllers, c)),
                new GameSwitcher.Listener(){
                    @Override
                    public void closing(final GameSession session){
                        reopenRoundabout = closeRoundabout();
                        SwingUtilities.invokeLater(() -> {
                            debugItems.forEach(item -> item.setEnabled(false));
                            videoOutput.showGame(null);
                        });
                    }

                    @Override
                    public void started(final GameSession session){
                        gameStarted(session);
                    }
                });
    }

    static void main(final String[] args) throws Exception {
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

        final CountDownLatch windowClosed = new CountDownLatch(1);
        //Swing components must only be constructed on the EDT - see PpuDebugViewerDemo's own comment.
        //invokeLater + our own retried-on-interrupt latch, not invokeAndWait, since invokeAndWait's own
        //InterruptedException doesn't cancel the already-posted EDT task - the window could still get
        //constructed asynchronously afterward with nothing left to close it, leaking a visible JFrame
        final SwingVideoOutput[] videoOutputHolder = new SwingVideoOutput[1];
        final CountDownLatch videoOutputConstructed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            //modern UI prototype: dark theme for every window, set before any of them exists
            FlatDarkLaf.setup();
            DockingUI.initialize();
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
        final List<FrameTimes> frameTimings = List.of(new FrameTimes("emu frames", ROUNDABOUT_FRAME_HISTORY,
                ROUNDABOUT_FRAME_BUDGET_MS), new FrameTimes("shown frames", ROUNDABOUT_FRAME_HISTORY, ROUNDABOUT_FRAME_BUDGET_MS));
        videoOutput.onFrameTimes(frameTimings.get(0)::record, frameTimings.get(1)::record);
        final RomVideoSmokeDemo app = new RomVideoSmokeDemo(videoOutput, controllers, frameTimings);

        //always close the window, even if something here throws - a visible, undisposed JFrame keeps a
        //non-daemon EDT thread alive, so the JVM would hang on exit instead of surfacing the error
        try {
            SwingUtilities.invokeLater(() -> {
                app.addFileMenu();
                app.addDebugMenu();
                app.openRom(romPath);
            });
            System.out.println("Showing " + romPath
                    + (runSeconds != null ? " for up to " + runSeconds + " seconds" : "")
                    + " (close the window to stop" + (runSeconds != null ? " early" : "") + ")...");
            try {
                if (runSeconds != null){
                    windowClosed.await(runSeconds, TimeUnit.SECONDS);
                } else {
                    windowClosed.await();
                }
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        } finally {
            try {
                app.switcher.close();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (app.debugger != null){
                        app.debugger.dispose();
                    }
                });
                videoOutput.close();
                //only once the game has ended all gamepad polling
                ControllerConfigLoader.closeConnectedGamepads();
            }
        }
        System.out.println("Done.");
    }

    /** EDT only. */
    private void addFileMenu(){
        videoOutput.addMenuItem(ROM_MENU, "Browse...", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
                this::browseForRom);
    }

    /** File > Open > ROM > Browse...: pick a ROM, starting in the current one's folder. EDT only. */
    private void browseForRom(){
        final Path startIn = switcher.current().map(session -> session.romPath().toAbsolutePath().getParent())
                .orElse(lastRomDirectory);
        videoOutput.chooseFileToOpen("Open ROM", startIn, "NES ROMs (*.nes)", "nes").ifPresent(this::openRom);
    }

    /** EDT only: the debug tools, each acting on whichever game is loaded. */
    private void addDebugMenu(){
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "Breakpoints", key(KeyEvent.VK_F7), () -> showDebugPanel("breakpoints")));
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "Roundabout", key(KeyEvent.VK_F8), () -> withGame(this::openRoundabout)));
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "Watch", key(KeyEvent.VK_F9), () -> showDebugPanel("watch")));
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "Memory", key(KeyEvent.VK_F10), () -> showDebugPanel("memory")));
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "CPU state", key(KeyEvent.VK_F11), () -> showDebugPanel("cpu")));
        debugItems.add(videoOutput.addMenuItem(DEBUG_MENU, "Flag issue", key(KeyEvent.VK_F12),
                () -> withGame(session -> flagIssue(session.nes(), videoOutput, session.romPath()))));
        debugItems.forEach(item -> item.setEnabled(false));
    }

    /** A menu item's accelerator still fires while it's greyed out, so each action checks too. */
    private void withGame(final Consumer<GameSession> action){
        switcher.current().ifPresent(action);
    }

    private void showDebugPanel(final String id){
        withGame(session -> debugger.showPanel(id));
    }

    /** EDT only: loads {@code rom} in place of the current game, or says why it couldn't. */
    private void openRom(final Path rom){
        lastRomDirectory = rom.toAbsolutePath().getParent();
        try {
            switcher.open(rom);
        } catch (IOException | IllegalArgumentException | LineUnavailableException e){
            System.err.println("Couldn't open " + rom + ": " + e.getMessage());
            videoOutput.showError("Couldn't open ROM", "Couldn't open " + rom.getFileName() + ":\n" + e.getMessage());
        }
    }

    /** EDT (via {@link #openRom}): hooks the debug tools up to the new game, as they were for the last. */
    private void gameStarted(final GameSession session){
        final List<DockedDebugWindow.Panel> panels = debuggerPanels(session);
        final boolean firstGame = debugger == null;
        if (firstGame){
            debugger = new DockedDebugWindow("EmuRoxLab - Debugger", panels);
        } else {
            debugger.replacePanels(panels);
        }
        debugItems.forEach(item -> item.setEnabled(true));
        videoOutput.showGame(session.romPath().getFileName().toString());
        if (reopenRoundabout || firstGame && Boolean.getBoolean("roundabout.open")){
            openRoundabout(session);
        }
    }

    private static List<DockedDebugWindow.Panel> debuggerPanels(final GameSession session){
        final NES nes = session.nes();
        final Path romPath = session.romPath();
        final CpuStatePanel cpuStatePanel = new CpuStatePanel(new NesCpuDebugSource(nes));
        final MemoryViewerPanel memoryViewerPanel = new MemoryViewerPanel(new NesMemoryDebugSource(nes));
        final LabelWatchPanel labelWatchPanel = new LabelWatchPanel(new LabelWatchModel(
                new FileLabelStore(SavePaths.labelsFile(romPath)), new NesLabelWatchTarget(nes)));
        final BreakpointsPanel breakpointsPanel = new BreakpointsPanel(nes.breakpoints(), nes::lastHit,
                new FileLabelStore(SavePaths.labelsFile(romPath)));
        return List.of(
                new DockedDebugWindow.Panel("cpu", "CPU state", cpuStatePanel, cpuStatePanel::refresh, CPU_STATE_REFRESH_MILLIS),
                new DockedDebugWindow.Panel("memory", "Memory", memoryViewerPanel, memoryViewerPanel::refresh, MEMORY_VIEWER_REFRESH_MILLIS),
                new DockedDebugWindow.Panel("watch", "Watch", labelWatchPanel, labelWatchPanel::refresh, MEMORY_VIEWER_REFRESH_MILLIS),
                new DockedDebugWindow.Panel("breakpoints", "Breakpoints", breakpointsPanel, breakpointsPanel::refresh,
                        CPU_STATE_REFRESH_MILLIS));
    }

    /**
     * Debug > Flag issue (runs on the EDT): freeze the game, capture everything, ask what went
     * wrong, then unfreeze - and, unless cancelled, write it all to a {@code debug-snapshot-<timestamp>}
     * folder beside the ROM's saves.
     */
    private static void flagIssue(final NES nes, final SwingVideoOutput videoOutput, final Path romPath){
        //already paused from the CPU state window: capture there, and leave it paused afterwards
        final boolean alreadyPaused = nes.isPaused();
        //at a frame's end, so the captured state matches the screenshot
        if (!alreadyPaused && !nes.pause(NES.PauseAt.END_OF_FRAME)){
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

    /**
     * Debug > Roundabout: opens the Roundabout memory debugger on {@code session}'s game, watching its saved
     * labels - at most one at a time. A daemon thread, so an open debugger never keeps the JVM alive once
     * the game window has closed.
     */
    private void openRoundabout(final GameSession session){
        final Path romPath = session.romPath();
        List<MemoryLabel> labels;
        try {
            labels = new FileLabelStore(SavePaths.labelsFile(romPath)).load();
        } catch (IOException | LabelFileException e){
            System.err.println("Roundabout: not watching saved labels - " + e.getMessage());
            labels = List.of();
        }
        final MemoryDebugger debugger = new MemoryDebugger(new NesRoundaboutTarget(session.nes(), labels, frameTimings),
                "EmuRoxLab - Roundabout - " + romPath.getFileName());
        final Thread thread = new Thread(debugger::run, "roundabout");
        thread.setDaemon(true);
        final OpenRoundabout open = new OpenRoundabout(debugger, thread);
        if (!roundabout.compareAndSet(null, open)){
            return;
        }
        thread.start();
        //closed from its own window: free the slot for the next one
        final Thread watcher = new Thread(() -> {
            joinUninterruptibly(thread);
            roundabout.compareAndSet(open, null);
        }, "roundabout-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Closes Roundabout, if open, and waits for its window to go - each one starts and ends GLFW itself.
     *
     * @return whether one was open
     */
    private boolean closeRoundabout(){
        final OpenRoundabout open = roundabout.getAndSet(null);
        if (open == null){
            return false;
        }
        open.debugger().close();
        joinUninterruptibly(open.thread());
        return true;
    }

    private static void joinUninterruptibly(final Thread thread){
        boolean interrupted = false;
        while (thread.isAlive()){
            try {
                thread.join();
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
    }

    private static KeyStroke key(final int keyCode){
        return KeyStroke.getKeyStroke(keyCode, 0);
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
