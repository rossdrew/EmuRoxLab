package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Decodes 6502 machine code into {@link DisassembledInstruction}s for display - forward-only, since a
 * variable-length instruction set can't be walked backward from an arbitrary address without ambiguity
 * (any preceding byte could be a real opcode or another instruction's operand). Bytes are read via a
 * caller-supplied {@code peekByte} function rather than any particular {@code Memory}/bus type, so a
 * caller reading from a live, running NES can substitute a side-effect-free peek (e.g. one that avoids
 * triggering read-clearing PPU/APU/controller registers) without this class needing to know about any
 * of that.
 */
public final class Disassembler {
    private static final int BYTE_MASK = 0xFF;
    private static final int ADDRESS_MASK = 0xFFFF;
    private static final String UNKNOWN_MNEMONIC = "???";

    private Disassembler(){
    }

    /** Decodes the single instruction starting at {@code address}. */
    public static DisassembledInstruction disassembleOne(final IntUnaryOperator peekByte, final int address){
        final int opcodeByte = peekByte.applyAsInt(address) & BYTE_MASK;

        final MOS6502OpCode opcode;
        try {
            opcode = MOS6502OpCode.from(opcodeByte);
        } catch (IllegalArgumentException e){
            return new DisassembledInstruction(address, 1, UNKNOWN_MNEMONIC, new int[0],
                    String.format("%s ($%02X)", UNKNOWN_MNEMONIC, opcodeByte));
        }

        final AddressingMode mode = OpCodeTable.modeOf(opcode);
        final String mnemonic = opcode.name().split("_", 2)[0];
        final int operandByteCount = mode.operandByteCount();
        final int[] operandBytes = new int[operandByteCount];
        for (int i = 0; i < operandByteCount; i++){
            operandBytes[i] = peekByte.applyAsInt(address + 1 + i) & BYTE_MASK;
        }
        final int length = 1 + operandByteCount;

        return new DisassembledInstruction(address, length, mnemonic, operandBytes,
                format(mnemonic, mode, operandBytes, address, length));
    }

    /** Decodes {@code count} instructions forward from {@code startAddress}, wrapping at $FFFF. */
    public static List<DisassembledInstruction> disassembleForward(final IntUnaryOperator peekByte, final int startAddress, final int count){
        final List<DisassembledInstruction> instructions = new ArrayList<>(count);
        int address = startAddress & ADDRESS_MASK;
        for (int i = 0; i < count; i++){
            final DisassembledInstruction instruction = disassembleOne(peekByte, address);
            instructions.add(instruction);
            address = (address + instruction.length()) & ADDRESS_MASK;
        }
        return instructions;
    }

    private static String format(final String mnemonic, final AddressingMode mode, final int[] operand,
                                  final int address, final int length){
        return switch (mode){
            case IMPLIED -> mnemonic;
            case ACCUMULATOR -> mnemonic + " A";
            case IMMEDIATE -> String.format("%s #$%02X", mnemonic, operand[0]);
            case ZERO_PAGE -> String.format("%s $%02X", mnemonic, operand[0]);
            case ZERO_PAGE_X -> String.format("%s $%02X,X", mnemonic, operand[0]);
            case ZERO_PAGE_Y -> String.format("%s $%02X,Y", mnemonic, operand[0]);
            case ABSOLUTE -> String.format("%s $%04X", mnemonic, absoluteAddress(operand));
            case ABSOLUTE_X -> String.format("%s $%04X,X", mnemonic, absoluteAddress(operand));
            case ABSOLUTE_Y -> String.format("%s $%04X,Y", mnemonic, absoluteAddress(operand));
            case INDIRECT -> String.format("%s ($%04X)", mnemonic, absoluteAddress(operand));
            case INDIRECT_X -> String.format("%s ($%02X,X)", mnemonic, operand[0]);
            case INDIRECT_Y -> String.format("%s ($%02X),Y", mnemonic, operand[0]);
            case RELATIVE -> String.format("%s $%04X", mnemonic, relativeTarget(address, length, operand[0]));
        };
    }

    private static int absoluteAddress(final int[] operand){
        return operand[0] | (operand[1] << 8);
    }

    /** A branch's target is relative to the address of the *next* instruction, not the branch opcode's own address. */
    private static int relativeTarget(final int address, final int length, final int offsetByte){
        final byte signedOffset = (byte) offsetByte;
        return (address + length + signedOffset) & ADDRESS_MASK;
    }
}
