package com.rox.cpu.mos6502.assembler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class DisassemblerTest {

    /**
     * A peek function backed by a small in-memory window starting at {@code baseAddress}; reads outside
     * it return 0. Offsets wrap at the 6502's 16-bit address space, so a window starting near $FFFF can
     * exercise address wraparound.
     */
    private static IntUnaryOperator memoryAt(final int baseAddress, final int... bytes){
        return address -> {
            final int offset = ((address - baseAddress) & 0xFFFF);
            return offset < bytes.length ? bytes[offset] : 0;
        };
    }

    @Test
    public void impliedModeHasNoOperandBytes(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xEA), 0x8000); //NOP

        assertEquals(0x8000, instruction.address());
        assertEquals(1, instruction.length());
        assertEquals("NOP", instruction.mnemonic());
        assertArrayEquals(new int[0], instruction.operandBytes());
        assertEquals("NOP", instruction.formatted());
    }

    @Test
    public void accumulatorModeFormatsWithAnExplicitA(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0x0A), 0x8000); //ASL A

        assertEquals(1, instruction.length());
        assertEquals("ASL A", instruction.formatted());
    }

    @Test
    public void immediateModeFormatsWithAHashAndTwoHexDigits(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xA9, 0x05), 0x8000); //LDA #$05

        assertEquals(2, instruction.length());
        assertArrayEquals(new int[]{0x05}, instruction.operandBytes());
        assertEquals("LDA #$05", instruction.formatted());
    }

    @Test
    public void zeroPageModeFormatsWithTwoHexDigitsNoPrefix(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xA5, 0x42), 0x8000); //LDA $42

        assertEquals(2, instruction.length());
        assertEquals("LDA $42", instruction.formatted());
    }

    @Test
    public void zeroPageXModeAppendsCommaX(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xB5, 0x42), 0x8000); //LDA $42,X

        assertEquals("LDA $42,X", instruction.formatted());
    }

    @Test
    public void zeroPageYModeAppendsCommaY(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0x96, 0x42), 0x8000); //STX $42,Y

        assertEquals("STX $42,Y", instruction.formatted());
    }

    @Test
    public void absoluteModeFormatsAsFourHexDigitsLittleEndian(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xAD, 0x34, 0x12), 0x8000); //LDA $1234

        assertEquals(3, instruction.length());
        assertArrayEquals(new int[]{0x34, 0x12}, instruction.operandBytes());
        assertEquals("LDA $1234", instruction.formatted());
    }

    @Test
    public void absoluteXModeAppendsCommaX(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xBD, 0x00, 0x80), 0x8000); //LDA $8000,X

        assertEquals("LDA $8000,X", instruction.formatted());
    }

    @Test
    public void absoluteYModeAppendsCommaY(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xB9, 0x00, 0x80), 0x8000); //LDA $8000,Y

        assertEquals("LDA $8000,Y", instruction.formatted());
    }

    @Test
    public void indirectModeWrapsTheAddressInParentheses(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0x6C, 0x00, 0x80), 0x8000); //JMP ($8000)

        assertEquals("JMP ($8000)", instruction.formatted());
    }

    @Test
    public void indirectXModeFormatsAsZeroPageCommaXInParentheses(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xA1, 0x42), 0x8000); //LDA ($42,X)

        assertEquals("LDA ($42,X)", instruction.formatted());
    }

    @Test
    public void indirectYModeFormatsAsZeroPageInParenthesesCommaY(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xB1, 0x42), 0x8000); //LDA ($42),Y

        assertEquals("LDA ($42),Y", instruction.formatted());
    }

    @Test
    public void relativeModeResolvesAForwardBranchToAnAbsoluteTarget(){
        //BNE $8010 - target = address (0x8000) + length (2) + offset (0x0E)
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xD0, 0x0E), 0x8000);

        assertEquals(2, instruction.length());
        assertEquals("BNE $8010", instruction.formatted());
    }

    @Test
    public void relativeModeResolvesABackwardBranchToAnAbsoluteTarget(){
        //BNE $7FFE - target = address (0x8000) + length (2) + offset (-4, i.e. 0xFC)
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0xD0, 0xFC), 0x8000);

        assertEquals("BNE $7FFE", instruction.formatted());
    }

    @Test
    public void unknownOpcodeByteProducesAOneByteFallbackInstruction(){
        final DisassembledInstruction instruction = Disassembler.disassembleOne(memoryAt(0x8000, 0x02), 0x8000); //not a modeled opcode

        assertEquals(0x8000, instruction.address());
        assertEquals(1, instruction.length());
        assertEquals("???", instruction.mnemonic());
        assertArrayEquals(new int[0], instruction.operandBytes());
        assertEquals("??? ($02)", instruction.formatted());
    }

    @Test
    public void disassembleForwardWalksEachInstructionsOwnLength(){
        //NOP (1 byte), LDA #$05 (2 bytes), LDA $1234 (3 bytes)
        final IntUnaryOperator peek = memoryAt(0x8000, 0xEA, 0xA9, 0x05, 0xAD, 0x34, 0x12);

        final List<DisassembledInstruction> instructions = Disassembler.disassembleForward(peek, 0x8000, 3);

        assertEquals(3, instructions.size());
        assertEquals(0x8000, instructions.get(0).address());
        assertEquals("NOP", instructions.get(0).formatted());
        assertEquals(0x8001, instructions.get(1).address());
        assertEquals("LDA #$05", instructions.get(1).formatted());
        assertEquals(0x8003, instructions.get(2).address());
        assertEquals("LDA $1234", instructions.get(2).formatted());
    }

    @Test
    public void disassembleForwardWrapsTheAddressAtSixteenBits(){
        final IntUnaryOperator peek = memoryAt(0xFFFF, 0xEA, 0xEA); //two NOPs, the second at wrapped address $0000

        final List<DisassembledInstruction> instructions = Disassembler.disassembleForward(peek, 0xFFFF, 2);

        assertEquals(0xFFFF, instructions.get(0).address());
        assertEquals(0x0000, instructions.get(1).address(), "address must wrap from $FFFF back to $0000, not overflow past it");
        assertEquals("NOP", instructions.get(1).formatted(), "the wrapped address must still read back the byte placed there, not a default 0");
    }
}
