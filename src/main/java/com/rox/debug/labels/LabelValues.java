package com.rox.debug.labels;

import java.util.Locale;
import java.util.function.IntUnaryOperator;

/**
 * A label's bytes as text, and back - raw bytes, one per address, for any length of label (a number
 * view for multi-byte values can come later). Input accepts {@code $1F}, {@code 0x1F} or {@code 31} per
 * byte, separated by whitespace.
 */
public final class LabelValues {
    private static final int MAX_BYTE = 0xFF;
    private static final int HEX_RADIX = 16;
    private static final int DECIMAL_RADIX = 10;
    private static final int MAX_DIGITS = 6;
    private static final String WHITESPACE = "\\p{javaWhitespace}+";

    private LabelValues(){
    }

    /** {@code $01 $02} */
    public static String hex(final MemoryLabel label, final IntUnaryOperator peek){
        final StringBuilder out = new StringBuilder();
        for (int address = label.start(); address <= label.end(); address++){
            if (!out.isEmpty()){
                out.append(' ');
            }
            out.append(String.format("$%02X", peek.applyAsInt(address)));
        }
        return out.toString();
    }

    /** {@code 1 2} */
    public static String decimal(final MemoryLabel label, final IntUnaryOperator peek){
        final StringBuilder out = new StringBuilder();
        for (int address = label.start(); address <= label.end(); address++){
            if (!out.isEmpty()){
                out.append(' ');
            }
            out.append(peek.applyAsInt(address));
        }
        return out.toString();
    }

    /**
     * One byte per address of {@code label}, from e.g. {@code "$1F"} or {@code "1 0x02 $FF"}.
     *
     * @throws IllegalArgumentException with a readable reason if it isn't exactly one 0-255 value per byte
     */
    public static int[] parse(final MemoryLabel label, final String text){
        final String trimmed = text.strip();
        final String[] tokens = trimmed.isEmpty() ? new String[0] : trimmed.split(WHITESPACE);
        if (tokens.length != label.length()){
            throw new IllegalArgumentException(String.format("'%s' covers %d byte%s - enter %d value%s, got %d",
                    label.name(), label.length(), label.length() == 1 ? "" : "s",
                    label.length(), label.length() == 1 ? "" : "s", tokens.length));
        }
        final int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++){
            values[i] = parseByte(tokens[i]);
        }
        return values;
    }

    private static int parseByte(final String token){
        final boolean hex = token.startsWith("$") || token.toLowerCase(Locale.ROOT).startsWith("0x");
        final String digits = token.startsWith("$") ? token.substring(1) : hex ? token.substring(2) : token;
        final int radix = hex ? HEX_RADIX : DECIMAL_RADIX;
        //checked by hand rather than trusting parseInt, which would also accept a sign
        if (digits.isEmpty() || !digits.chars().allMatch(c -> Character.digit(c, radix) >= 0)){
            throw new IllegalArgumentException("'" + token + "' isn't a byte value - use e.g. 31, $1F or 0x1F");
        }
        //anything this long is out of range anyway - and parseInt would overflow on it
        if (digits.length() > MAX_DIGITS || Integer.parseInt(digits, radix) > MAX_BYTE){
            throw new IllegalArgumentException("'" + token + "' is more than a byte holds (0-255, $00-$FF)");
        }
        return Integer.parseInt(digits, radix);
    }
}
