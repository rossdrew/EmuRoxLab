package com.rox;

public class TestRAM implements MemoryBus {
    private final int[] memory;

    public TestRAM(final int size) {
        this.memory = new int[size];
    }

    @Override
    public int read(final int address) {
        return memory[address & (memory.length - 1)] & 0xFF;
    }

    @Override
    public void write(final int address, final int value) {
        memory[address & (memory.length - 1)] = value & 0xFF;
    }
}
