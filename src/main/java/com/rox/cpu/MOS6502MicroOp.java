package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;
import com.rox.mem.LatchedMemoryBus;

//XXX There's probably a fair bit of redundancy here
enum MOS6502MicroOp implements MOS6502Operation {
    //Temporary
    NOP((e,m,a) -> {}),

    /** Fetch next instruction. <b>Should be implicit</b>  <code>instruction_register := mem[pc++]</code> */
    FETCH((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getAndIncrementPC());
        env.setIR(mem.fetch());
    }),

    /** Load address into address bus : <code>address_bus := mem[00:adl]</code> */
    ADDRESS_ADL((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getADL());
    }),

    /** Load address from program counter into address bus : <code>address_bus := mem[pc++]</code> */
    ADDRESS_PC((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getAndIncrementPC());
    }),

    /** Pull addressed value and load into address bus : <code>address_bus := mem[address_bus]</code> */
    ADDRESS_MEM_POINTER((env, mem, alu) -> {
        mem.loadMemoryAddress(mem.fetch());
    }),

    /**
     * Load the next addressed value into ADH : <code>adl := mem[address_bus + 1]</code>
     * Due to a (mimicked) hardware bug in the 6502, this wraps $00FF to $0000 rather than $01000
     *
     * XXX This probably needs to go in favor of [ADDRESS_PC, MEM_TO_ADH]
     * */
    NEXT_MEM_TO_ADH((env, mem, alu)->{
        int incrementedMemoryLocation = (mem.getAddressedMemory() + 1) & 0xFF;
        mem.loadMemoryAddress(incrementedMemoryLocation);
        env.setADH(mem.fetch());
    }),

    /** Increment the program counter : <code>pc := pc++</code>*/
    INC_PC((env, mem, alu)->{
        env.getAndIncrementPC();
    }),

    /** Increment the address register directly with low byte wrapping : <code>adl := adl++</code>*/
    INC_ADL((env, mem, alu)->{
        env.setADL((env.getADL() + 1) & 0xFF);
    }),

    /** Set the program counter to the state of the address bus : <code>pc := ad</code>*/
    AD_TO_PC((env, mem, alu)->{
        env.setPC(env.getAD());
    }),

    /** Take the currently addressed memory location and add X:  <code>address_bus := mem[address_bus + X]</code> */
    X_OFFSET_ADDRESS((env, mem, alu)->{
        int basePointer = mem.fetch();
        mem.loadMemoryAddress((basePointer + env.getX()) & 0xFF);
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

    /** Add Y to ADL, wrapping rather than carrying to ADH on overflow: <code>ADL := ADL + Y</code> */
    ADL_PLUS_Y((env, mem, alu)->{
        int low = env.getADL() + env.getY();
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

    /** Set static flags (N and Z) based on the value of X */
    SET_FLAGS_ON_X((env, mem, alu)->{
        alu.setStaticFlags(env.getX());
    }),

    /** Set static flags (N and Z) based on the value of Y */
    SET_FLAGS_ON_Y((env, mem, alu)->{
        alu.setStaticFlags(env.getY());
    }),

    /** Set Accumulator to the value on the data bus: <code>A := mem[address_bus]</code> */
    A_FROM_AD((env, mem, alu) -> {
        env.setA(mem.fetch());
    }),

    /** Set X to the value on the data bus: <code>X := mem[address_bus]</code> */
    X_FROM_AD((env, mem, alu) -> {
        env.setX(mem.fetch());
    }),

    /** Set X to the value on the data bus: <code>X := mem[address_bus]</code> */
    Y_FROM_AD((env, mem, alu) -> {
        env.setY(mem.fetch());
    }),

    /** Set A to the value at the location specified by the address bus: <code>A := mem[AD]</code> */
    A_FROM_MEM((env, mem, alu)->{
        env.setA(mem.fetch());
    }),

    /** Set X to the value at the location specified by the address bus: <code>X := mem[AD]</code> */
    X_FROM_MEM((env, mem, alu)->{
        env.setX(mem.fetch());
    }),

    /** Set Y to the value at the location specified by the address bus: <code>Y := mem[AD]</code> */
    Y_FROM_MEM((env, mem, alu)->{
        env.setY(mem.fetch());
    }),

    /** Load value from memory into ADL : <code>adl := mem[address_bus]</code> */
    ADL_FROM_MEM((env, mem, alu)->{
        env.setADL(mem.fetch());
    }),

    /** Load value from memory into ADH : <code>adh := mem[address_bus]</code> */
    ADH_FROM_MEM((env, mem, alu)->{
        env.setADH(mem.fetch());
    }),

    /** Set the program counter low byte to the state of the address bus low byte : <code>pcl := adl</code>*/
    PCL_FROM_MEM((env, mem, alu)->{
        env.setPCL(mem.fetch());
    }),

    /** Set the program counter high byte to the state of the address bus high byte : <code>pch := adh</code>*/
    PCH_FROM_MEM((env, mem, alu)->{
        env.setPCH(mem.fetch());
    }),

    /** Set the memory locations indicated by the address bus to the value of A: <code>mem[AD] := A</code> */
    MEM_FROM_A((env, mem, alu)->{
        mem.store(env.getA());
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
    }),

    /** Perform {@link MOS6502ALU#eor(int)} with the value at <code>mem[address_bus]</code> */
    EOR((env, mem, alu) -> {
        alu.eor(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#sbc(int)} with the value at <code>mem[address_bus]</code> */
    SBC((env, mem, alu) -> {
        alu.sbc(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#cmp(int)} with the value at <code>mem[address_bus]</code> */
    CMP((env, mem, alu) -> {
        alu.cmp(mem.fetch());
    });

    private final MOS6502Operation op;

    @Override
    public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
        op.execute(environment, memory, alu);
    }

    MOS6502MicroOp(final MOS6502Operation op) {
        this.op = op;
    }
}
