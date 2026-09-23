package com.rox.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SaveFileFormatTest {
    private static final int PRG_RAM_SIZE = 0x2000;

    private static int[] prgRamWithValueAt(final int index, final int value){
        final int[] prgRam = new int[PRG_RAM_SIZE];
        prgRam[index] = value;
        return prgRam;
    }

    @Test
    public void writeThenReadRoundTripsPrgRamAndMetadata(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.ofEpochMilli(5_000), 12_000);
        final int[] prgRam = prgRamWithValueAt(2, 0x42);

        SaveFileFormat.writeBatterySave(saveFile, metadata, prgRam);
        final Optional<BatterySaveFile> read = SaveFileFormat.readBatterySave(saveFile);

        assertTrue(read.isPresent());
        assertEquals(metadata, read.get().metadata());
        assertArrayEquals(prgRam, read.get().prgRam());
    }

    @Test
    public void readBatterySaveIsEmptyWhenTheFileDoesNotExist(@TempDir final Path tempDir){
        assertEquals(Optional.empty(), SaveFileFormat.readBatterySave(tempDir.resolve("nonexistent.sav")));
    }

    @Test
    public void readBatterySaveIsEmptyForACorruptedFile(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        Files.write(saveFile, new byte[]{1, 2, 3});

        assertEquals(Optional.empty(), SaveFileFormat.readBatterySave(saveFile));
    }

    @Test
    public void readBatterySaveIsEmptyForAFileOfTheWrongSaveType(@TempDir final Path tempDir) throws IOException {
        //payload is exactly PRG_RAM_SIZE, same as a real battery save - isolates the type check from
        //the separate size check below it, so a mutant breaking just the type filter can't hide behind
        //the size filter also (correctly, coincidentally) rejecting an undersized payload
        final Path saveFile = tempDir.resolve("latest.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.LIVE_SNAPSHOT, Instant.now(), 0);
        Files.write(saveFile, SaveCodec.encode(SaveType.LIVE_SNAPSHOT, metadata, new byte[PRG_RAM_SIZE]));

        assertEquals(Optional.empty(), SaveFileFormat.readBatterySave(saveFile));
    }

    @Test
    public void readBatterySaveIsEmptyForAPayloadOfTheWrongSize(@TempDir final Path tempDir) throws IOException {
        //a CRC-valid file whose payload isn't exactly PRG_RAM_SIZE (corruption, or a future format
        //change) must be rejected here, not crash deep inside Mapper.restorePrgRam()'s own length check
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);
        Files.write(saveFile, SaveCodec.encode(SaveType.BATTERY_PRG_RAM, metadata, new byte[]{0x01, 0x02, 0x03}));

        assertEquals(Optional.empty(), SaveFileFormat.readBatterySave(saveFile));
    }

    @Test
    public void writeBatterySaveRejectsTheWrongPrgRamSize(@TempDir final Path tempDir){
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);

        assertThrows(IllegalArgumentException.class, () -> SaveFileFormat.writeBatterySave(saveFile, metadata, new int[]{0x01}));
    }

    @Test
    public void writeCreatesParentDirectoriesThatDoNotYetExist(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("loz").resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);

        SaveFileFormat.writeBatterySave(saveFile, metadata, new int[PRG_RAM_SIZE]);

        assertTrue(Files.exists(saveFile));
    }

    @Test
    public void prgRamBytesAreMaskedTo0To255(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);

        SaveFileFormat.writeBatterySave(saveFile, metadata, prgRamWithValueAt(0, 0xFF));
        final BatterySaveFile read = SaveFileFormat.readBatterySave(saveFile).orElseThrow();

        assertEquals(0xFF, read.prgRam()[0]);
    }
}
