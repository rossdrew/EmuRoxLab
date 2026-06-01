package com.rox.mem;

public interface LatchedMemoryBus {
    void loadMemoryAddress(final int memory);
    int getAddressedMemory();
    int fetch();
    void store(final int value);
}
