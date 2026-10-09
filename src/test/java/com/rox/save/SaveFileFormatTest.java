package com.rox.save;

import com.rox.apu.APU;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import com.rox.ppu.PPU;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static com.rox.RecordAssertions.assertRecordsEqual;
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

    // --- live snapshots ---

    /** A real (if mostly power-on) snapshot of every component, with some non-default values throughout. */
    private static SystemSnapshot systemSnapshot(){
        final byte[] fileBytes = new byte[16 + 2 * 0x4000];
        System.arraycopy(new byte[]{'N', 'E', 'S', 0x1A, 2, 0, 0x40, 0}, 0, fileBytes, 0, 8); //MMC3, CHR-RAM
        final Cartridge cartridge = RomLoader.fromBytes(fileBytes);
        cartridge.write(0x6000, 0x12);
        cartridge.write(0x8000, 0xC6);
        cartridge.write(0x8001, 0x05);
        cartridge.writeChr(0x0010, 0x34);
        final PPU ppu = new PPU(cartridge);
        ppu.write(0x2000, 0x80);
        ppu.write(0x2003, 0x10);
        ppu.write(0x2004, 0x56);
        for (int i = 0; i < 1000; i++){
            ppu.tick();
        }
        final RAM ram = new RAM(0x10000);
        ram.write(0x0123, 0x78);
        final APU apu = new APU(new MemoryBus8Bit(ram));
        apu.write(0x4015, 0x0F);
        apu.write(0x4003, 0x08);
        final MOS6502Snapshot cpu = new MOS6502Snapshot(0x8123, 1, 2, 3, 0xFD, 0x4C, 0x23, 0x81,
                true, false, true, false, true, false, true, false, true);
        return new SystemSnapshot(0xDEADBEEFL, cpu, ppu.snapshot(), apu.snapshot(), ram.snapshot(), cartridge.snapshot());
    }

    @Test
    public void writeThenReadRoundTripsALiveSnapshotAndMetadata(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("debug-snapshot-20261009-120000").resolve("debug-snapshot-20261009-120000.sav");
        final SaveMetadata metadata = new SaveMetadata(SaveType.LIVE_SNAPSHOT, Instant.ofEpochMilli(9_000), 0);
        final SystemSnapshot snapshot = systemSnapshot();

        SaveFileFormat.writeLiveSnapshot(saveFile, metadata, snapshot);
        final Optional<LiveSaveFile> read = SaveFileFormat.readLiveSnapshot(saveFile);

        assertTrue(read.isPresent());
        assertEquals(metadata, read.get().metadata());
        assertRecordsEqual(snapshot, read.get().snapshot());
    }

    @Test
    public void readLiveSnapshotIsEmptyWhenTheFileDoesNotExist(@TempDir final Path tempDir){
        assertTrue(SaveFileFormat.readLiveSnapshot(tempDir.resolve("missing.sav")).isEmpty());
    }

    @Test
    public void readLiveSnapshotIsEmptyForABatterySave(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("battery.sav");
        SaveFileFormat.writeBatterySave(saveFile, new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.EPOCH, 0), new int[PRG_RAM_SIZE]);

        assertTrue(SaveFileFormat.readLiveSnapshot(saveFile).isEmpty());
    }

    @Test
    public void readLiveSnapshotIsEmptyForAValidFileWhosePayloadIsNotASnapshot(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("odd.sav");
        Files.write(saveFile, SaveCodec.encode(SaveType.LIVE_SNAPSHOT, new SaveMetadata(SaveType.LIVE_SNAPSHOT, Instant.EPOCH, 0), new byte[]{1, 2, 3}));

        assertTrue(SaveFileFormat.readLiveSnapshot(saveFile).isEmpty());
    }

    @Test
    public void readBatterySaveIsEmptyForALiveSnapshot(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("live.sav");
        SaveFileFormat.writeLiveSnapshot(saveFile, new SaveMetadata(SaveType.LIVE_SNAPSHOT, Instant.EPOCH, 0), systemSnapshot());

        assertTrue(SaveFileFormat.readBatterySave(saveFile).isEmpty());
    }

    @Test
    public void readLiveSnapshotIsEmptyForASnapshotPayloadInAFileTaggedAsAnotherSaveType(@TempDir final Path tempDir) throws IOException {
        final Path saveFile = tempDir.resolve("mislabelled.sav");
        Files.write(saveFile, SaveCodec.encode(SaveType.BATTERY_PRG_RAM, new SaveMetadata(SaveType.BATTERY_PRG_RAM, Instant.EPOCH, 0),
                SnapshotCodec.encode(systemSnapshot())));

        assertTrue(SaveFileFormat.readLiveSnapshot(saveFile).isEmpty());
    }
}
