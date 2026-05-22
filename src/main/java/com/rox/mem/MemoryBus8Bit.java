package com.rox.mem;

public class MemoryBus8Bit implements MemoryBus {
    private final Memory memory;

    public MemoryBus8Bit(final Memory memory){
        this.memory = memory;
    }

    @Override
    public int read(int address) {
        return memory.read(0xFF & address);
    }

    @Override
    public void write(int address, int value) {
        memory.write(0xFF & address, 0xFF & value);
    }
}
