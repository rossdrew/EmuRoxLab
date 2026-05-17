package com.rox.mem;

public class MemoryBus8Bit implements MemoryBus {
    private final Memory memory;
    private int addressBus;

    public MemoryBus8Bit(final Memory memory){
        this.memory = memory;
    }

    @Override
    public int read(int address) {
        return 0;
    }

    @Override
    public void write(int address, int value) {
        memory.write(address & 0xFF, value & 0xFF);
    }
}
