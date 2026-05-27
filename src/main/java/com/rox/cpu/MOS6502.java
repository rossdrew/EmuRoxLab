package com.rox.cpu;

import com.rox.ClockWatcher;
import com.rox.mem.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.rox.cpu.MOS6502.MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private boolean fCarry = false;

    private Deque<Operation[]> opStack = new ArrayDeque<>();

    private MOS6502Environment environment;
    private MOS6502ALU alu;

    public MOS6502Environment getEnvironmentSnapshot(){
        return environment.clone();
    }

    @FunctionalInterface
    interface Operation {
        void execute(final MOS6502Environment environment,
                     final LatchedMemoryBus memory,
                     final MOS6502ALU alu);
    }

    enum MicroOp implements Operation {
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
        VALUE_FROM_PC_ADDRESS((env, mem, alu)->{
            mem.loadMemoryAddress(env.pc());
        }),

        LOW_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {}), //PC -> low address
        HIGH_ADDRESS_FROM_PC_ADDRESS((env, mem, alu) -> {}), //PC -> high address

        ADJUSTED_ADDRESS((env, mem, alu) -> {}), // read adjusted address
        PAGE_CROSS_CYCLE((env, mem, alu) -> {}); // optional extra page-cross cycle

        private final Operation op;

        @Override
        public void execute(MOS6502Environment environment, LatchedMemoryBus memory, MOS6502ALU alu) {
            op.execute(environment, memory, alu);
        }

        MicroOp(final Operation op) {
            this.op = op;
        }
    }

    static Operation[] opsInTick(final Operation... ops) {
        return ops;
    }

    static Operation[][] clockTick(final Operation[]... ticks) {
        return ticks;
    }

    public enum OpCode {
        ADC_Z (0x65, clockTick(
                opsInTick(Z_ADDRESS_FROM_PC_ADDRESS),
                opsInTick(Z_ADDRESS, ADC))
        ),

        ADC_I (0x69, clockTick(
                opsInTick(VALUE_FROM_PC_ADDRESS, ADC))
        );

        private final int id;
        private final Operation[][] ops;

        private static final Map<Integer, OpCode> BY_ID =
                Arrays.stream(values())
                        .collect(Collectors.toMap(op -> op.id, op -> op));

        OpCode(final int id, final Operation[][] ops) {
            this.id = id;
            this.ops = ops;
        }

        public static OpCode of(final int id) {
            final OpCode opCode = BY_ID.get(id);

            if (opCode == null) {
                throw new IllegalArgumentException(String.format("Unknown opcode: 0x%02X", id));
            }

            return opCode;
        }

        public Integer getId() {
            return id;
        }
    }

    public MOS6502(final LatchedMemoryBus latchedMemory) {
        this.latchedMemory = latchedMemory;
        this.environment = new MOS6502Environment();
        this.alu = new MOS6502ALU(this.environment);
    }

    @Override
    public void tick() {
        if (opStack.isEmpty()){
            FETCH.execute(environment, latchedMemory, alu);
            final OpCode opcode = OpCode.of(environment.getIR()); //decode
            /*DEBUG*///System.out.println("TICK>(!) Fetched next opcode: " + opcode + "\t - " + environment);

            //Schedule: push (reversed) to stack
            for (int tick=opcode.ops.length-1; tick>=0; tick--){
                opStack.push(opcode.ops[tick]);
            }
        } else {
            final Operation[] op = opStack.pop();
            for (int i=0; i<op.length; i++){
                op[i].execute(environment, latchedMemory, alu);
                /*DEBUG*///System.out.println("TICK> " + op[i] + "\t - " + environment);
            }
        }
    }
}
