package com.rox.cpu.mos6502.assembler;

/** The 13 addressing modes of the 6502 instruction set, as used by the assembler's operand grammar. */
enum AddressingMode {
    IMPLIED,
    ACCUMULATOR,
    IMMEDIATE,
    ZERO_PAGE,
    ZERO_PAGE_X,
    ZERO_PAGE_Y,
    ABSOLUTE,
    ABSOLUTE_X,
    ABSOLUTE_Y,
    INDIRECT,
    INDIRECT_X,
    INDIRECT_Y,
    RELATIVE;

    /** @return the number of operand bytes this mode encodes in an instruction, after the opcode byte */
    int operandByteCount() {
        return switch (this) {
            case IMPLIED, ACCUMULATOR -> 0;
            case ABSOLUTE, ABSOLUTE_X, ABSOLUTE_Y, INDIRECT -> 2;
            default -> 1; // IMMEDIATE, ZERO_PAGE(+X/Y), INDIRECT_X, INDIRECT_Y, RELATIVE
        };
    }
}
