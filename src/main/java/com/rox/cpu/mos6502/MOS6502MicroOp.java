package com.rox.cpu.mos6502;

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

    /** Load address from program counter into address bus without advancing it : <code>address_bus := mem[pc]</code> */
    ADDRESS_CURRENT_PC((env, mem, alu) -> {
        mem.loadMemoryAddress(env.getPC());
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

    DUMMY_READ((env, mem, alu) -> {
        mem.fetch();
    }),

    /**
     * Load address bus with AD + X: <code>address_bus := AD := AD + X</code> <br/><br/>
     * XXX: Needs a better approach 1/2.<br/>
     * LDA_ABS_X is a read instruction, so the 6502 can optimistically try to read from the partially indexed address first; if adding X does not cross a page, that read is valid and the instruction finishes in 4 cycles, but if the low byte overflows, the CPU needs one extra cycle to fix the high byte and reread from the correct address. STA_ABS_X is a write instruction, so the CPU cannot safely do that optimistic access because an early write to the wrong address would corrupt memory; it must always spend the indexing/dummy-read cycle before performing the real write, making it 5 cycles whether or not a page is crossed.
     */
    ADDRESS_AD_PLUS_X((env, mem, alu)->{
        int originalHigh = env.getADH();

        int low = env.getADL() + env.getX();
        env.setADL(low & 0xFF);

        if (low > 0xFF) {
            env.setADH((originalHigh + 1) & 0xFF);
        }

        mem.loadMemoryAddress(env.getAD());
    }),

    /**
     * Load address bus with AD + X, adding an extra tick instruction if there's a page cross: <code>address_bus := AD := AD + X</code> <br/><br/>
     * XXX: Needs a better approach 1/2.<br/>
     * LDA_ABS_X is a read instruction, so the 6502 can optimistically try to read from the partially indexed address first; if adding X does not cross a page, that read is valid and the instruction finishes in 4 cycles, but if the low byte overflows, the CPU needs one extra cycle to fix the high byte and reread from the correct address. STA_ABS_X is a write instruction, so the CPU cannot safely do that optimistic access because an early write to the wrong address would corrupt memory; it must always spend the indexing/dummy-read cycle before performing the real write, making it 5 cycles whether or not a page is crossed.
     */
    ADDRESS_AD_PLUS_X_AND_PAGE_CROSS((env, mem, alu)->{
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
            env.setADH((originalHigh + 1) & 0xFF);
        }

        mem.loadMemoryAddress(env.getAD());
    }),

    /** Load address bus with AD + Y: <code>address_bus := AD := AD + Y</code> */
    ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS((env, mem, alu)->{
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

    /** Set A to the value of X <code>A := X</code> */
    A_FROM_X((env, mem, alu)->{
        env.setA(env.getX());
    }),


    /** Set A to the value of Y <code>A := Y</code> */
    A_FROM_Y((env, mem, alu)->{
        env.setA(env.getY());
    }),

    /** Set X to the value on the data bus: <code>X := mem[address_bus]</code> */
    X_FROM_AD((env, mem, alu) -> {
        env.setX(mem.fetch());
    }),

    /** Set X to the value of the Stack Pointer <code>X := Stack Pointer</code> */
    X_FROM_SP((env, mem, alu)->{
        env.setX(env.getStackPointer());
    }),

    /** Set the Stack Pointer to the value of X <code>Stack Pointer := X</code> */
    SP_FROM_X((env, mem, alu)->{
        env.setStackPointer(env.getX());
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

    /** Set X to the value of A <code>X := A</code> */
    X_FROM_A((env, mem, alu)->{
        env.setX(env.getA());
    }),

    /** Set Y to the value of A <code>Y := A</code> */
    Y_FROM_A((env, mem, alu)->{
        env.setY(env.getA());
    }),

    /** Set the memory locations indicated by the address bus to the value of X: <code>mem[AD] := X</code> */
    MEM_FROM_X((env, mem, alu)->{
        mem.store(env.getX());
    }),

    /** Set the memory locations indicated by the address bus to the value of Y: <code>mem[AD] := Y</code> */
    MEM_FROM_Y((env, mem, alu)->{
        mem.store(env.getY());
    }),

    /** Increment the value in the X register</code> */
    INX((env, mem, alu) -> {
        env.setX((env.getX() + 1) & 0xFF);
    }),

    /** Decrement the value in the X register</code> */
    DEX((env, mem, alu) -> {
        env.setX((env.getX() - 1) & 0xFF);
    }),

    /** Increment the value in the Y register</code> */
    INY((env, mem, alu) -> {
        env.setY((env.getY() + 1) & 0xFF);
    }),

    /** Decrement the value in the Y register</code> */
    DEY((env, mem, alu) -> {
        env.setY((env.getY() - 1) & 0xFF);
    }),

    /** Set the interrupt flag to true <code>I := true</code> **/
    INTERRUPT((env, mem, alu) -> {
        env.setI(true);
    }),

    /** Clear the carry flag <code>C := false</code> **/
    CLEAR_CARRY((env, mem, alu) -> {
        env.setCarry(false);
    }),

    /** Set the carry flag <code>C := true</code> **/
    SET_CARRY((env, mem, alu) -> {
        env.setCarry(true);
    }),

    /** Clear the interrupt disable flag <code>I := false</code> **/
    CLEAR_INTERRUPT_DISABLE((env, mem, alu) -> {
        env.setI(false);
    }),

    /** Clear the overflow flag <code>V := false</code> **/
    CLEAR_OVERFLOW((env, mem, alu) -> {
        env.setV(false);
    }),

    /** Set the decimal flag <code>D := true</code> **/
    SET_DECIMAL((env, mem, alu) -> {
        env.setD(true);
    }),

    /** Clear the decimal flag <code>D := false</code> **/
    CLEAR_DECIMAL((env, mem, alu) -> {
        env.setD(false);
    }),

    /** Address the low interrupt vector ready to be read <code>mem[$FF:FE)</code> */
    ADDRESS_IV_LOW((env, mem, alu) -> {
        mem.loadMemoryAddress(0xFFFE);
    }),

    /** Address the high interrupt vector ready to be read <code>mem[$FF:FF)</code> */
    ADDRESS_IV_HIGH((env, mem, alu) -> {
        mem.loadMemoryAddress(0xFFFF);
    }),

    /** Address the low byte of the NMI vector ready to be read <code>mem[$FF:FA)</code> */
    ADDRESS_NMI_LOW((env, mem, alu) -> {
        mem.loadMemoryAddress(0xFFFA);
    }),

    /** Address the high byte of the NMI vector ready to be read <code>mem[$FF:FB)</code> */
    ADDRESS_NMI_HIGH((env, mem, alu) -> {
        mem.loadMemoryAddress(0xFFFB);
    }),

    /** Push the current value of PCH onto the stack and decrement the stack pointer <code>mem[$01:SP--] := PCH</code> **/
    PUSH_PCH((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        mem.store(env.getPCH());
        env.setStackPointer((env.getStackPointer()-1) & 0xFF);
    }),

    /** Push the current value of PCL onto the stack and decrement the stack pointer <code>mem[$01:SP--] := PCL</code> **/
    PUSH_PCL((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        mem.store(env.getPCL());
        env.setStackPointer((env.getStackPointer()-1) & 0xFF);
    }),

    /** Push the current value of A onto the stack and decrement the stack pointer <code>mem[$01:SP--] := A</code> **/
    PUSH_A((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        mem.store(env.getA());
        env.setStackPointer((env.getStackPointer()-1) & 0xFF);
    }),

    /** Increment the stack pointer and pull the value of A from that location <code>A := mem[$01:++SP]</code> **/
    PULL_A((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        env.setA(mem.fetch());
    }),

    /** Increment the stack pointer and pull the value of PCL from that location <code>PCL := mem[$01:++SP]</code> **/
    PULL_PCL((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        env.setPCL(mem.fetch());
    }),

    /** Increment the stack pointer and pull the value of PCH from that location <code>PCH := mem[$01:++SP]</code> **/
    PULL_PCH((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        env.setPCH(mem.fetch());
    }),

    /** Increment the stack pointer <code>SP++</code>*/
    INC_SP((env, mem, alu) -> {
        env.setStackPointer((env.getStackPointer()+1) & 0xFF);
    }),

    /** Push the current state of the processor onto the stack with break flag set to 1 and decrement the stack pointer <code>mem[$01:SP--] := Processor Status + break flag</code> **/
    PUSH_PROCESSOR_STATUS_WITH_BREAK((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        mem.store(env.getStatus(true));
        env.setStackPointer((env.getStackPointer()-1) & 0xFF);
    }),

    /** Push the current state of the processor onto the stack with break flag set to 0 and decrement the stack pointer, as a hardware IRQ/NMI does (distinguishing it from BRK/PHP) <code>mem[$01:SP--] := Processor Status</code> **/
    PUSH_PROCESSOR_STATUS_WITHOUT_BREAK((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        mem.store(env.getStatus(false));
        env.setStackPointer((env.getStackPointer()-1) & 0xFF);
    }),

    /** Set the current state of the processor from the stack and increment the stack pointer <code>Processor Status := mem[$01:SP++]</code> **/
    PULL_PROCESSOR_STATUS((env, mem, alu) -> {
        mem.loadMemoryAddress(0x0100 | env.getStackPointer());
        env.setStatus(mem.fetch());
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
    }),

    /** Perform {@link MOS6502ALU#cpx(int)} with the value at <code>mem[address_bus]</code> */
    CPX((env, mem, alu) -> {
        alu.cpx(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#cpy(int)} with the value at <code>mem[address_bus]</code> */
    CPY((env, mem, alu) -> {
        alu.cpy(mem.fetch());
    }),

    /** Write the addressed value back unchanged, modeling the 6502's read-modify-write dummy write cycle: <code>mem[address_bus] := mem[address_bus]</code> */
    DUMMY_WRITE_MEM((env, mem, alu) -> {
        mem.store(mem.fetch());
    }),

    /** Increment the addressed memory value, wrapping on overflow: <code>mem[address_bus] := mem[address_bus] + 1</code> */
    INC_MEM((env, mem, alu) -> {
        mem.store((mem.fetch() + 1) & 0xFF);
    }),

    /** Decrement the addressed memory value, wrapping on underflow: <code>mem[address_bus] := mem[address_bus] - 1</code> */
    DEC_MEM((env, mem, alu) -> {
        mem.store((mem.fetch() - 1) & 0xFF);
    }),

    /** Set static flags (N and Z) based on the addressed memory value */
    SET_FLAGS_ON_MEM((env, mem, alu) -> {
        alu.setStaticFlags(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#bit(int)} with the value at <code>mem[address_bus]</code> */
    BIT_TEST((env, mem, alu) -> {
        alu.bit(mem.fetch());
    }),

    /** Perform {@link MOS6502ALU#asl(int)} on the accumulator: <code>A := ASL(A)</code> */
    ASL_ACC((env, mem, alu) -> {
        env.setA(alu.asl(env.getA()));
    }),

    /** Perform {@link MOS6502ALU#lsr(int)} on the accumulator: <code>A := LSR(A)</code> */
    LSR_ACC((env, mem, alu) -> {
        env.setA(alu.lsr(env.getA()));
    }),

    /** Perform {@link MOS6502ALU#rol(int)} on the accumulator: <code>A := ROL(A)</code> */
    ROL_ACC((env, mem, alu) -> {
        env.setA(alu.rol(env.getA()));
    }),

    /** Perform {@link MOS6502ALU#ror(int)} on the accumulator: <code>A := ROR(A)</code> */
    ROR_ACC((env, mem, alu) -> {
        env.setA(alu.ror(env.getA()));
    }),

    /** Perform {@link MOS6502ALU#asl(int)} on the addressed memory value: <code>mem[address_bus] := ASL(mem[address_bus])</code> */
    ASL_MEM((env, mem, alu) -> {
        mem.store(alu.asl(mem.fetch()));
    }),

    /** Perform {@link MOS6502ALU#lsr(int)} on the addressed memory value: <code>mem[address_bus] := LSR(mem[address_bus])</code> */
    LSR_MEM((env, mem, alu) -> {
        mem.store(alu.lsr(mem.fetch()));
    }),

    /** Perform {@link MOS6502ALU#rol(int)} on the addressed memory value: <code>mem[address_bus] := ROL(mem[address_bus])</code> */
    ROL_MEM((env, mem, alu) -> {
        mem.store(alu.rol(mem.fetch()));
    }),

    /** Perform {@link MOS6502ALU#ror(int)} on the addressed memory value: <code>mem[address_bus] := ROR(mem[address_bus])</code> */
    ROR_MEM((env, mem, alu) -> {
        mem.store(alu.ror(mem.fetch()));
    }),

    /** Branch if N is clear: <code>if !N then pc := pc + offset</code> */
    BRANCH_IF_POSITIVE((env, mem, alu) -> {
        branch(env, mem, !env.getN());
    }),

    /** Branch if N is set: <code>if N then pc := pc + offset</code> */
    BRANCH_IF_NEGATIVE((env, mem, alu) -> {
        branch(env, mem, env.getN());
    }),

    /** Branch if V is clear: <code>if !V then pc := pc + offset</code> */
    BRANCH_IF_OVERFLOW_CLEAR((env, mem, alu) -> {
        branch(env, mem, !env.getV());
    }),

    /** Branch if V is set: <code>if V then pc := pc + offset</code> */
    BRANCH_IF_OVERFLOW_SET((env, mem, alu) -> {
        branch(env, mem, env.getV());
    }),

    /** Branch if carry is clear: <code>if !C then pc := pc + offset</code> */
    BRANCH_IF_CARRY_CLEAR((env, mem, alu) -> {
        branch(env, mem, !env.getCarry());
    }),

    /** Branch if carry is set: <code>if C then pc := pc + offset</code> */
    BRANCH_IF_CARRY_SET((env, mem, alu) -> {
        branch(env, mem, env.getCarry());
    }),

    /** Branch if Z is clear: <code>if !Z then pc := pc + offset</code> */
    BRANCH_IF_NOT_EQUAL((env, mem, alu) -> {
        branch(env, mem, !env.getZ());
    }),

    /** Branch if Z is set: <code>if Z then pc := pc + offset</code> */
    BRANCH_IF_EQUAL((env, mem, alu) -> {
        branch(env, mem, env.getZ());
    });

    /**
     * Shared PC-relative branch logic. The offset operand is fetched from the already-addressed
     * location (via {@code ADDRESS_PC} in the same tick). If not taken, this is the full cost (2 cycles
     * total including FETCH). If taken, an additional tick is chained to apply the low-byte offset
     * (3 cycles total); if that crosses a page boundary, a further tick is chained to correct the high
     * byte (4 cycles total) &mdash; mirroring the real 6502's extra page-cross penalty.
     */
    private static void branch(final MOS6502Environment env, final LatchedMemoryBus mem, final boolean take) {
        final byte offset = (byte) mem.fetch();

        if (!take) {
            return;
        }

        env.requestAdditionalOp((e, m, a) -> {
            final int oldPCH = e.getPCH();
            final int newLow = e.getPCL() + offset;
            e.setPCL(newLow & 0xFF);

            m.loadMemoryAddress(e.getPC()); //dummy read at (possibly not yet page-corrected) PC
            m.fetch();

            if (newLow < 0 || newLow > 0xFF) {
                final int correctedPCH = (oldPCH + (newLow > 0xFF ? 1 : -1)) & 0xFF;
                e.requestAdditionalOp((e2, m2, a2) -> {
                    e2.setPCH(correctedPCH);
                    m2.loadMemoryAddress(e2.getPC()); //dummy read at the corrected PC
                    m2.fetch();
                    e2.additionalTickCompleted();
                });
            } else {
                e.additionalTickCompleted();
            }
        });
    }

    private final MOS6502Operation op;

    @Override
    public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
        op.execute(environment, memory, alu);
    }

    MOS6502MicroOp(final MOS6502Operation op) {
        this.op = op;
    }
}
