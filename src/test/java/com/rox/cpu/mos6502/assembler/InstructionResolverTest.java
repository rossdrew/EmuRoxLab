package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstructionResolverTest {

    @Test
    public void unknownMnemonicIsRejected() {
        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> InstructionResolver.resolve("XXX", Operand.EMPTY, 1));

        assertTrue(exception.getMessage().contains("Unknown mnemonic: XXX"));
    }

    @Test
    public void emptyOperandResolvesToAccumulatorWhenTheMnemonicSupportsIt() {
        assertEquals(MOS6502OpCode.ASL_A, InstructionResolver.resolve("ASL", Operand.EMPTY, 1));
    }

    @Test
    public void emptyOperandResolvesToImpliedWhenTheMnemonicHasNoAccumulatorVariant() {
        assertEquals(MOS6502OpCode.INX_IMP, InstructionResolver.resolve("INX", Operand.EMPTY, 1));
    }

    @Test
    public void emptyOperandIsRejectedWhenTheMnemonicRequiresOne() {
        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> InstructionResolver.resolve("LDA", Operand.EMPTY, 1));

        assertTrue(exception.getMessage().contains("LDA requires an operand"));
    }

    @Test
    public void labelOperandResolvesToRelativeForBranches() {
        assertEquals(MOS6502OpCode.BNE_REL, InstructionResolver.resolve("BNE", Operand.labelRef("LOOP"), 1));
    }

    @Test
    public void labelOperandResolvesToAbsoluteForJumpsAndCalls() {
        assertEquals(MOS6502OpCode.JMP_ABS, InstructionResolver.resolve("JMP", Operand.labelRef("SUB"), 1));
        assertEquals(MOS6502OpCode.JSR_ABS, InstructionResolver.resolve("JSR", Operand.labelRef("SUB"), 1));
    }

    @Test
    public void labelOperandIsRejectedWhenTheMnemonicSupportsNeitherRelativeNorAbsolute() {
        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> InstructionResolver.resolve("INX", Operand.labelRef("LOOP"), 1));

        assertTrue(exception.getMessage().contains("INX cannot take a label operand"));
    }

    @Test
    public void directModeResolvesToTheMatchingOpcode() {
        assertEquals(MOS6502OpCode.LDA_Z, InstructionResolver.resolve("LDA", Operand.of(AddressingMode.ZERO_PAGE, 0x05), 1));
    }

    @Test
    public void zeroPageModeWidensToAbsoluteWhenTheMnemonicHasNoZeroPageVariant() {
        assertEquals(MOS6502OpCode.JMP_ABS, InstructionResolver.resolve("JMP", Operand.of(AddressingMode.ZERO_PAGE, 0x12), 1));
    }

    @Test
    public void unsupportedDirectModeListsWhatIsSupported() {
        final AssemblyException exception = assertThrows(AssemblyException.class,
                () -> InstructionResolver.resolve("STA", Operand.of(AddressingMode.IMMEDIATE, 0x00), 1));

        assertTrue(exception.getMessage().contains("STA does not support IMMEDIATE addressing"));
        assertTrue(exception.getMessage().contains("supported:"));
    }
}
