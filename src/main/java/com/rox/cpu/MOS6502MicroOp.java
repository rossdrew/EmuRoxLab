package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;
import com.rox.mem.LatchedMemoryBus;

//XXX There's probably a fair bit of redundancy here
enum MOS6502MicroOp implements MOS6502Operation {
    //Temporary
    NOP((e,m,a) -> {}),

    /** Fetch next instruction. <b>Should be implicit</b>  <code>instruction_register := mem[pc++]</code> */
    FETCH((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
        env.setIR(mem.fetch());
    }),

    /** Load address into address bus : <code>address_bus := mem[00:adl]</code> */
    ADDRESS_ADL((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getADL());
    }),

    /** Load address from program counter into address bus : <code>address_bus := mem[pc++]</code> */
    ADDRESS_PC((env, mem, alu) -> {
        mem.loadMemoryAddress(env.pc());
    }),

    /** Pull addressed value and load into address bus : <code>address_bus := mem[address_bus]</code> */
    ADDRESS_MEM_POINTER((env, mem, alu) -> {
        mem.loadMemoryAddress(mem.fetch());
    }),

    /** Load value from memory into ADL : <code>adl := mem[address_bus]</code> */
    MEM_TO_ADL((env, mem, alu)->{
        env.setADL(mem.fetch());
    }),

    /** Load the next addressed value into ADH : <code>adl := mem[address_bus + 1]</code>*/
    NEXT_MEM_TO_ADH((env, mem, alu)->{
        int incrementedMemoryLocation = (mem.getAddressedMemory() + 1) & 0xFF;
        mem.loadMemoryAddress(incrementedMemoryLocation);
        env.setADH(mem.fetch());
    }),

    /** Take the currently addressed memory location and add X:  <code>address_bus := mem[address_bus + X]</code> */
    X_OFFSET_ADDRESS((env, mem, alu)->{
        int basePointer = mem.fetch();
        mem.loadMemoryAddress((basePointer + env.getX()) & 0xFF);
    }),

    //ADH = mem[pc]
    ADH_FROM_PC((env, mem, alu) -> {
        //XXX Could be replaced with (ADDRESS_PC, MEM_TO_ADH)
        mem.loadMemoryAddress(env.pc());
        env.setADH(mem.fetch());
    }),

    //addr_mem[adh:adl]
    ADDRESS_AD((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getAD());
    }),

    //addr_mem[0x00:adl]
    ADL_PLUS_X((env, mem, alu)->{
        int low = env.getADL() + env.getX();
        env.setADL(low & 0xFF);
        env.setADH(0x00);
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

    SET_FLAGS_ON_A((env, mem, alu)->{
        alu.setStaticFlags(env.getA());
    }),

    A_FROM_AD((env, mem, alu) -> {
        final int newValue = mem.fetch();
        env.setA(newValue);
    }),

    A_FROM_PC((env, mem, alu)->{
        mem.loadMemoryAddress(env.pc());
        final int newValue = mem.fetch();
        env.setA(newValue);
    }),

    /* A = adc(mem[addr]) */
    ADC((env, mem, alu) -> {
        alu.adc(mem.fetch());
    }),

    /* A = and(mem[addr]) */
    AND((env, mem, alu) -> {
        alu.and(mem.fetch());
    }),

    /* A = ora(mem[addr]) */
    ORA((env, mem, alu) -> {
        alu.ora(mem.fetch());
    });

    //Should be easy wins
    //TODO EOR
    //TODO SBC
    //TODO CMP

    //TODO LDY
    //TODO LDX

    //The real test
    //TODO JMP

    private final MOS6502Operation op;

    @Override
    public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
        op.execute(environment, memory, alu);
    }

    MOS6502MicroOp(final MOS6502Operation op) {
        this.op = op;
    }
}
