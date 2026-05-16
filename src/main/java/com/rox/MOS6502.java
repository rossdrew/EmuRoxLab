package com.rox;

public class MOS6502 implements ClockWatcher {
    private final MemoryBus memoryBus;

    private int pc;

    public MOS6502(final MemoryBus memoryBus) {
        this.memoryBus = memoryBus;
        pc = 0;
    }

    @Override
    public void tick() {
        int instruction = memoryBus.read(pc++);
        //decode it
        //execute it
    }
}
