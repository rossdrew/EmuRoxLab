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

    /** Set ADH to the value at the address indicated by the current state of the program counter: <code>ADH := mem[pc++]</code> */
    ADH_FROM_PC((env, mem, alu) -> {
        //XXX Could be replaced with (ADDRESS_PC, MEM_TO_ADH)
        mem.loadMemoryAddress(env.pc());
        env.setADH(mem.fetch());
    }),

    /** Load address register into address bus : <code>address_bus := mem[adh:adl]</code> */
    ADDRESS_AD((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getAD());
    }),

    /** Add X to ADL, wrapping rather than carrying to ADH on overflow: <code>ADL := ADL + X</code> */
    ADL_PLUS_X((env, mem, alu)->{
        int low = env.getADL() + env.getX();
        env.setADL(low & 0xFF);
        env.setADH(0x00);
    }),

    /** Load address bus with AD + X: <code>address_bus := AD := AD + X</code> */
    ADDRESS_AD_PLUS_X((env, mem, alu)->{
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

    /** Load address bus with AD + Y: <code>address_bus := AD := AD + Y</code> */
    ADDRESS_AD_PLUS_Y((env, mem, alu)->{
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

    /** Set static flags (N and Z) based on the value of the accumulator */
    SET_FLAGS_ON_A((env, mem, alu)->{
        alu.setStaticFlags(env.getA());
    }),

    /** Set Accumulator to the value on the data bus: <code>A := mem[address_bus]</code> */
    A_FROM_AD((env, mem, alu) -> {
        final int newValue = mem.fetch();
        env.setA(newValue);
    }),

    /** Set Accumulator to the value at the location specified by the program counter: <code>A := mem[pc++]</code> */
    A_FROM_PC((env, mem, alu)->{
        mem.loadMemoryAddress(env.pc());
        final int newValue = mem.fetch();
        env.setA(newValue);
    }),

    /** Perform {@link MOS6502ALU#adc(int)} with the value at <code>mem[address_bus]</code> */
    ADC((env, mem, alu) -> {
        alu.adc(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#and(int)} with the value at <code>mem[address_bus]</code> */
    AND((env, mem, alu) -> {
        alu.and(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#ora(int)} with the value at <code>mem[address_bus]</code> */
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
