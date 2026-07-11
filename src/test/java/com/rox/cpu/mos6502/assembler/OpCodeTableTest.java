package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.rox.cpu.mos6502.assembler.AddressingMode.ABSOLUTE;
import static com.rox.cpu.mos6502.assembler.AddressingMode.ABSOLUTE_X;
import static com.rox.cpu.mos6502.assembler.AddressingMode.ABSOLUTE_Y;
import static com.rox.cpu.mos6502.assembler.AddressingMode.ACCUMULATOR;
import static com.rox.cpu.mos6502.assembler.AddressingMode.IMMEDIATE;
import static com.rox.cpu.mos6502.assembler.AddressingMode.IMPLIED;
import static com.rox.cpu.mos6502.assembler.AddressingMode.INDIRECT_X;
import static com.rox.cpu.mos6502.assembler.AddressingMode.INDIRECT_Y;
import static com.rox.cpu.mos6502.assembler.AddressingMode.RELATIVE;
import static com.rox.cpu.mos6502.assembler.AddressingMode.ZERO_PAGE;
import static com.rox.cpu.mos6502.assembler.AddressingMode.ZERO_PAGE_X;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpCodeTableTest {

    @Test
    public void resolveFindsTheOpcodeForAKnownMnemonicAndMode() {
        assertEquals(MOS6502OpCode.LDA_Z, OpCodeTable.resolve("LDA", ZERO_PAGE));
        assertEquals(MOS6502OpCode.LDA_ABS, OpCodeTable.resolve("LDA", ABSOLUTE));
        assertEquals(MOS6502OpCode.BNE_REL, OpCodeTable.resolve("BNE", RELATIVE));
        assertEquals(MOS6502OpCode.JSR_ABS, OpCodeTable.resolve("JSR", ABSOLUTE));
        assertEquals(MOS6502OpCode.ASL_A, OpCodeTable.resolve("ASL", ACCUMULATOR));
        assertEquals(MOS6502OpCode.BRK_IMP, OpCodeTable.resolve("BRK", IMPLIED));
    }

    @Test
    public void supportsReturnsTrueOnlyForModesTheMnemonicActuallyHas() {
        assertTrue(OpCodeTable.supports("LDA", IMMEDIATE));
        assertTrue(OpCodeTable.supports("LDA", ZERO_PAGE));
        assertFalse(OpCodeTable.supports("STA", IMMEDIATE)); // STA has no immediate mode
        assertFalse(OpCodeTable.supports("JMP", ZERO_PAGE));  // JMP has no zero-page mode
    }

    @Test
    public void resolveThrowsForAnUnsupportedModeCombination() {
        assertThrows(IllegalStateException.class, () -> OpCodeTable.resolve("STA", IMMEDIATE));
    }

    @Test
    public void supportsIsFalseForACompletelyUnknownMnemonic() {
        assertFalse(OpCodeTable.supports("XXX", IMPLIED));
    }

    @Test
    public void resolveThrowsForACompletelyUnknownMnemonic() {
        assertThrows(IllegalStateException.class, () -> OpCodeTable.resolve("XXX", IMPLIED));
    }

    @Test
    public void supportedModesListsExactlyWhatTheMnemonicSupports() {
        assertEquals(Set.of(IMMEDIATE, ZERO_PAGE, ZERO_PAGE_X, ABSOLUTE, ABSOLUTE_X, ABSOLUTE_Y, INDIRECT_X, INDIRECT_Y),
                OpCodeTable.supportedModes("LDA"));
    }

    @Test
    public void supportedModesIsEmptyForAnUnknownMnemonic() {
        assertTrue(OpCodeTable.supportedModes("XXX").isEmpty());
    }

    @Test
    public void modeOfIsTheInverseOfResolveForEveryOfficialOpcode() {
        for (final MOS6502OpCode opcode : MOS6502OpCode.values()) {
            final AddressingMode mode = OpCodeTable.modeOf(opcode);
            final String mnemonic = opcode.name().split("_", 2)[0];

            assertNotNull(mode, opcode + " has no addressing mode mapped");
            assertEquals(opcode, OpCodeTable.resolve(mnemonic, mode));
        }
    }
}
