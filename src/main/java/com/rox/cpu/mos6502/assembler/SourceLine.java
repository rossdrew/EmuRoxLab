package com.rox.cpu.mos6502.assembler;

/**
 * A single non-blank, comment-stripped line of 6502 assembly source.
 *
 * @param lineNumber 1-based line number in the original source, for error reporting
 * @param label the label defined on this line (e.g. {@code "LOOP"} for {@code "LOOP: STA $00"}), or {@code null}
 * @param mnemonic the instruction mnemonic (e.g. {@code "STA"}), or {@code null} if this line only defines a label
 * @param operandText the raw, untyped operand text (e.g. {@code "$0200,X"}), or {@code ""} if there is none
 */
record SourceLine(int lineNumber, String label, String mnemonic, String operandText) {
    /** Does this line contain a reference label */
    boolean isLabelled() {
        return label != null;
    }

    /** Does this line contain code */
    boolean containsCode() {
        return mnemonic != null;
    }
}
