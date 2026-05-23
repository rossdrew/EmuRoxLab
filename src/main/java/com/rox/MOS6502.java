package com.rox;

import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.MemoryBus;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static com.rox.MOS6502.MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private boolean fCarry = false;

    private Deque<MicroOp> opStack = new ArrayDeque<>();

    //XX Temporary environment structure
    class Environment {
        public boolean carry; //FLAG: Carry
        public boolean z; //FLAG: Zero
        public boolean n; //FLAG: Negative
        public boolean v; //FLAG: Signed Overflow

        private int pc; //program counter
        private int ir; //instruction register
        private int adl; //(1/2) address data low byte
        private int adh; //(2/2) address data high byte
        private int a; //accumulator

        public void setIR(final int value){
            this.ir = 0xFF & value;
        }

        public void setADL(final int value){
            this.adl = 0xFF & value;
        }

        public void setA(final int value){
            this.a = 0xFF & value;
        }

        public int getA() {
            return 0xFF & a;
        }

        /** Overflow safe PC + increment */
        public int pc(){
            int cached_pc = environment.pc;
            environment.pc = (environment.pc + 1) & 0xFFFF;
            return cached_pc;
        }
    }

    private Environment environment;
    private MOS6502ALU alu;

    @FunctionalInterface
    interface Operation {
        void execute(final Environment environment,
                     final LatchedMemoryBus memory,
                     final MOS6502ALU alu);
    }

    enum MicroOp implements Operation {
        /* Implicit fetch cycle.  IR = mem[pc] */
        IR_FROM_PC_ADDRESS((env, mem, alu) -> {
            mem.loadMemoryAddress(env.pc());
            env.setIR(mem.fetch());
        }),

        /* ADL = mem[pc] */
        Z_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {
            mem.loadMemoryAddress(env.pc());
            env.setADL(mem.fetch());
        }),

        /* A = alu.ADC(A, mem[adl]) */
        Z_ADDRESS_READ((env, mem, alu) -> {
            mem.loadMemoryAddress(env.adl);
            //XXX execute is done in the same step but that doesn't work with this microop name
            int result = alu.adc(env.getA(), mem.fetch());
            env.setA(result);
        }),

        OP((env, mem, alu) -> {}),
        LOW_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {}), //PC -> low address
        HIGH_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {}), //PC -> high address

        ADJUSTED_ADDRESS((env, mem, alu) -> {}), // read adjusted address
        PAGE_CROSS_CYCLE((env, mem, alu) -> {}); // optional extra page-cross cycle

        private final Operation op;

        @Override
        public void execute(Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
            op.execute(environment, memory, alu);
        }

        MicroOp(final Operation op) {
            this.op = op;
        }
    }

    public enum OpCode {
        ADC_Z (0x65, Arrays.asList(Z_ADDRESS_FROM_PC_ADDRESS, Z_ADDRESS_READ, OP));

        private final int id;
        private final List<MicroOp> microOperations;

        OpCode(final int id, final List<MicroOp> microOperations) {
            this.id = id;
            this.microOperations = microOperations;
        }

        public static OpCode of(final int id) {
            return ADC_Z;
        }
    }

    public MOS6502(final LatchedMemoryBus latchedMemory) {
        this.latchedMemory = latchedMemory;
        this.environment = new Environment();
        this.environment.pc = 0;
        this.alu = new MOS6502ALU(this.environment);

    }

    @Override
    public void tick() {
        if (opStack.isEmpty()){
            IR_FROM_PC_ADDRESS.execute(environment, latchedMemory, alu);
            //Load opcode into opstack
//            latchedMemory.loadMemoryAddress(environment.pc());
//            environment.ir = latchedMemory.fetch(); //read
            final OpCode opcode = OpCode.of(environment.ir); //decode
            opcode.microOperations.reversed().forEach(microop -> opStack.push(microop)); //schedule
        } else {
            //Execute opcode/opstack
            final MicroOp op = opStack.pop();

            op.execute(environment, latchedMemory, alu);
            //1. getmemory at pc, 2. increment pc

            System.out.println(op.name());
            //TODO execute microop
        }
    }
}
