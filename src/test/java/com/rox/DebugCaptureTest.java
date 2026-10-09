package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.clock.Clock;
import com.rox.debug.report.DebugReport;
import com.rox.debug.report.DebugReportWriter;
import com.rox.save.SaveFileFormat;
import com.rox.save.SavePaths;
import com.rox.save.SystemSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.IntUnaryOperator;

import static com.rox.RecordAssertions.assertRecordsEqual;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DebugCaptureTest {
    private static final int PRG_ROM_SIZE = 0x4000;
    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 10, 9, 15, 0, 0);

    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000. PRG byte at $8000 (and its $C000 mirror) is $42. */
    private static Cartridge cartridge(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        fileBytes[header.length] = 0x42;
        final byte[] program = {(byte) 0xE8, (byte) 0x86, 0x10, 0x4C, 0x00, (byte) 0x90};
        System.arraycopy(program, 0, fileBytes, header.length + 0x1000, program.length);
        fileBytes[header.length + 0x3FFC] = 0x00;
        fileBytes[header.length + 0x3FFD] = (byte) 0x90;
        return RomLoader.fromBytes(fileBytes);
    }

    @Test
    public void captureDisassemblesForwardFromTheCpusPc(){
        final NES nes = new NES(mock(AudioOutput.class), cartridge(), new ManuallyTickedClock());
        nes.cpu().reset(); //PC -> $9000

        final DebugReport report = DebugCapture.capture(nes, "test.nes", null, CAPTURED_AT);

        assertEquals(DebugCapture.UPCOMING_INSTRUCTION_COUNT, report.upcomingInstructions().size());
        assertEquals("INX", report.upcomingInstructions().get(0).formatted());
        assertEquals("STX $10", report.upcomingInstructions().get(1).formatted());
        assertEquals("JMP $9000", report.upcomingInstructions().get(2).formatted());
        assertEquals(0x9000, report.upcomingInstructions().get(0).address());
    }

    @Test
    public void captureCarriesTheSnapshotFramesAndMapperStateWithNoDescriptionYet(){
        final NES nes = new NES(mock(AudioOutput.class), cartridge(), new ManuallyTickedClock());
        nes.cpu().reset();
        final int[] lastFrame = new int[256 * 240];

        final DebugReport report = DebugCapture.capture(nes, "test.nes", lastFrame, CAPTURED_AT);

        assertEquals(CAPTURED_AT, report.capturedAt());
        assertEquals("test.nes", report.romName());
        assertEquals("", report.description());
        assertSame(lastFrame, report.screenshotRgb());
        assertArrayEquals(nes.ppu().rgbFramebuffer(), report.partialFrameRgb());
        assertRecordsEqual(nes.captureSnapshot(), report.snapshot());
        assertTrue(report.mapperState().isEmpty(), "NROM has no bank registers");
    }

    @Test
    public void captureIsRefusedWhileTheNesIsRunningUnpaused(){
        final Clock runningClock = mock(Clock.class);
        when(runningClock.isRunning()).thenReturn(true);
        final NES nes = new NES(mock(AudioOutput.class), cartridge(), runningClock);

        assertThrows(IllegalStateException.class, () -> DebugCapture.capture(nes, "test.nes", null, CAPTURED_AT));
    }

    @ParameterizedTest(name = "${0}")
    @CsvSource({
            "0000, 0x11",   //RAM
            "07FF, 0x22",   //last byte of the 2KB
            "0800, 0x11",   //RAM mirrors, as NESMemoryBus resolves them
            "1FFF, 0x22",
            "2000, 0xFF",   //PPU registers: placeholder
            "3FFF, 0xFF",
            "4000, 0xFF",   //APU/IO registers: placeholder
            "4017, 0xFF",
            "4018, 0x00",   //unmapped, as NESMemoryBus treats it
            "5FFF, 0x00",
            "6000, 0x66",   //cartridge PRG-RAM, from its very first byte
            "8000, 0x42",   //cartridge PRG-ROM
            "C000, 0x42",   //16KB PRG mirrored
    })
    public void peekRoutesLikeTheBusButNeverTouchesRegisters(final String hexAddress, final String hexExpected){
        final int[] ram = new int[0x800];
        ram[0x0000] = 0x11;
        ram[0x07FF] = 0x22;
        final Cartridge cartridge = cartridge();
        cartridge.write(0x6000, 0x66);
        final IntUnaryOperator peek = DebugCapture.sideEffectFreePeek(ram, cartridge);

        assertEquals(Integer.decode(hexExpected), peek.applyAsInt(Integer.parseInt(hexAddress, 16)));
    }

    /**
     * The whole "Flag issue" chain minus Swing, as RomVideoSmokeDemo runs it: capture a running-then-
     * paused NES, describe, write the folder, then read the .sav back and restore it into a fresh NES.
     */
    @Test
    public void flaggedIssueFolderRestoresIntoAFreshNes(@TempDir final Path tempDir) throws IOException {
        final NES nes = new NES(mock(AudioOutput.class), cartridge(), new ManuallyTickedClock());
        nes.cpu().reset();
        for (int i = 0; i < 30_000; i++){
            nes.clock().tick();
        }
        final Path romPath = tempDir.resolve("test.nes");
        final Path folder = SavePaths.debugSnapshotDirectory(romPath, CAPTURED_AT);

        final DebugReport report = DebugCapture.capture(nes, "test.nes", null, CAPTURED_AT).withDescription("it broke");
        DebugReportWriter.write(folder, report);

        assertEquals("it broke", Files.readString(folder.resolve("description.txt")));
        final SystemSnapshot saved = SaveFileFormat.readLiveSnapshot(folder.resolve("debug-snapshot-20261009-150000.sav"))
                .orElseThrow().snapshot();
        final NES fresh = new NES(mock(AudioOutput.class), cartridge(), new ManuallyTickedClock());
        fresh.restoreSnapshot(saved);
        assertRecordsEqual(nes.captureSnapshot(), fresh.captureSnapshot());
    }
}
