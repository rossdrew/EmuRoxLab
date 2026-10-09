package com.rox.mem;

import static com.rox.ByteUtil.BYTE_MASK;

public class RAM implements Memory {
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

    /** A copy of every byte, for a save state. */
    public int[] snapshot() {
        return memory.clone();
    }

    /**
     * Checks {@code contents} could be {@link #restore}d, without changing anything.
     *
     * @throws IllegalArgumentException if {@code contents} isn't exactly this RAM's size
     */
    public void checkRestorable(final int[] contents) {
        if (contents.length != memory.length) {
            throw new IllegalArgumentException(
                    "Expected " + memory.length + " bytes of RAM, got " + contents.length);
        }
    }

    /**
     * Overwrites every byte with {@code contents} (copied, not kept) - the counterpart of {@link #snapshot()}.
     *
     * @throws IllegalArgumentException if {@code contents} isn't exactly this RAM's size
     */
    public void restore(final int[] contents) {
        checkRestorable(contents);
        for (int i = 0; i < memory.length; i++) {
            memory[i] = contents[i] & BYTE_MASK;
        }
    }
}
