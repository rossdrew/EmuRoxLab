package com.rox.debug.labels;

/**
 * A name for a byte, or a run of bytes, in the CPU's address space - e.g. Zelda's rupee count. Names
 * are a single word (no whitespace), so they read cleanly in a labels file and a watch list.
 *
 * @param length how many bytes, from {@code start} - at least 1, and not running past {@code $FFFF}
 */
public record MemoryLabel(String name, int start, int length) {
    private static final int MAX_ADDRESS = 0xFFFF;

    public MemoryLabel {
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace)){
            throw new IllegalArgumentException("A label name must be one word, got '" + name + "'");
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
