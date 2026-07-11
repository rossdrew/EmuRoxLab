package com.rox.cpu.mos6502.assembler;

/**
 * Parses the raw operand text of a single instruction line into an {@link Operand}.
 * <p>
 * Purely syntactic and mnemonic-agnostic: it decides {@code #$xx} is IMMEDIATE, {@code ($xx,X)} is
 * INDIRECT_X, and so on from shape alone. Zero page vs. absolute is decided by hex-digit count
 * ({@code $xx} = zero page, {@code $xxxx} = absolute) or, for decimal literals, by magnitude -
 * matching how real 6502 assemblers behave. The two cases that can't be resolved from syntax alone
 * (no operand token at all, and a bare label reference) are left as {@link Operand} sentinels for
 * {@code InstructionResolver} to resolve against what the mnemonic supports.
 */
final class OperandParser {

    private OperandParser() {
    }

    static Operand parse(final int lineNumber, final String operandText) {
        if (operandText.isEmpty()) {
            return Operand.EMPTY;
        }
        if (operandText.equalsIgnoreCase("A")) {
            return Operand.accumulator();
        }
        if (operandText.startsWith("#")) {
            return Operand.of(AddressingMode.IMMEDIATE, parseNumberInRange(lineNumber, operandText.substring(1), 0xFF));
        }
        if (operandText.startsWith("(")) {
            return parseIndirect(lineNumber, operandText);
        }
        if (endsWithIgnoreCase(operandText, ",X")) {
            return parseDirect(lineNumber, withoutLastTwoChars(operandText), AddressingMode.ZERO_PAGE_X, AddressingMode.ABSOLUTE_X);
        }
        if (endsWithIgnoreCase(operandText, ",Y")) {
            return parseDirect(lineNumber, withoutLastTwoChars(operandText), AddressingMode.ZERO_PAGE_Y, AddressingMode.ABSOLUTE_Y);
        }
        if (looksLikeNumericLiteral(operandText)) {
            return parseDirect(lineNumber, operandText, AddressingMode.ZERO_PAGE, AddressingMode.ABSOLUTE);
        }
        if (Identifiers.isValidLabelName(operandText)) {
            return Operand.labelRef(operandText);
        }
        throw new AssemblyException(lineNumber, "Invalid operand syntax: " + operandText);
    }

    private static Operand parseIndirect(final int lineNumber, final String text) {
        if (endsWithIgnoreCase(text, "),Y")) {
            final String inner = text.substring(1, text.length() - 3).strip();
            return Operand.of(AddressingMode.INDIRECT_Y, parseNumberInRange(lineNumber, inner, 0xFF));
        }
        if (endsWithIgnoreCase(text, ",X)")) {
            final String inner = text.substring(1, text.length() - 3).strip();
            return Operand.of(AddressingMode.INDIRECT_X, parseNumberInRange(lineNumber, inner, 0xFF));
        }
        if (text.endsWith(")")) {
            final String inner = text.substring(1, text.length() - 1).strip();
            return Operand.of(AddressingMode.INDIRECT, parseNumberInRange(lineNumber, inner, 0xFFFF));
        }
        throw new AssemblyException(lineNumber, "Invalid operand syntax: " + text);
    }

    private static Operand parseDirect(final int lineNumber, final String literal,
                                        final AddressingMode zeroPageMode, final AddressingMode absoluteMode) {
        final int value = parseNumberInRange(lineNumber, literal, 0xFFFF);
        return Operand.of(isZeroPage(literal, value) ? zeroPageMode : absoluteMode, value);
    }

    private static boolean isZeroPage(final String literal, final int value) {
        if (literal.startsWith("$")) {
            return literal.length() - 1 <= 2;
        }
        return value <= 0xFF;
    }

    private static boolean looksLikeNumericLiteral(final String text) {
        return text.startsWith("$") || Character.isDigit(text.charAt(0));
    }

    private static int parseNumberInRange(final int lineNumber, final String literal, final int maxValue) {
        final int value = parseNumber(lineNumber, literal);
        if (value < 0 || value > maxValue) {
            throw new AssemblyException(lineNumber, "Value out of range: " + literal);
        }
        return value;
    }

    private static int parseNumber(final int lineNumber, final String literal) {
        try {
            return literal.startsWith("$") ? Integer.parseInt(literal.substring(1), 16) : Integer.parseInt(literal);
        } catch (final NumberFormatException e) {
            throw new AssemblyException(lineNumber, "Invalid numeric literal: " + literal);
        }
    }

    private static boolean endsWithIgnoreCase(final String text, final String suffix) {
        return text.length() >= suffix.length()
                && text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private static String withoutLastTwoChars(final String text) {
        return text.substring(0, text.length() - 2);
    }
}
