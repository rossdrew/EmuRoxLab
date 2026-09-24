package com.rox.cpu.mos6502.assembler;

/**
 * One decoded 6502 instruction, ready for display - deliberately exposes no {@link AddressingMode} or
 * {@link com.rox.cpu.mos6502.MOS6502OpCode}, just primitives/String, so those package-private types
 * never have to cross into a debug-view package. {@code operandBytes} is empty for an unknown/illegal
 * opcode byte (this codebase doesn't model every possible byte value - see {@link Disassembler}).
 *
 * @param address the address {@code mnemonic} was fetched from
 * @param length total instruction length in bytes (1-3), including the opcode byte
 * @param mnemonic the instruction's mnemonic (e.g. {@code "LDA"}), or {@code "???"} for an unknown opcode byte
 * @param operandBytes the raw operand bytes, in the order they appear after the opcode (0-2 elements)
 * @param formatted a human-readable rendering (e.g. {@code "LDA #$05"}, {@code "JMP $8000"})
 */
public record DisassembledInstruction(int address, int length, String mnemonic, int[] operandBytes, String formatted) {
}
