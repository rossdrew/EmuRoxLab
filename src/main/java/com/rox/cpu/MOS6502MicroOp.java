package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;
import com.rox.mem.LatchedMemoryBus;

enum MOS6502MicroOp implements MOS6502Operation {
    //Temporary
    NOP((e,m,a) -> {}),

    /* Implicit fetch cycle.  IR = mem[pc] */
    FETCH((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setIR(mem.fetch());
    }),

    /* ADL = mem[pc] */
    ADL_FROM_PC((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setADL(mem.fetch());
    }),

    /* addr_mem[adl] */
    LOAD_ADL_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getADL());
    }),

    /* A = adc(mem[addr]) */
    ADC((env, mem, alu) -> {
        alu.adc(env.getA(), mem.fetch());
    }),

    /* addr_mem[pc] */
    LOAD_PC_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
    }),

    /* ADL = mem[addr] */
    ADL_FETCH((env, mem, alu)->{
        env.setADL(mem.fetch());
    }),

    /* ADL = mem[addr+1] */
    ADH_INC_FETCH((env, mem, alu)->{
        int incrementedMemoryLocation = (mem.getAddressedMemory() + 1 & 0xFF);
        mem.loadMemoryAddress(incrementedMemoryLocation);
        env.setADH(mem.fetch());
    }),

    /* addr_mem = addre_mem + x */
    X_OFFSET_ADDRESS((env, mem, alu)->{
        int tmp = mem.getAddressedMemory();
        mem.loadMemoryAddress((tmp + env.getX()) & 0xFF);
    }),

    //ADL = mem[pc]
    ADL_FROM_PC_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setADL(mem.fetch());
    }),

    //ADH = mem[pc]
    ADH_FROM_PC((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setADH(mem.fetch());
    }),

    //addr_mem[adh:adl]
    ABS_ADDRESS((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getAD());
    }),

    //addr_mem[(adh:adl)+x]
    AD_PLUS_X((env, mem, alu)->{
        int originalHigh = env.getADH();

        int low = env.getADL() + env.getX();
        env.setADL(low & 0xFF);

        if (low > 0xFF) {
            env.requestAdditionalOp((environment, m, a) -> {
                m.fetch(); //dummy read
                env.additionalTickCompleted();
            });
            env.setADH((originalHigh + 1) & 0xFF);
        }

        mem.loadMemoryAddress(env.getAD());
    }),

    //addr_mem[(adh:adl)+y]
    AD_PLUS_Y((env, mem, alu)->{
        int originalHigh = env.getADH();

        int low = env.getADL() + env.getY();
        env.setADL(low & 0xFF);

        if (low > 0xFF) {
            env.requestAdditionalOp((environment, m, a) -> {
                m.fetch(); //Dummy read
                env.additionalTickCompleted();
            });
            env.setADH((originalHigh + 1) & 0xFF);
        }

        mem.loadMemoryAddress(env.getAD());
    }),

    //Used to waste an additional tick
//    ADDITIONAL_TICK((env, mem, alu)->{
//        env.additionalTickCompleted();
//    }),

    DUMMY_READ((env,mem,alu) -> {
        mem.fetch(); //read that goes nowhere
    }),

    ADJUSTED_ADDRESS((env, mem, alu) -> {
    }), // read adjusted address
    PAGE_CROSS_CYCLE((env, mem, alu) -> {
    }); // optional extra page-cross cycle

    private final MOS6502Operation op;

    @Override
    public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
        op.execute(environment, memory, alu);
    }

    MOS6502MicroOp(final MOS6502Operation op) {
        this.op = op;
    }
}
