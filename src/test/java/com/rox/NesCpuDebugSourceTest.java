package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class NesCpuDebugSourceTest {
    private static final int PRG_ROM_SIZE = 0x4000;

    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000 (2 + 3 + 3 cycles per loop). */
    private static NES nes(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        final byte[] program = {(byte) 0xE8, (byte) 0x86, 0x10, 0x4C, 0x00, (byte) 0x90};
        System.arraycopy(program, 0, fileBytes, header.length + 0x1000, program.length);
        fileBytes[header.length + 0x3FFC] = 0x00;
        fileBytes[header.length + 0x3FFD] = (byte) 0x90;
        final Cartridge cartridge = RomLoader.fromBytes(fileBytes);
        final NES nes = new NES(mock(AudioOutput.class), cartridge, new ManuallyTickedClock());
        nes.cpu().reset();
        return nes;
    }

    /** Ticks two full loops plus INX, leaving the CPU between instructions at $9001 (STX). */
    private static void tickToStxOnTheThirdLoop(final NES nes){
        for (int i = 0; i < 2 * 8 + 2; i++){
            nes.clock().tick();
        }
        assertEquals(0x9001, nes.cpu().programCounter(), "test setup");
        assertTrue(nes.cpu().isAtInstructionBoundary(), "test setup");
    }

    private static List<String> formatted(final List<DisassembledInstruction> instructions){
        return instructions.stream().map(DisassembledInstruction::formatted).toList();
    }

    @Test
    public void stateIsTheCpusLiveState(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);

        assertEquals(nes.cpu().liveState(), new NesCpuDebugSource(nes).state());
        assertEquals(3, new NesCpuDebugSource(nes).state().x());
    }

    @Test
    public void upcomingInstructionsStartAtThePc(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);

        final List<DisassembledInstruction> upcoming = new NesCpuDebugSource(nes).upcomingInstructions(3);

        //linear, like any forward disassembly - it doesn't follow the JMP, so the zero byte after it is BRK
        assertEquals(List.of("STX $10", "JMP $9000", "BRK"), formatted(upcoming));
        assertEquals(0x9001, upcoming.get(0).address());
    }

    @Test
    public void recentInstructionsAreHistoryOldestFirstWithoutTheOneAtThePc(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);

        final List<DisassembledInstruction> recent = new NesCpuDebugSource(nes).recentInstructions();

        //the reset vector's first INX isn't recorded (reset() sets the PC without a tick), then two loops
        //and the third INX; STX at $9001 is current, so not included
        assertEquals(List.of("STX $10", "JMP $9000", "INX", "STX $10", "JMP $9000", "INX"), formatted(recent));
    }

    @Test
    public void recentInstructionsKeepTheNewestWhenItIsNotThePc(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);
        nes.clock().tick(); //part-way into STX: the trace's newest entry ($9001) is no longer "about to start"

        final List<DisassembledInstruction> recent = new NesCpuDebugSource(nes).recentInstructions();

        assertEquals("STX $10", recent.get(recent.size() - 1).formatted());
    }

    @Test
    public void recentInstructionsIsEmptyBeforeAnythingRuns(){
        assertTrue(new NesCpuDebugSource(nes()).recentInstructions().isEmpty());
    }

    @Test
    public void ratesComeFromTheNes(){
        final NES nes = nes();
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);

        assertEquals(1_789_773, source.intendedHz());
        assertEquals(0.0, source.measuredHz(), "no full measuring window has passed yet");
    }

    @Test
    public void pauseAndResumeDriveTheNes(){
        final NES nes = nes();
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);
        assertFalse(source.isPaused());

        assertTrue(source.pause());
        assertTrue(source.isPaused());
        assertTrue(nes.isPaused());

        source.resume();
        assertFalse(source.isPaused());
    }

    @Test
    public void measuredHzIsTheNessTickRateMonitors(){
        final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();
        final com.rox.clock.TickRateMonitor monitor = new com.rox.clock.TickRateMonitor(now::get, 1_000_000_000L);
        for (int i = 0; i < 500; i++){
            monitor.tick();
        }
        now.set(1_000_000_000L);
        final NES nes = mock(NES.class);
        org.mockito.Mockito.when(nes.tickRateMonitor()).thenReturn(monitor);

        assertEquals(500.0, new NesCpuDebugSource(nes).measuredHz());
    }

    @Test
    public void pauseReportsWhenThereWasNothingRunningToPause(){
        final NES notRunning = new NES(mock(AudioOutput.class), nes().cartridge()); //a real, never-started FPSClock

        assertFalse(new NesCpuDebugSource(notRunning).pause());
    }
}
