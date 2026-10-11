package com.rox.debug.memory;

import java.util.function.IntUnaryOperator;

/** Classic hex-dump text: 16 bytes per row, each row prefixed with its address, optionally with an ASCII column. */
public final class HexDump {
    private static final int BYTES_PER_ROW = 16;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_PRINTABLE = 0x7E;

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
        for (int row = start; row <= end; row += BYTES_PER_ROW){
            final int rowEnd = Math.min(row + BYTES_PER_ROW - 1, end);
            final StringBuilder characters = new StringBuilder();
            out.append(String.format("%04X:", row));
            for (int address = row; address <= rowEnd; address++){
                final int value = peek.applyAsInt(address);
                out.append(String.format(" %02X", value));
                characters.append(value >= FIRST_PRINTABLE && value <= LAST_PRINTABLE ? (char) value : '.');
            }
            if (ascii){
                out.append("  |").append(characters).append('|');
            }
            out.append('\n');
        }
        return out.toString();
    }
}
