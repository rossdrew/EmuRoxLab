package com.rox.mem;

public interface LatchedMemoryBus {
    void loadMemoryAddress(final int memory);
    int fetch();
    void store(final int value);
}
