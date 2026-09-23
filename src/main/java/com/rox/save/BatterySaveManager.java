package com.rox.save;

import com.rox.cartridge.Cartridge;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors real battery-backed SRAM: write-through, no timer, no user action - the disk file is
 * updated after every write to {@code $6000-$7FFF} ({@link Cartridge#setOnPrgRamWrite}), same as a
 * real battery-backed chip never losing a write. The actual disk write always happens on this
 * class's own background thread, never the emulation thread {@link Cartridge#write} runs on,
 * matching {@code SpeakerAudioOutput}'s own never-block-the-emulation-thread discipline - writes
 * arriving faster than disk I/O naturally coalesce onto the flush thread's next loop iteration
 * rather than blocking or queuing unboundedly.
 */
public final class BatterySaveManager {
    private static final Logger log = Logger.getLogger(BatterySaveManager.class.getName());

    private final Path saveFile;
    private final Cartridge cartridge;
    private final Object lock = new Object();
    private final Instant createdAt;
    private final long baseGameTimeMillis;
    private final Instant sessionStart = Instant.now();
    private volatile boolean running;
    private boolean pendingWrite;
    //true whenever prgRam has changes not yet successfully written to disk - deliberately separate
    //from pendingWrite (the wake signal): pendingWrite is always cleared before attempting a flush, so
    //a *failed* flush leaving it set would busy-loop retrying instead of going back to sleep. dirty is
    //only cleared once a flush genuinely succeeds, and is only re-attempted when either a later write
    //wakes the loop again, or once, right before shutdown, if nothing ever did
    private boolean dirty;
    private Thread flushThread;

    private BatterySaveManager(final Path saveFile, final Cartridge cartridge, final Instant createdAt, final long baseGameTimeMillis){
        this.saveFile = saveFile;
        this.cartridge = cartridge;
        this.createdAt = createdAt;
        this.baseGameTimeMillis = baseGameTimeMillis;
    }

    /**
     * If a battery save already exists for this cartridge, loads it straight into PRG-RAM - mirrors
     * inserting a cartridge whose battery was already charged. No-op if none exists yet.
     */
    public static void loadIfPresent(final Path saveFile, final Cartridge cartridge){
        SaveFileFormat.readBatterySave(saveFile).ifPresent(save -> cartridge.restorePrgRam(save.prgRam()));
    }

    /** Starts write-through persistence for {@code cartridge} to {@code saveFile}, reusing an existing save's creation timestamp/accumulated game time if one is already on disk. */
    public static BatterySaveManager start(final Path saveFile, final Cartridge cartridge){
        final Optional<BatterySaveFile> existing = SaveFileFormat.readBatterySave(saveFile);
        final Instant createdAt = existing.map(save -> save.metadata().createdAt()).orElseGet(Instant::now);
        final long baseGameTimeMillis = existing.map(save -> save.metadata().accumulatedGameTimeMillis()).orElse(0L);

        final BatterySaveManager manager = new BatterySaveManager(saveFile, cartridge, createdAt, baseGameTimeMillis);
        manager.running = true;
        cartridge.setOnPrgRamWrite(manager::onWrite);
        manager.flushThread = new Thread(manager::run, "BatterySaveManager-flush");
        //XXX a new Thread already inherits isDaemon() from its creator - a test asserting isDaemon()
        //true can only distinguish this call from a genuinely non-daemon *caller*, which a mutation-
        //testing harness's own test-execution thread isn't guaranteed to be; accepted, environment-
        //dependent gap, not a real behavioral difference `./gradlew test` itself would ever exercise
        manager.flushThread.setDaemon(true);
        manager.flushThread.start();
        return manager;
    }

    private void onWrite(){
        synchronized (lock){
            pendingWrite = true;
            dirty = true;
            lock.notifyAll();
        }
    }

    private void run(){
        while (true){
            synchronized (lock){
                while (running && !pendingWrite){
                    try {
                        lock.wait();
                    } catch (InterruptedException e){
                        //this thread isn't exposed for a test to interrupt directly, unlike stop()'s
                        //own join() below (interruptible via the *calling* thread) - accepted gap, same
                        //category as NES.powerOn()'s own defensive interrupt-restore branches
                        Thread.currentThread().interrupt();
                    }
                }
                if (!running && !pendingWrite){
                    if (dirty){
                        //shutting down with an earlier flush failure that nothing ever retried - one
                        //last attempt, not an unbounded retry loop
                        flush();
                    }
                    return;
                }
                pendingWrite = false;
            }
            flush();
        }
    }

    private void flush(){
        final int[] prgRam = cartridge.prgRam();
        final long gameTimeMillis = baseGameTimeMillis + Duration.between(sessionStart, Instant.now()).toMillis();
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, createdAt, gameTimeMillis);
        try {
            SaveFileFormat.writeBatterySave(saveFile, metadata, prgRam);
            synchronized (lock){
                dirty = false;
            }
        } catch (IOException e){
            log.log(Level.WARNING, "Could not write battery save to " + saveFile, e);
        }
    }

    /**
     * Stops write-through persistence, flushing one last time first if a write was still pending or an
     * earlier flush had failed. Never returns while that final flush is still in progress - retries the
     * join until the flush thread has genuinely terminated, the same interrupt-retry pattern
     * {@code NES.powerOn()} uses for its own lifecycle-critical thread, rather than a single bounded
     * join that could return early and let JVM shutdown kill a daemon thread mid-write.
     */
    public void stop(){
        synchronized (lock){
            running = false;
            lock.notifyAll();
        }
        boolean interrupted = false;
        //XXX removing the join() call itself is a hard-to-kill mutant: isAlive() alone still converges
        //to the same observable outcome (busy-spinning instead of blocking until the thread is genuinely
        //dead) - accepted, matches NES.powerOn()'s own identically-shaped retry-join loop
        while (flushThread.isAlive()){
            try {
                flushThread.join();
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
    }

    /** Test seam: direct access to this instance's own flush thread - avoids a name-based search across every thread in the JVM, fragile when more than one manager (e.g. across different tests) shares the same thread name. */
    Thread flushThreadForTesting(){
        return flushThread;
    }
}
