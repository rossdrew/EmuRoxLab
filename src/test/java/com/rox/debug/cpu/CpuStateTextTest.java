package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
