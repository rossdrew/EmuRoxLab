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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SaveFileFormatTest {

    @Test
    public void writeThenReadRoundTripsPrgRamAndMetadata(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.ofEpochMilli(5_000), 12_000);
        final int[] prgRam = {0x00, 0xFF, 0x42, 0x7F};

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
        final Path saveFile = tempDir.resolve("latest.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.LIVE_SNAPSHOT, Instant.now(), 0);
        Files.write(saveFile, SaveCodec.encode(SaveType.LIVE_SNAPSHOT, metadata, new byte[]{0x01}));

        assertEquals(Optional.empty(), SaveFileFormat.readBatterySave(saveFile));
    }

    @Test
    public void writeCreatesParentDirectoriesThatDoNotYetExist(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("loz").resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);

        SaveFileFormat.writeBatterySave(saveFile, metadata, new int[]{0x01});

        assertTrue(Files.exists(saveFile));
    }

    @Test
    public void prgRamBytesAreMaskedTo0To255(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.now(), 0);

        SaveFileFormat.writeBatterySave(saveFile, metadata, new int[]{0xFF});
        final BatterySaveFile read = SaveFileFormat.readBatterySave(saveFile).orElseThrow();

        assertArrayEquals(new int[]{0xFF}, read.prgRam());
    }
}
