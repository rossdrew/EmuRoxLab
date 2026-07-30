package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;

import java.nio.file.Path;

/**
 * Manual audible smoke test: loads a real {@code .nes} ROM file and plays its audio output through
 * the system speaker for a fixed duration - not a unit test, run it directly and listen.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.RomAudioSmokeDemo path/to/rom.nes [seconds]</pre>
 */
public final class RomAudioSmokeDemo {
    private static final int DEFAULT_RUN_SECONDS = 30;

    private RomAudioSmokeDemo(){
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1){
            System.err.println("Usage: RomAudioSmokeDemo <path-to-rom.nes> [seconds]");
            System.exit(1);
            return;
        }

        final Path romPath = Path.of(args[0]);
        final int runSeconds = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_RUN_SECONDS;

        final Cartridge cartridge = RomLoader.load(romPath);
        final NES nes = new NES(cartridge);

        System.out.println("Playing " + romPath + " for " + runSeconds + " seconds...");
        final Thread nesThread = new Thread(nes::powerOn);
        nesThread.start();
        try {
            Thread.sleep(runSeconds * 1000L);
        } finally {
            nes.powerOff();
            //retry until nesThread has genuinely terminated - a single interrupted join() would
            //otherwise return early without actually waiting, leaving the emulation thread running
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
        System.out.println("Done.");
    }
}
