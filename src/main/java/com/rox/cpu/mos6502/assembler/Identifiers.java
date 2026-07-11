package com.rox.cpu.mos6502.assembler;

/** Shared identifier-name validation for labels, used by both label parsing and operand parsing. */
final class Identifiers {

    private Identifiers() {
    }

    static boolean isValidLabelName(final String text) {
        if (text.isEmpty() || !(Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
