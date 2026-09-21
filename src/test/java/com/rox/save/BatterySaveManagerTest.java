package com.rox.save;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.INesRom;
import com.rox.cartridge.NromMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BatterySaveManagerTest {
    private static final int PRG_RAM_SIZE = 0x2000;

    private static Cartridge blankCartridge(){
        final byte[] fileBytes = new byte[16 + 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 0x01;
        final INesRom rom = INesRom.parse(fileBytes);
        return new Cartridge(rom, new NromMapper(rom));
    }

    /** No mock hook exists for "a background thread finished writing a file" - a bounded poll is the next best thing, matching NESTest's own precedent for cases with nothing to verify() against. */
    private static void awaitFileExists(final Path path) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 2000;
        while (!Files.exists(path)){
            if (System.currentTimeMillis() > deadline){
                fail("expected " + path + " to be written within 2 seconds");
            }
            Thread.sleep(10);
        }
    }

    private static Thread findFlushThread(){
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> "BatterySaveManager-flush".equals(t.getName()))
                .findFirst()
                .orElse(null);
    }

    @Test
    public void aWriteToPrgRamIsFlushedToDiskWithoutAnyExplicitSaveAction(@TempDir final Path tempDir) throws Exception {
        final Cartridge cartridge = blankCartridge();
        final Path saveFile = tempDir.resolve("battery.sav");
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);

        cartridge.write(0x6000, 0x42);
        awaitFileExists(saveFile);

        final int[] expected = new int[PRG_RAM_SIZE];
        expected[0] = 0x42;
        assertArrayEquals(expected, SaveFileFormat.readBatterySave(saveFile).orElseThrow().prgRam());

        manager.stop();
    }

    @Test
    public void stopFlushesAPendingWriteBeforeReturning(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();
        final Path saveFile = tempDir.resolve("battery.sav");
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);

        cartridge.write(0x6001, 0x77);
        manager.stop();

        assertTrue(Files.exists(saveFile), "the write before stop() must still make it to disk");
        final int[] expected = new int[PRG_RAM_SIZE];
        expected[1] = 0x77;
        assertArrayEquals(expected, SaveFileFormat.readBatterySave(saveFile).orElseThrow().prgRam());
    }

    @Test
    public void stopWithNoPendingWriteDoesNotCreateAFile(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();
        final Path saveFile = tempDir.resolve("battery.sav");
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);

        manager.stop();

        assertTrue(Files.notExists(saveFile), "no write ever happened, so nothing should have been flushed");
    }

    @Test
    public void loadIfPresentRestoresPrgRamFromAnExistingSave(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        final int[] prgRam = new int[PRG_RAM_SIZE];
        prgRam[5] = 0x99;
        SaveFileFormat.writeBatterySave(saveFile, new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0), prgRam);
        final Cartridge cartridge = blankCartridge();

        BatterySaveManager.loadIfPresent(saveFile, cartridge);

        assertArrayEquals(prgRam, cartridge.prgRam());
    }

    @Test
    public void loadIfPresentIsANoOpWhenNoSaveExists(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();

        BatterySaveManager.loadIfPresent(tempDir.resolve("nonexistent.sav"), cartridge);

        assertArrayEquals(new int[PRG_RAM_SIZE], cartridge.prgRam());
    }

    @Test
    public void startReusesAnExistingSavesCreationTimeAndAccumulatedGameTime(@TempDir final Path tempDir) throws Exception {
        final Path saveFile = tempDir.resolve("battery.sav");
        final Instant originalCreatedAt = Instant.ofEpochMilli(123_456_000);
        SaveFileFormat.writeBatterySave(saveFile,
                new SaveMetadata(SaveType.BATTERY_PRG_RAM, originalCreatedAt, 60_000), new int[PRG_RAM_SIZE]);
        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);

        //a real, measurable gap between session start and the flush below - without this, a fast
        //in-memory run can measure 0ms elapsed, at which point "+ 0" and "- 0" are indistinguishable
        Thread.sleep(50);
        cartridge.write(0x6000, 0x01);
        awaitFileExists(saveFile);
        manager.stop();

        final SaveMetadata metadata = SaveFileFormat.readBatterySave(saveFile).orElseThrow().metadata();
        assertEquals(originalCreatedAt, metadata.createdAt(), "creation time must never change once a save exists");
        //an upper bound (not just >=) proves this session's elapsed time was genuinely ADDED to the
        //carried-over 60_000, not subtracted or otherwise miscalculated - 10s is generous slack for
        //however long this test actually took to run
        final long gameTimeMillis = metadata.accumulatedGameTimeMillis();
        assertTrue(gameTimeMillis >= 60_000 && gameTimeMillis < 70_000,
                "expected accumulated game time just over 60_000ms (the carried-over base plus this session's elapsed time), got " + gameTimeMillis);
    }

    @Test
    public void theFlushThreadStaysIdleUntilThereIsSomethingToFlush(@TempDir final Path tempDir) throws InterruptedException {
        final Cartridge cartridge = blankCartridge();
        final Path saveFile = tempDir.resolve("battery.sav");
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);
        try {
            Thread.sleep(300);
            assertTrue(Files.notExists(saveFile), "nothing was ever written - the flush thread must not flush spuriously while idle");

            cartridge.write(0x6000, 0x42);
            awaitFileExists(saveFile); //still works normally after sitting idle
        } finally {
            manager.stop();
        }
    }

    @Test
    public void theFlushThreadIsADaemonThread(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(tempDir.resolve("battery.sav"), cartridge);
        try {
            final Thread flushThread = findFlushThread();
            assertNotNull(flushThread, "expected to find the running BatterySaveManager-flush thread");
            assertTrue(flushThread.isDaemon(), "the flush thread must be a daemon thread so it never blocks JVM exit");
        } finally {
            manager.stop();
        }
    }

    @Test
    public void stopTerminatesTheFlushThreadPromptly(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(tempDir.resolve("battery.sav"), cartridge);
        final Thread flushThread = findFlushThread();
        assertNotNull(flushThread, "expected to find the running BatterySaveManager-flush thread");

        manager.stop();

        assertFalse(flushThread.isAlive(), "stop() must leave the flush thread terminated, not leaked and still waiting");
    }

    @Test
    public void aFailedFlushIsLoggedRatherThanCrashingTheFlushThread(@TempDir final Path tempDir) throws Exception {
        final Path saveDir = Files.createDirectory(tempDir.resolve("savedir"));
        final Path saveFile = saveDir.resolve("battery.sav");
        assumeTrue(saveDir.toFile().setWritable(false), "test requires being able to make a directory read-only (e.g. not running as root)");

        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);
        try {
            cartridge.write(0x6000, 0x42);
            Thread.sleep(200); //give the flush thread a chance to attempt (and fail) the write
            assertTrue(Files.notExists(saveFile), "the write must have failed against the read-only directory");

            saveDir.toFile().setWritable(true);
            cartridge.write(0x6001, 0x43);
            awaitFileExists(saveFile); //only succeeds if the flush thread survived the earlier failure
        } finally {
            saveDir.toFile().setWritable(true);
            manager.stop();
        }
    }

    @Test
    public void aFailedFlushIsRetriedOnceMoreDuringStopEvenWithNoFurtherWrite(@TempDir final Path tempDir) throws Exception {
        final Path saveDir = Files.createDirectory(tempDir.resolve("savedir"));
        final Path saveFile = saveDir.resolve("battery.sav");
        assumeTrue(saveDir.toFile().setWritable(false), "test requires being able to make a directory read-only (e.g. not running as root)");

        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(saveFile, cartridge);
        cartridge.write(0x6000, 0x42);
        Thread.sleep(200); //give the flush thread a chance to attempt (and fail) the write
        assertTrue(Files.notExists(saveFile), "the write must have failed against the read-only directory");

        saveDir.toFile().setWritable(true);
        manager.stop(); //no further write - stop() alone must retry the still-unsaved change

        assertTrue(Files.exists(saveFile), "the earlier failed flush must be retried once during stop(), even with no further write");
    }

    @Test
    public void stopHandlesBeingInterruptedWhileJoiningTheFlushThread(@TempDir final Path tempDir){
        final Cartridge cartridge = blankCartridge();
        final BatterySaveManager manager = BatterySaveManager.start(tempDir.resolve("battery.sav"), cartridge);

        Thread.currentThread().interrupt();
        try {
            manager.stop(); //must not throw or hang - just restores the interrupt flag
        } finally {
            assertTrue(Thread.interrupted(), "stop() must restore this thread's interrupt status, not swallow it");
        }
    }
}
