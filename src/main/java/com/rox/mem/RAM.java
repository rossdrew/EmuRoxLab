package com.rox.mem;

public class RAM implements Memory {
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    private final int[] memory;

    public RAM(final int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException(
                    "RAM size must be power of two");
        }
        this.memory = new int[size];
    }

    @Override
    public int read(final int address) {
        return memory[address & (memory.length - 1)] & BYTE_MASK;
    }

    @Override
    public void write(final int address, final int value) {
        memory[address & (memory.length - 1)] = value & BYTE_MASK;
    }
}
