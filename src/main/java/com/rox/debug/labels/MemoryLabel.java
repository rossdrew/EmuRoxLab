package com.rox.debug.labels;

import java.util.regex.Pattern;

/**
 * A name for a byte, or a run of bytes, in the CPU's address space - e.g. Zelda's rupee count. Names
 * are letters, digits, {@code _}, {@code .} and {@code -} only, so one always reads back unchanged from
 * a labels file - no whitespace (the field separator) and no {@code #} (the comment marker).
 *
 * @param length how many bytes, from {@code start} - at least 1, and not running past {@code $FFFF}
 */
public record MemoryLabel(String name, int start, int length) {
    private static final int MAX_ADDRESS = 0xFFFF;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.\\-]+");

    public MemoryLabel {
        if (name == null || !VALID_NAME.matcher(name).matches()){
            throw new IllegalArgumentException("A label name must be letters, digits, '_', '.' or '-' only, got '" + name + "'");
        }
        if (start < 0 || start > MAX_ADDRESS){
            throw new IllegalArgumentException(String.format("Label '%s' starts outside $0000-$FFFF: %d", name, start));
        }
        if (length < 1){
            throw new IllegalArgumentException("Label '" + name + "' must cover at least one byte, got " + length);
        }
        if (start + length - 1 > MAX_ADDRESS){
            throw new IllegalArgumentException(String.format("Label '%s' runs past $FFFF: $%04X + %d bytes", name, start, length));
        }
    }

    /** A single-byte label. */
    public static MemoryLabel at(final String name, final int address){
        return new MemoryLabel(name, address, 1);
    }

    /** A label covering {@code start} to {@code end}, both inclusive. */
    public static MemoryLabel spanning(final String name, final int start, final int end){
        return new MemoryLabel(name, start, end - start + 1);
    }

    /** The last byte covered, inclusive. */
    public int end(){
        return start + length - 1;
    }
}
