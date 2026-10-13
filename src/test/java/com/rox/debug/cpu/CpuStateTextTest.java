package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CpuStateTextTest {
    private static final MOS6502Snapshot SOME_SET = new MOS6502Snapshot(0x8123, 0x01, 0x02, 0x03, 0xFD, 0, 0, 0,
            true, false, true, false, true, false, true, false, true);
    private static final MOS6502Snapshot OTHERS_SET = new MOS6502Snapshot(0x0005, 0xFF, 0x00, 0x7F, 0x00, 0, 0, 0,
            false, true, false, true, false, true, false, true, false);

    @Test
    public void registersArePaddedHex(){
        assertEquals("PC: $8123  A: $01  X: $02  Y: $03  SP: $FD", CpuStateText.registers(SOME_SET));
        assertEquals("PC: $0005  A: $FF  X: $00  Y: $7F  SP: $00", CpuStateText.registers(OTHERS_SET));
    }

    @Test
    public void flagsAreUppercaseWhenSet(){
        assertEquals("Nv-BdIzC", CpuStateText.flags(SOME_SET));
        assertEquals("nV-bDiZc", CpuStateText.flags(OTHERS_SET));
    }

    @Test
    public void interruptLinesShowBothStates(){
        assertEquals("IRQ line: clear  NMI pending: yes", CpuStateText.interruptLines(SOME_SET));
        assertEquals("IRQ line: asserted  NMI pending: no", CpuStateText.interruptLines(OTHERS_SET));
    }

    @Test
    public void instructionMarksTheOneAtThePc(){
        final DisassembledInstruction jmp = new DisassembledInstruction(0x8123, 3, "JMP", new int[]{0x00, 0x90}, "JMP $9000");

        assertEquals("> $8123  JMP $9000", CpuStateText.instruction(jmp, true));
        assertEquals("  $8123  JMP $9000", CpuStateText.instruction(jmp, false));
    }

    @Test
    public void clockRateShowsMeasuredIntendedAndPercentage(){
        assertEquals("Clock: 1,789,773 Hz of 1,789,773 Hz (100.0%)", CpuStateText.clockRate(1_789_773, 1_789_773));
        assertEquals("Clock: 894,887 Hz of 1,789,773 Hz (50.0%)", CpuStateText.clockRate(894_886.5, 1_789_773));
        assertEquals("Clock: 0 Hz of 1,789,773 Hz (0.0%)", CpuStateText.clockRate(0, 1_789_773));
    }

    /** A fixed-answer {@link CpuDebugSource}, for checking what the panel text is built from. */
    private static CpuDebugSource source(final boolean paused){
        return new CpuDebugSource(){
            @Override public MOS6502Snapshot state(){ return SOME_SET; }
            @Override public java.util.List<DisassembledInstruction> upcomingInstructions(final int count){
                return java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new DisassembledInstruction(0x8123 + i, 1, "NOP", new int[0], "NOP")).toList();
            }
            @Override public java.util.List<TracedInstruction> recentInstructions(){
                return java.util.List.of(
                        new TracedInstruction(new DisassembledInstruction(0x8120, 1, "INX", new int[0], "INX"),
                                MOS6502Snapshot.unpack(0x8120, MOS6502Snapshot.packRegisters(0x10, 0x01, 0x00, 0xFF), 0)),
                        new TracedInstruction(new DisassembledInstruction(0x8121, 2, "LDA", new int[]{5}, "LDA #$05"),
                                MOS6502Snapshot.unpack(0x8121, MOS6502Snapshot.packRegisters(0x10, 0x02, 0x00, 0xFF),
                                        MOS6502Snapshot.packFlags(false, false, false, false, true, false, true, true, false))));
            }
            @Override public java.util.Optional<MOS6502Snapshot> currentInstructionState(){
                return java.util.Optional.of(SOME_SET);
            }
            @Override public double measuredHz(){ return 1_789_773; }
            @Override public long intendedHz(){ return 1_789_773; }
            @Override public boolean pause(){ return true; }
            @Override public void resume(){ }
            @Override public boolean isPaused(){ return paused; }
            @Override public void stepInstruction(){ }
            @Override public void stepCycles(final int cycles){ }
            @Override public void stepFrame(){ }
        };
    }

    @Test
    public void panelRunsTimeBottomToTopWithTheCurrentInstructionBetweenNextAndRecent(){
        assertEquals("""
                Running  Clock: 1,789,773 Hz of 1,789,773 Hz (100.0%)
                PC: $8123  A: $01  X: $02  Y: $03  SP: $FD
                Flags: Nv-BdIzC  (NV-BDIZC)
                IRQ line: clear  NMI pending: yes

                --- Next (furthest ahead at top) ---
                  $8125  NOP
                  $8124  NOP
                > $8123  NOP            A:01 X:02 Y:03 SP:FD  Nv-BdIzC  nmi
                --- Recent (latest at top) ---
                  $8121  LDA #$05       A:10 X:02 Y:00 SP:FF  nv-bdIzC  irq
                  $8120  INX            A:10 X:01 Y:00 SP:FF  nv-bdizc
                """, CpuStateText.panel(source(false), 3));
    }

    @Test
    public void panelShowsWhenPaused(){
        assertTrue(CpuStateText.panel(source(true), 1).startsWith("PAUSED  Clock:"));
    }

    @Test
    public void panelCanUseAStateTheCallerAlreadyRead(){
        final String text = CpuStateText.panel(OTHERS_SET, source(false), 1);

        assertTrue(text.contains("PC: $0005  A: $FF  X: $00  Y: $7F  SP: $00\n"), text);
        assertTrue(text.contains("Flags: nV-bDiZc"), text);
    }

    @Test
    public void instructionWithStateAlignsTheStateColumnEvenForTheLongestInstruction(){
        final DisassembledInstruction longest = new DisassembledInstruction(0xFFFF, 2, "ORA", new int[]{0x40}, "ORA ($40),Y");

        assertEquals("  $FFFF  ORA ($40),Y    A:FF X:00 Y:7F SP:00  nV-bDiZc  irq",
                CpuStateText.instruction(longest, false, OTHERS_SET));
    }

    @Test
    public void stateShowsIrqAndNmiOnlyWhenActive(){
        assertEquals("A:01 X:02 Y:03 SP:FD  Nv-BdIzC  nmi", CpuStateText.state(SOME_SET));
        assertEquals("A:FF X:00 Y:7F SP:00  nV-bDiZc  irq", CpuStateText.state(OTHERS_SET));
    }

    @Test
    public void theCurrentInstructionHasNoStateBeforeAnythingHasBeenRecorded(){
        final CpuDebugSource fresh = new CpuDebugSource(){
            @Override public MOS6502Snapshot state(){ return SOME_SET; }
            @Override public java.util.List<DisassembledInstruction> upcomingInstructions(final int count){
                return java.util.List.of(new DisassembledInstruction(0x8000, 1, "NOP", new int[0], "NOP"));
            }
            @Override public java.util.List<TracedInstruction> recentInstructions(){ return java.util.List.of(); }
            @Override public java.util.Optional<MOS6502Snapshot> currentInstructionState(){ return java.util.Optional.empty(); }
            @Override public double measuredHz(){ return 0; }
            @Override public long intendedHz(){ return 1; }
            @Override public boolean pause(){ return true; }
            @Override public void resume(){ }
            @Override public boolean isPaused(){ return true; }
            @Override public void stepInstruction(){ }
            @Override public void stepCycles(final int cycles){ }
            @Override public void stepFrame(){ }
        };

        assertTrue(CpuStateText.panel(fresh, 1).contains("\n> $8000  NOP\n"));
    }
}
