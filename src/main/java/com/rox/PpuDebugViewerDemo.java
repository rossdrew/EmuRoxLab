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

/**
 * Manual visual smoke test: loads a real {@code .nes} ROM file, runs it, and opens a live
 * {@link PpuDebugFrame} showing its CHR/nametable/OAM/register state - not a unit test, run it
 * directly and look. Runs until the window is closed.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.PpuDebugViewerDemo path/to/rom.nes</pre>
 */
public final class PpuDebugViewerDemo {
    private PpuDebugViewerDemo(){
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1){
            System.err.println("Usage: PpuDebugViewerDemo <path-to-rom.nes>");
            System.exit(1);
            return;
        }

        final Path romPath = Path.of(args[0]);
        final Cartridge cartridge = RomLoader.load(romPath);
        final NES nes = new NES(cartridge);

        final Thread nesThread = new Thread(nes::powerOn);
        nesThread.start();

        final CountDownLatch windowClosed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            final PpuDebugFrame frame = new PpuDebugFrame(nes.ppu(), cartridge);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter(){
                @Override
                public void windowClosing(final WindowEvent e){
                    windowClosed.countDown();
                }
            });
            frame.setVisible(true);
        });

        windowClosed.await();
        nes.powerOff();
        //retry until nesThread has genuinely terminated - a single interrupted join() would otherwise
        //return early without actually waiting, leaving the emulation thread running
        boolean interrupted = false;
        while (nesThread.isAlive()){
            try {
                nesThread.join();
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
    }
}
