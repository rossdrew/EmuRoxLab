package com.rox.debug.memory;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

/** Classic hex-dump text: 16 bytes per row, each row prefixed with its address, optionally with an ASCII column. */
public final class HexDump {
    private static final int BYTES_PER_ROW = 16;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_PRINTABLE = 0x7E;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int ADDRESS_COLUMN_WIDTH = "0000:".length();
    private static final int BYTE_WIDTH = " 00".length();
    private static final String ASCII_OPEN = "  |";
    private static final int HEX_RADIX = 16;
    private static final int MAX_BYTE_DIGITS = 2;

    private HexDump(){
    }

    /**
     * {@code 0010: 00 01 ... 0F} per row, plus {@code  |....|} when {@code ascii} (non-printable bytes as
     * {@code .}). A last row shorter than 16 bytes is just shorter.
     *
     * @param end inclusive
     */
    public static String format(final IntUnaryOperator peek, final int start, final int end, final boolean ascii){
        final StringBuilder out = new StringBuilder();
        final StringBuilder characters = new StringBuilder(BYTES_PER_ROW);
        for (int row = start; row <= end; row += BYTES_PER_ROW){
            final int rowEnd = Math.min(row + BYTES_PER_ROW - 1, end);
            characters.setLength(0);
            appendHex(out, row >> 8);
            appendHex(out, row);
            out.append(':');
            for (int address = row; address <= rowEnd; address++){
                final int value = peek.applyAsInt(address);
                out.append(' ');
                appendHex(out, value);
                characters.append(value >= FIRST_PRINTABLE && value <= LAST_PRINTABLE ? (char) value : '.');
            }
            if (ascii){
                out.append("  |").append(characters).append('|');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * The address of the byte at {@code offset} in {@link #format}'s text for the same arguments - its hex
     * column (including the space before it) or its ASCII column. Empty for the address column, the
     * separators and anywhere past the end.
     */
    public static OptionalInt addressAt(final int offset, final int start, final int end, final boolean ascii){
        final int rowLength = rowLength(BYTES_PER_ROW, ascii);
        final int rowStart = start + (offset / rowLength) * BYTES_PER_ROW;
        if (rowStart > end){
            return OptionalInt.empty();
        }
        final int bytesInRow = Math.min(BYTES_PER_ROW, end - rowStart + 1);
        final int column = offset % rowLength;
        final int hexEnd = ADDRESS_COLUMN_WIDTH + bytesInRow * BYTE_WIDTH;
        if (column >= ADDRESS_COLUMN_WIDTH && column < hexEnd){
            return OptionalInt.of(rowStart + (column - ADDRESS_COLUMN_WIDTH) / BYTE_WIDTH);
        }
        final int asciiStart = hexEnd + ASCII_OPEN.length();
        if (ascii && column >= asciiStart && column < asciiStart + bytesInRow){
            return OptionalInt.of(rowStart + column - asciiStart);
        }
        return OptionalInt.empty();
    }

    /**
     * A byte typed into a hex view: one or two hex digits, optionally after {@code $} or {@code 0x} -
     * so {@code 10} is {@code $10}, not ten.
     *
     * @throws IllegalArgumentException if it isn't one
     */
    public static int parseByte(final String text){
        final String trimmed = text.strip().toLowerCase(Locale.ROOT);
        final String digits = trimmed.startsWith("$") ? trimmed.substring(1)
                : trimmed.startsWith("0x") ? trimmed.substring(2) : trimmed;
        //checked by hand rather than trusting parseInt, which would also accept a sign
        if (digits.isEmpty() || digits.length() > MAX_BYTE_DIGITS
                || !digits.chars().allMatch(c -> Character.digit(c, HEX_RADIX) >= 0)){
            throw new IllegalArgumentException("'" + text + "' isn't a hex byte ($00-$FF)");
        }
        return Integer.parseInt(digits, HEX_RADIX);
    }

    /** Characters in a row of {@code bytes} bytes, newline included. */
    private static int rowLength(final int bytes, final boolean ascii){
        final int hex = ADDRESS_COLUMN_WIDTH + bytes * BYTE_WIDTH;
        return (ascii ? hex + ASCII_OPEN.length() + bytes + "|".length() : hex) + "\n".length();
    }

    /**
     * Two uppercase hex digits for {@code value}'s low byte - by table lookup rather than
     * {@code String.format}, which is slow enough to matter when a viewer redraws a 32KB region
     * (~35,000 bytes) several times a second.
     */
    private static void appendHex(final StringBuilder out, final int value){
        out.append(HEX_DIGITS[(value >> 4) & 0x0F]).append(HEX_DIGITS[value & 0x0F]);
    }
}
