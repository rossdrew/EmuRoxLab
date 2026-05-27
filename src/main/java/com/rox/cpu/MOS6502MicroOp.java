package com.rox.cpu;

import com.rox.mem.LatchedMemoryBus;

enum MOS6502MicroOp implements MOS6502.MOS6502Operation {
    /* Implicit fetch cycle.  IR = mem[pc] */
    FETCH((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setIR(mem.fetch());
    }),

    /* ADL = mem[pc] */
    Z_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setADL(mem.fetch());
    }),

    /* addr_mem[adl] */
    Z_ADDRESS((env, mem, alu) -> {
        //this part costs
        mem.loadMemoryAddress(env.getADL());
    }),

    /* A = adc(mem[addr]) */
    ADC((env, mem, alu) -> {
        alu.adc(env.getA(), mem.fetch());
    }),

    /* addr_mem[pc] */
    VALUE_FROM_PC_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
    }),

    LOW_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {
    }), //PC -> low address
    HIGH_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {
    }), //PC -> high address

    ADJUSTED_ADDRESS((env, mem, alu) -> {
    }), // read adjusted address
    PAGE_CROSS_CYCLE((env, mem, alu) -> {
    }); // optional extra page-cross cycle

    private final MOS6502.MOS6502Operation op;

    @Override
    public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
        op.execute(environment, memory, alu);
    }

    MOS6502MicroOp(final MOS6502.MOS6502Operation op) {
        this.op = op;
    }
}
