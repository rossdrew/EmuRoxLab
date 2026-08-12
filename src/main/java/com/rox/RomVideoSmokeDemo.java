package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.video.SwingVideoOutput;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manual visual smoke test: loads a real {@code .nes} ROM file and shows its video output in a real
 * window for up to a fixed duration, or until the window is closed, whichever comes first - not a
 * unit test, run it directly and look.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.RomVideoSmokeDemo path/to/rom.nes [seconds]</pre>
 */
public final class RomVideoSmokeDemo {
    private static final int DEFAULT_RUN_SECONDS = 30;

    private RomVideoSmokeDemo(){
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1){
            System.err.println("Usage: RomVideoSmokeDemo <path-to-rom.nes> [seconds]");
            System.exit(1);
            return;
        }

        final Path romPath = Path.of(args[0]);
        final int runSeconds = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_RUN_SECONDS;
        if (runSeconds < 0){
            System.err.println("Seconds must be non-negative");
            System.exit(1);
            return;
        }

        final Cartridge cartridge = RomLoader.load(romPath);
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

        //outer try/finally so the window is always closed, even if NES construction itself throws
        //(e.g. no audio line available) - a visible, undisposed JFrame keeps a non-daemon EDT thread
        //alive, so skipping close() here would hang the JVM on exit instead of surfacing the error
        try {
            final NES nes = new NES(videoOutput, cartridge);

            System.out.println("Showing " + romPath + " for up to " + runSeconds + " seconds (close the window to stop early)...");
            final Thread nesThread = new Thread(nes::powerOn);
            nesThread.start();
            boolean interrupted = false;
            try {
                windowClosed.await(runSeconds, TimeUnit.SECONDS);
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
            videoOutput.close();
        }
        System.out.println("Done.");
    }
}
