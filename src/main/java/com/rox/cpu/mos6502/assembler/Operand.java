package com.rox.cpu.mos6502.assembler;

/**
 * A parsed instruction operand.
 * <p>
 * {@code mode} is left {@code null} for two syntactically ambiguous cases, resolved later by
 * {@code InstructionResolver} against what the mnemonic actually supports:
 * <ul>
 *     <li>no operand token at all ({@link #EMPTY}) - could be IMPLIED or ACCUMULATOR</li>
 *     <li>a bare label reference - could be RELATIVE (branches) or ABSOLUTE (e.g. JMP/JSR)</li>
 * </ul>
 */
record Operand(AddressingMode mode, int value, String label) {
    static final Operand EMPTY = new Operand(null, 0, null);

    static Operand accumulator() {
        return new Operand(AddressingMode.ACCUMULATOR, 0, null);
    }

    static Operand of(final AddressingMode mode, final int value) {
        return new Operand(mode, value, null);
    }

    static Operand labelRef(final String label) {
        return new Operand(null, 0, label);
    }

    boolean isEmpty() {
        return mode == null && label == null;
    }

    boolean isLabelReference() {
        return label != null;
    }
}
