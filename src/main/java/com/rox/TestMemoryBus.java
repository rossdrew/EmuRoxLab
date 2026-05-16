package com.rox;

public class TestMemoryBus implements MemoryBus {
    private final Memory memory;

    TestMemoryBus(final Memory memory){
        this.memory = memory;
    }

    @Override
    public int read(int address) {
        return 0;
    }

    @Override
    public void write(int address, int value) {

    }
}
