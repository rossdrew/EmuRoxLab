package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.ppu.debug.PpuDebugFrame;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manual visual smoke test: opens a live {@link PpuDebugFrame} showing CHR/nametable/OAM/register
 * state for a running NES. A ROM can be given on the command line to load immediately, and/or picked
 * (or swapped for another) at any time via the window's File &gt; Open ROM... menu - not a unit test,
 * run it directly and look. Runs until the window is closed.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.PpuDebugViewerDemo [path/to/rom.nes]</pre>
 */
public final class PpuDebugViewerDemo {
    private PpuDebugViewerDemo(){
    }

    public static void main(final String[] args) throws Exception {
        final RunningNes runningNes = new RunningNes();
        final CountDownLatch windowClosed = new CountDownLatch(1);
        //Swing components must only be created/configured/shown from the EDT - constructing (and
        //pack()-ing) PpuDebugFrame here, then only touching it from invokeAndWait's callback, would
        //violate that; the holder is just how the reference gets back out to this thread afterward
        final PpuDebugFrame[] frameHolder = new PpuDebugFrame[1];
        //serializes loadRom() calls onto one thread: a game selected twice in quick succession (or a
        //CLI-arg load racing a menu selection) must not let two overlapping stop()/start() pairs
        //interleave and orphan a running NES - see loadRom()'s own doc for the exact race this closes
        final ExecutorService romLoaderExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "rom-loader"));

        SwingUtilities.invokeAndWait(() -> {
            final PpuDebugFrame frame = new PpuDebugFrame();
            frameHolder[0] = frame;
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter(){
                @Override
                public void windowClosing(final WindowEvent e){
                    frame.dispose();
                    windowClosed.countDown();
                }
            });
            frame.setOnOpenRom(romPath -> loadRom(romPath, frame, runningNes, romLoaderExecutor));
            frame.setVisible(true);
        });
        final PpuDebugFrame frame = frameHolder[0];

        if (args.length >= 1){
            loadRom(Path.of(args[0]), frame, runningNes, romLoaderExecutor);
        }

        windowClosed.await();
        //no more loads may be submitted past this point; block until any load that was already queued
        //or mid-flight has genuinely finished (including its own stop()+start() pair) before the final
        //stop() below - otherwise a load racing the window closing could start a NES that this stop()
        //never sees, leaking its thread and audio line forever
        romLoaderExecutor.shutdown();
        boolean interrupted = false;
        while (!romLoaderExecutor.isTerminated()){
            try {
                romLoaderExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
        runningNes.stop();
    }

    /**
     * Loads {@code romPath} and, only once that's succeeded, stops whatever ROM was previously running
     * (if any) and starts the new one in its place - a ROM that fails to parse must leave the previously
     * working NES running rather than killing it for nothing. Runs on {@code romLoaderExecutor} (a
     * single dedicated thread, off the EDT since both file parsing and {@code NES} construction, which
     * opens an audio line, can block) - serializing every load onto that one thread is what stops two
     * overlapping loads from interleaving their stop()/start() pairs and orphaning a running NES: {@link
     * RunningNes#start} doesn't itself stop whatever was previously running, so two concurrent callers
     * could otherwise both call stop() (the second a no-op) then both call start(), with the second
     * start() silently overwriting the first's still-running NES instead of replacing it. Reports
     * failure (bad file, unsupported mapper, ...) back to the window rather than throwing on this
     * background thread, where nothing would ever see it.
     */
    private static void loadRom(final Path romPath, final PpuDebugFrame frame, final RunningNes runningNes,
                                final ExecutorService romLoaderExecutor){
        romLoaderExecutor.submit(() -> {
            try {
                final Cartridge cartridge = RomLoader.load(romPath);
                final NES nes = new NES(cartridge);
                runningNes.stop();
                runningNes.start(nes);
                SwingUtilities.invokeLater(() -> frame.rebind(nes.ppu(), cartridge));
            } catch (Exception e){
                SwingUtilities.invokeLater(() -> frame.showLoadError(romPath, e));
            }
        });
    }

    /** Owns whichever {@link NES} is currently running, plus its emulation thread - swappable at runtime via the debug UI's file picker. */
    private static final class RunningNes {
        private NES nes;
        private Thread thread;

        synchronized void start(final NES nes){
            this.nes = nes;
            thread = new Thread(nes::powerOn, "nes-emulation");
            thread.start();
        }

        synchronized void stop(){
            if (nes == null){
                return;
            }
            nes.powerOff();
            //retry until the thread has genuinely terminated - a single interrupted join() would
            //otherwise return early without actually waiting, leaving the emulation thread running
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
            nes = null;
            thread = null;
        }
    }
}
