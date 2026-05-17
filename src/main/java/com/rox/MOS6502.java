package com.rox;

import com.rox.mem.LatchedMemoryBus;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static com.rox.MOS6502.MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private int pc;

    private int rInstruction = 0;
    private int rAccumulator = 0;
    private boolean fCarry = false;

    private Deque<MicroOp> opStack = new ArrayDeque<>();

    enum MicroOp {
        IR_FROM_PC_ADDRESS, //IMPLICIT!! PC -> r_instruction

        Z_ADDRESS_FROM_PC_ADDRESS,
        Z_ADDRESS_READ,

        OP,
        LOW_ADDRESS_FROM_PC_ADDRESS, //PC -> low address
        HIGH_ADDRESS_FROM_PC_ADDRESS, //PC -> high address

        ADJUSTED_ADDRESS, // read adjusted address
        PAGE_CROSS_CYCLE // optional extra page-cross cycle
    }

    public enum OpCode {
        ADC_Z (0x65, Arrays.asList(Z_ADDRESS_FROM_PC_ADDRESS, Z_ADDRESS_READ, OP));

        private final int id;
        private final List<MicroOp> microOperations;

        OpCode(final int id, final List<MicroOp> microOperations) {
            this.id = id;
            this.microOperations = microOperations;
        }

        public int id(){
            return id;
        }

        public static OpCode of(final int id) {
            return ADC_Z;
        }
    }

    public MOS6502(final LatchedMemoryBus latchedMemory) {
        this.latchedMemory = latchedMemory;
        pc = 0;
    }

    /** Overflow safe PC + increment */
    private int pc(){
        int cached_pc = pc;
        pc = (pc + 1) & 0xFFFF;
        return cached_pc;
    }

    @Override
    public void tick() {
        if (opStack.isEmpty()){
            //Load opcode into opstack
            latchedMemory.loadMemoryAddress(pc());
            rInstruction = latchedMemory.fetch(); //read
            final OpCode opcode = OpCode.of(rInstruction); //decode
            opcode.microOperations.reversed().forEach(microop -> opStack.push(microop)); //schedule
        } else {
            //Execute opcode/opstack
            final MicroOp op = opStack.pop();

            //1. getmemory at pc, 2. increment pc

            System.out.println(op.name());
            //TODO execute microop
        }
    }
}
