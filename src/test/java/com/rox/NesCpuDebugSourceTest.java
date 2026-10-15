package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.debug.cpu.TracedInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class NesCpuDebugSourceTest {
    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000 (2 + 3 + 3 cycles per loop). */
    private static NES nes(){
        final Cartridge cartridge = TestRoms.nrom().at(0x9000, 0xE8, 0x86, 0x10, 0x4C, 0x00, 0x90).resetVector(0x9000).cartridge();
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

        final List<DisassembledInstruction> recent = new NesCpuDebugSource(nes).recentInstructions().stream()
                .map(TracedInstruction::instruction).toList();

        //the reset vector's first INX isn't recorded (reset() sets the PC without a tick), then two loops
        //and the third INX; STX at $9001 is current, so not included
        assertEquals(List.of("STX $10", "JMP $9000", "INX", "STX $10", "JMP $9000", "INX"), formatted(recent));
    }

    /** Read live, the CPU is usually mid-instruction, with the PC already pointing at operand bytes. */
    @Test
    public void midInstructionBothListsAreAnchoredOnTheInstructionInProgressNotThePc(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);
        nes.clock().tick(); //part-way into STX $10: the PC now points at its $10 operand
        assertEquals(0x9002, nes.cpu().programCounter(), "test setup");
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);

        final List<DisassembledInstruction> upcoming = source.upcomingInstructions(2);
        final List<DisassembledInstruction> recent = source.recentInstructions().stream()
                .map(TracedInstruction::instruction).toList();

        assertEquals(List.of("STX $10", "JMP $9000"), formatted(upcoming));
        assertEquals(0x9001, upcoming.get(0).address());
        assertEquals("INX", recent.get(recent.size() - 1).formatted());
    }

    @Test
    public void beforeAnythingRunsUpcomingInstructionsStartAtThePc(){
        final List<DisassembledInstruction> upcoming = new NesCpuDebugSource(nes()).upcomingInstructions(1);

        assertEquals(0x9000, upcoming.get(0).address());
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

    @Test
    public void stepInstructionStepsTheNes(){
        final NES nes = nes();
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);

        source.stepInstruction();

        assertEquals(0x9001, source.state().pc());
    }

    @Test
    public void stepFrameStepsTheNes(){
        final NES nes = nes();

        new NesCpuDebugSource(nes).stepFrame();

        assertTrue(nes.tickRateMonitor().ticks() > 20_000, "ran (most of) a frame - the first frame starts mid-way");
        assertTrue(nes.cpu().isAtInstructionBoundary());
    }

    @Test
    public void stepCyclesStepsTheNes(){
        final NES nes = nes();

        new NesCpuDebugSource(nes).stepCycles(7);

        assertEquals(7, nes.tickRateMonitor().ticks());
    }

    @Test
    public void historyCarriesEachInstructionsStartingStateAndTheCurrentOnesToo(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes); //at STX $10 on the third loop, X has just become 3
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);

        final List<TracedInstruction> recent = source.recentInstructions();

        final TracedInstruction lastInx = recent.get(recent.size() - 1);
        assertEquals("INX", lastInx.instruction().formatted());
        assertEquals(2, lastInx.before().x(), "INX started with X=2");
        assertEquals(3, source.currentInstructionState().orElseThrow().x(), "STX starts with X=3");
    }

    @Test
    public void instructionCountGoesUpByOnePerInstructionStep(){
        final NES nes = nes();
        tickToStxOnTheThirdLoop(nes);
        final NesCpuDebugSource source = new NesCpuDebugSource(nes);
        final long before = source.instructionCount();

        source.stepInstruction();

        assertEquals(before + 1, source.instructionCount());
    }

    @Test
    public void beforeAnythingRunsThereIsNoCurrentInstructionState(){
        assertTrue(new NesCpuDebugSource(nes()).currentInstructionState().isEmpty());
    }
}
