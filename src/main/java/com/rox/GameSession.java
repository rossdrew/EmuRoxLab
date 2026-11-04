package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.save.BatterySaveManager;
import com.rox.save.SavePaths;

import javax.sound.sampled.LineUnavailableException;
import java.nio.file.Path;

/**
 * One loaded game: its cartridge, battery saver and {@link NES}, running on its own thread from
 * {@link #start} until {@link #close}. The game window and debugger outlive it, so changing ROM is one
 * close then one start - like swapping a cartridge with the power off.
 */
final class GameSession {
    /** Builds the {@link NES} for a cartridge. */
    @FunctionalInterface
    interface NesFactory {
        NES create(Cartridge cartridge) throws LineUnavailableException;
    }

    private final Path romPath;
    private final NES nes;
    private final Thread nesThread;
    /** {@code null} unless the cartridge is battery-backed */
    private final BatterySaveManager batterySaver;

    private GameSession(final Path romPath, final NES nes, final Thread nesThread, final BatterySaveManager batterySaver){
        this.romPath = romPath;
        this.nes = nes;
        this.nesThread = nesThread;
        this.batterySaver = batterySaver;
    }

    /**
     * Loads any battery save into {@code cartridge}, builds its {@code NES} and powers it on, on a thread
     * of its own.
     */
    static GameSession start(final Path romPath, final Cartridge cartridge, final NesFactory nesFactory)
            throws LineUnavailableException {
        //like inserting a cartridge whose battery was already charged - silent, no prompt
        final BatterySaveManager batterySaver;
        if (cartridge.rom().hasBattery()){
            final Path batterySaveFile = SavePaths.batterySaveFile(romPath);
            BatterySaveManager.loadIfPresent(batterySaveFile, cartridge);
            batterySaver = BatterySaveManager.start(batterySaveFile, cartridge);
        } else {
            batterySaver = null;
        }

        final NES nes;
        try {
            nes = nesFactory.create(cartridge);
        } catch (LineUnavailableException | RuntimeException e){
            if (batterySaver != null){
                batterySaver.stop();
            }
            throw e;
        }
        final Thread nesThread = new Thread(nes::powerOn, "nes-" + romPath.getFileName());
        nesThread.start();
        return new GameSession(romPath, nes, nesThread, batterySaver);
    }

    Path romPath(){
        return romPath;
    }

    NES nes(){
        return nes;
    }

    /**
     * Powers off and returns once the emulation thread has ended, then flushes the battery save - only
     * then can nothing write PRG-RAM any more.
     */
    void close(){
        nes.powerOff();
        boolean interrupted = false;
        //retry until nesThread has genuinely ended - an interrupted join() would otherwise return early
        while (nesThread.isAlive()){
            try {
                nesThread.join();
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        if (batterySaver != null){
            batterySaver.stop();
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
    }
}
