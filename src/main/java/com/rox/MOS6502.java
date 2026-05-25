package com.rox;

import com.rox.mem.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.rox.MOS6502.MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private boolean fCarry = false;

    private Deque<Operation> opStack = new ArrayDeque<>();

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

        @Override
        public String toString() {
            return "pc:"+pc+", ir:"+ir+", ad["+adh+":"+adl+"], a:"+a+" | F[c:"+carry+", z:"+z+", n:"+n+", v:"+v+"]";
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
        Z_ADDRESS_READ((env, mem, alu) -> {
            mem.loadMemoryAddress(env.adl & 0xFF);
        }),

        /* A = adc(mem[addr]) */
        ADC((env, mem, alu) -> {
            alu.adc(env.getA(), mem.fetch());
        }),

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
        ADC_Z (0x65, Arrays.asList(Z_ADDRESS_FROM_PC_ADDRESS, Z_ADDRESS_READ, ADC)),
        ADC_I (0x69, Arrays.asList(Z_ADDRESS_READ, ADC));

        private final int id;
        private final List<Operation> ops;

        private static final Map<Integer, OpCode> BY_ID =
                Arrays.stream(values())
                        .collect(Collectors.toMap(op -> op.id, op -> op));

        OpCode(final int id, final List<Operation> ops) {
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
            FETCH.execute(environment, latchedMemory, alu);
            final OpCode opcode = OpCode.of(environment.ir); //decode
            System.out.println("(!) Fetched next opcode: " + opcode + "\t - " + environment);
            opcode.ops.reversed().forEach(microop -> opStack.push(microop)); //schedule
        } else {
            final Operation op = opStack.pop();
            op.execute(environment, latchedMemory, alu);
            System.out.println(op + "\t - " + environment);
        }
    }

    static void main(String[] args){
        final Memory ram = new RAM(1024);
        ram.write(0x00, OpCode.ADC_Z.id);
        ram.write(0x01, 4);
        ram.write(0x02, OpCode.ADC_I.id);
        ram.write(0x03, 8);
        final MemoryBus subMemoryBus = new MemoryBus8Bit(ram);
        final LatchedMemoryBus memoryBus = new Latched8BitMemoryBus(subMemoryBus);;
        final MOS6502 cpu = new MOS6502(memoryBus);
        System.out.println("Starting >>>>");
        cpu.tick();
        cpu.tick();
        cpu.tick();  //The actual ADC is costing one byte!!!

        cpu.tick();
        cpu.tick();
        try {
            cpu.tick(); //0x0 is an unknown upcode
            System.out.println("FAILURE!!");
        }catch (RuntimeException e){

        }
        System.out.println(">>>> Ending!");
    }
}
