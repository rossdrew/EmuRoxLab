package com.rox.cpu.mos6502;

import com.rox.mem.LatchedMemoryBus;

@FunctionalInterface
public interface MOS6502Operation {
    void execute(final MOS6502Environment environment,
                 final LatchedMemoryBus memory,
                 final MOS6502ALU alu);
}