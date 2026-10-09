package com.rox.debug.report;

import com.rox.cartridge.NromMapperSnapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.save.LiveSaveFile;
import com.rox.save.SaveFileFormat;
import com.rox.save.SystemSnapshot;
import com.rox.save.SystemSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.rox.RecordAssertions.assertRecordsEqual;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebugReportWriterTest {
    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 10, 9, 14, 30, 5);

    private static int[] solidFrame(final int rgb){
        final int[] frame = new int[256 * 240];
        java.util.Arrays.fill(frame, rgb);
        return frame;
    }

    /** {@link SystemSnapshots#sample()} with RAM marked either side of the dumped $0000-$1FFF window. */
    private static SystemSnapshot snapshot(){
        final SystemSnapshot sample = SystemSnapshots.sample();
        final int[] ram = sample.ram().clone();
        ram[0x0000] = 0x11;
        ram[0x1FFF] = 0xAB;
        ram[0x2000] = 0xCD; //outside the dump - the PPU register range on a real bus
        return new SystemSnapshot(sample.romCrc32(), sample.cpu(), sample.ppu(), sample.apu(), ram, sample.mapper());
    }

    private static DebugReport report(final int[] screenshot, final SystemSnapshot snapshot, final Map<String, String> mapperState){
        final List<DisassembledInstruction> upcoming = List.of(
                new DisassembledInstruction(0x8123, 3, "JMP", new int[]{0x00, 0x90}, "JMP $9000"),
                new DisassembledInstruction(0x9000, 1, "INX", new int[0], "INX"));
        return new DebugReport(CAPTURED_AT, "loz.nes", "Link froze mid-swing", screenshot, solidFrame(0x123456),
                snapshot, mapperState, upcoming);
    }

    private static DebugReport report(){
        final Map<String, String> mapperState = new LinkedHashMap<>();
        mapperState.put("R0", "$02");
        mapperState.put("IRQ latch", "$20");
        return report(solidFrame(0xFF8000), snapshot(), mapperState);
    }

    private static int[] readBytes(final Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        final int[] values = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++){
            values[i] = bytes[i] & 0xFF;
        }
        return values;
    }

    @Test
    public void writesTheRestorableSnapshotNamedAfterTheFolder(@TempDir final Path tempDir) throws IOException {
        final Path folder = tempDir.resolve("debug-snapshot-20261009-143005");
        final DebugReport report = report();

        DebugReportWriter.write(folder, report);

        final Optional<LiveSaveFile> saved = SaveFileFormat.readLiveSnapshot(folder.resolve("debug-snapshot-20261009-143005.sav"));
        assertTrue(saved.isPresent());
        assertRecordsEqual(report.snapshot(), saved.get().snapshot());
        assertEquals(CAPTURED_AT.atZone(ZoneId.systemDefault()).toInstant(), saved.get().metadata().createdAt());
    }

    @Test
    public void writesBothFramesAsPngs(@TempDir final Path tempDir) throws IOException {
        DebugReportWriter.write(tempDir, report());

        final BufferedImage screenshot = ImageIO.read(tempDir.resolve("screenshot.png").toFile());
        final BufferedImage partial = ImageIO.read(tempDir.resolve("framebuffer-partial.png").toFile());
        assertEquals(256, screenshot.getWidth());
        assertEquals(240, screenshot.getHeight());
        assertEquals(0xFF8000, screenshot.getRGB(255, 239) & 0xFFFFFF);
        assertEquals(0x123456, partial.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    public void skipsTheScreenshotWhenNoFrameHadBeenShownYet(@TempDir final Path tempDir) throws IOException {
        DebugReportWriter.write(tempDir, report(null, snapshot(), Map.of()));

        assertFalse(Files.exists(tempDir.resolve("screenshot.png")));
        assertTrue(Files.exists(tempDir.resolve("framebuffer-partial.png")));
    }

    @Test
    public void writesTheDescription(@TempDir final Path tempDir) throws IOException {
        DebugReportWriter.write(tempDir, report().withDescription("Bomb exploded twice — sound cut out"));

        assertEquals("Bomb exploded twice — sound cut out", Files.readString(tempDir.resolve("description.txt")));
    }

    @Test
    public void dumpsTheCpuRamWindowOnly(@TempDir final Path tempDir) throws IOException {
        DebugReportWriter.write(tempDir, report());

        final int[] ram = readBytes(tempDir.resolve("ram.bin"));
        assertEquals(0x2000, ram.length);
        assertEquals(0x11, ram[0x0000]);
        assertEquals(0xAB, ram[0x1FFF]);
        final String hex = Files.readString(tempDir.resolve("ram.hex.txt"));
        assertTrue(hex.startsWith("0000: 11 00"), hex.substring(0, 20));
        assertTrue(hex.endsWith("1FF0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 AB\n"));
    }

    @Test
    public void dumpsPpuAndMapperMemoryRaw(@TempDir final Path tempDir) throws IOException {
        final DebugReport report = report();

        DebugReportWriter.write(tempDir, report);

        assertArrayEquals(report.snapshot().ppu().oam(), readBytes(tempDir.resolve("oam.bin")));
        assertArrayEquals(report.snapshot().ppu().nametableRam(), readBytes(tempDir.resolve("nametables.bin")));
        assertArrayEquals(report.snapshot().ppu().paletteRam(), readBytes(tempDir.resolve("palette.bin")));
        assertArrayEquals(report.snapshot().mapper().prgRam(), readBytes(tempDir.resolve("prg-ram.bin")));
        assertArrayEquals(report.snapshot().mapper().chrRam(), readBytes(tempDir.resolve("chr-ram.bin")));
    }

    @Test
    public void skipsChrRamOnAChrRomBoard(@TempDir final Path tempDir) throws IOException {
        final SystemSnapshot sample = snapshot();
        final SystemSnapshot chrRomBoard = new SystemSnapshot(sample.romCrc32(), sample.cpu(), sample.ppu(), sample.apu(),
                sample.ram(), new NromMapperSnapshot(new int[0x2000], new int[0]));

        DebugReportWriter.write(tempDir, report(null, chrRomBoard, Map.of()));

        assertFalse(Files.exists(tempDir.resolve("chr-ram.bin")));
        assertTrue(Files.exists(tempDir.resolve("prg-ram.bin")));
    }

    @Test
    public void stateSummaryCoversCpuInstructionsPpuAndMapper(){
        final String state = DebugReportWriter.formatState(report());

        //CPU from SystemSnapshots.sample(): PC $8123 A 1 X 2 Y 3 SP $FD; N/B/I/C set; NMI pending, IRQ clear
        assertTrue(state.contains("ROM:      loz.nes\n"), state);
        assertTrue(state.contains("Captured: 2026-10-09 14:30:05\n"), state);
        assertTrue(state.contains("PC: $8123  A: $01  X: $02  Y: $03  SP: $FD\n"), state);
        assertTrue(state.contains("Flags: Nv-BdIzC"), state);
        assertTrue(state.contains("IRQ line: clear  NMI pending: yes\n"), state);
        assertTrue(state.contains("> $8123  JMP $9000\n  $9000  INX\n"), state);
        assertTrue(state.contains("PPUCTRL: $80  PPUMASK: $00  OAMADDR: $11\n"), state);
        assertTrue(state.contains("write toggle: 0\n"), state);
        assertTrue(state.contains("R0: $02\nIRQ latch: $20\n"), state);
    }

    @Test
    public void stateSummaryShowsTheOtherHalfOfEveryFlagAndToggle(){
        final SystemSnapshot sample = snapshot();
        final com.rox.cpu.mos6502.MOS6502Snapshot inverted = new com.rox.cpu.mos6502.MOS6502Snapshot(0, 0, 0, 0, 0, 0, 0, 0,
                false, true, false, true, false, true, false, true, false);
        final SystemSnapshot invertedCpu = new SystemSnapshot(sample.romCrc32(), inverted, sample.ppu(), sample.apu(),
                sample.ram(), sample.mapper());

        final String state = DebugReportWriter.formatState(report(null, invertedCpu, Map.of()));

        assertTrue(state.contains("Flags: nV-bDiZc"), state);
        assertTrue(state.contains("IRQ line: asserted  NMI pending: no\n"), state);
        assertTrue(state.contains("(fixed mapping, no bank registers)\n"), state);
    }

    @Test
    public void hexDumpPrefixesEveryRowWithItsAddressAndHandlesAShortLastRow(){
        final int[] bytes = new int[18];
        bytes[0] = 0x01;
        bytes[17] = 0xFF;

        assertEquals("0000: 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00\n0010: 00 FF\n", DebugReportWriter.hexDump(bytes));
    }
}
