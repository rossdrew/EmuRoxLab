package com.rox.mem;

public class MemoryBus8Bit implements MemoryBus {
    private final Memory memory;

    public final static int ADDRESS_MASK = 0xFFFF;
    public final static int DATA_MASK = 0xFF;

    public MemoryBus8Bit(final Memory memory){
        this.memory = memory;
    }

    @Override
    public int read(int address) {
        return memory.read(ADDRESS_MASK & address);
    }

    @Override
    public void write(int address, int value) {
        memory.write(address & ADDRESS_MASK, value & DATA_MASK);
    }
}
