package com.rox.mem;

public class Latched8BitMemoryBus implements LatchedMemoryBus {
    private final MemoryBus memoryBus;
    private int addressedMemory;

    public Latched8BitMemoryBus(final MemoryBus memoryBus){
        this.memoryBus = memoryBus;
    }

    @Override
    public void loadMemoryAddress(final int memory) {
        this.addressedMemory = memory & 0xFF;
    }

    @Override
    public int fetch() {
        return memoryBus.read(addressedMemory);
    }

    @Override
    public void store(final int value) {
        memoryBus.write(addressedMemory, value & 0xFF);
    }
}
