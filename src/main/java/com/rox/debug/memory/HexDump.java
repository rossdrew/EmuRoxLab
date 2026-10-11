package com.rox.debug.memory;

import java.util.function.IntUnaryOperator;

/** Classic hex-dump text: 16 bytes per row, each row prefixed with its address, optionally with an ASCII column. */
public final class HexDump {
    private static final int BYTES_PER_ROW = 16;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_PRINTABLE = 0x7E;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

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
     * Two uppercase hex digits for {@code value}'s low byte - by table lookup rather than
     * {@code String.format}, which is slow enough to matter when a viewer redraws a 32KB region
     * (~35,000 bytes) several times a second.
     */
    private static void appendHex(final StringBuilder out, final int value){
        out.append(HEX_DIGITS[(value >> 4) & 0x0F]).append(HEX_DIGITS[value & 0x0F]);
    }
}
