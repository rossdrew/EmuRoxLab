package com.rox.mem;

/**
 * 16bit addressed, 8bit data memory
 */
public class Latched8BitMemoryBus implements LatchedMemoryBus {
    private final MemoryBus memoryBus;
    private int addressedMemory;

    public final static int ADDRESS_MASK = 0xFFFF;
    public final static int DATA_MASK = 0xFF;

    public Latched8BitMemoryBus(final MemoryBus memoryBus){
        this.memoryBus = memoryBus;
    }

    @Override
    public void loadMemoryAddress(final int memory) {
        this.addressedMemory = memory & ADDRESS_MASK;
    }

    @Override
    public int getAddressedMemory(){
        return this.addressedMemory & ADDRESS_MASK;
    }

    @Override
    public int fetch() {
        return memoryBus.read(addressedMemory);
    }

    @Override
    public void store(final int value) {
        memoryBus.write(addressedMemory, value & DATA_MASK);
    }
}
