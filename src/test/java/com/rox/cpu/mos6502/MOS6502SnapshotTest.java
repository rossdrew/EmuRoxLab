package com.rox.cpu.mos6502;

import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class MOS6502SnapshotTest {
    /**
     * LDA #$05; PHA; SEC; LDX #$07; loop: ADC #$01; DEX; BNE loop - exercises A, X, SP, the carry/zero/
     * negative flags and the ALU's own environment reference, and branches (variable-cycle) repeatedly.
     */
    private static final int[] PROGRAM = {0xA9, 0x05, 0x48, 0x38, 0xA2, 0x07, 0x69, 0x01, 0xCA, 0xD0, 0xFB};

    private static MOS6502 cpuRunningProgram(){
        final RAM ram = new RAM(0x10000);
        for (int i = 0; i < PROGRAM.length; i++){
            ram.write(i, PROGRAM[i]);
        }
        return new MOS6502(new Latched8BitMemoryBus(new MemoryBus8Bit(ram)));
    }

    private static void tickToInstructionBoundary(final MOS6502 cpu){
        do {
            cpu.tick();
        } while (!cpu.isAtInstructionBoundary());
    }

    @Property
    public void restoreThenSnapshotRoundTripsEveryField(@ForAll @IntRange(max = 0xFFFF) int pc,
                                                        @ForAll @IntRange(max = 0xFF) int a,
                                                        @ForAll @IntRange(max = 0xFF) int x,
                                                        @ForAll @IntRange(max = 0xFF) int y,
                                                        @ForAll @IntRange(max = 0xFF) int stackPointer,
                                                        @ForAll @IntRange(max = 0xFF) int ir,
                                                        @ForAll @IntRange(max = 0xFF) int adl,
                                                        @ForAll @IntRange(max = 0xFF) int adh,
                                                        @ForAll boolean negative, @ForAll boolean signedOverflow,
                                                        @ForAll boolean breakFlag, @ForAll boolean decimal,
                                                        @ForAll boolean interruptDisable, @ForAll boolean zero,
                                                        @ForAll boolean carry, @ForAll boolean irqLineAsserted,
                                                        @ForAll boolean nmiPending){
        final MOS6502Snapshot snapshot = new MOS6502Snapshot(pc, a, x, y, stackPointer, ir, adl, adh, negative,
                signedOverflow, breakFlag, decimal, interruptDisable, zero, carry, irqLineAsserted, nmiPending);
        final MOS6502 cpu = new MOS6502(mock(LatchedMemoryBus.class));

        cpu.restore(snapshot);

        assertEquals(snapshot, cpu.snapshot());
    }

    @Test
    public void snapshotMidInstructionIsRefused(){
        final MOS6502 cpu = cpuRunningProgram();
        cpu.tick(); //LDA #$05 fetched, operand still to come

        assertThrows(IllegalStateException.class, cpu::snapshot);
    }

    @Test
    public void snapshotDuringAnOutstandingStallIsRefused(){
        final MOS6502 cpu = cpuRunningProgram();
        cpu.stall(1);

        assertThrows(IllegalStateException.class, cpu::snapshot);
    }

    @Test
    public void restoreAbandonsAnInstructionAndStallInFlight(){
        final MOS6502 cpu = cpuRunningProgram();
        final MOS6502Snapshot atReset = cpu.snapshot();
        cpu.tick();
        cpu.stall(5);

        cpu.restore(atReset);

        assertTrue(cpu.isAtInstructionBoundary());
        assertEquals(atReset, cpu.snapshot());
    }

    /**
     * Behavioural proof, not just field equality: a fresh CPU restored from a mid-program snapshot then
     * runs in lockstep with the original - including ADC, which only comes out right if the ALU was
     * rebound to the restored environment rather than left pointing at the fresh CPU's old one.
     */
    @Test
    public void restoredCpuRunsOnInLockstepWithTheOriginal(){
        final MOS6502 original = cpuRunningProgram();
        for (int i = 0; i < 4; i++){
            tickToInstructionBoundary(original); //LDA, PHA, SEC, LDX done - into the ADC/DEX/BNE loop
        }
        final MOS6502 restored = cpuRunningProgram();
        restored.restore(original.snapshot());

        for (int i = 0; i < 12; i++){
            tickToInstructionBoundary(original);
            tickToInstructionBoundary(restored);
            assertEquals(original.snapshot(), restored.snapshot(), "diverged after instruction " + (i + 1));
        }
        assertEquals(0x05 + 1 + 4, restored.snapshot().a(), "test setup: expected 4 loop iterations' ADCs to have run (A = 5 + carry + 4)");
    }
}
