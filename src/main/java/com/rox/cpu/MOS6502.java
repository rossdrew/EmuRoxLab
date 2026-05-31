package com.rox.cpu;

import com.rox.ClockWatcher;
import com.rox.mem.*;

import java.util.*;

import static com.rox.cpu.MOS6502MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private Deque<MOS6502Operation[]> opsInTicksStack = new ArrayDeque<>();
    private MOS6502Environment environment;
    private MOS6502ALU alu;

    @FunctionalInterface
    interface MOS6502Operation {
        void execute(final MOS6502Environment environment,
                     final LatchedMemoryBus memory,
                     final MOS6502ALU alu);
    }

    public MOS6502Environment getEnvironmentSnapshot(){
        return environment.clone();
    }

    public MOS6502(final LatchedMemoryBus latchedMemory) {
        this.latchedMemory = latchedMemory;
        this.environment = new MOS6502Environment();
        this.alu = new MOS6502ALU(this.environment);
    }

    @Override
    public void tick() {
        if (opsInTicksStack.isEmpty()){
            FETCH.execute(environment, latchedMemory, alu);
            final MOS6502OpCode opcode = MOS6502OpCode.of(environment.getIR()); //decode
            /*DEBUG*///System.out.println("TICK>(!) Fetched next opcode: " + opcode + "\t - " + environment);

            //Schedule: push (reversed) to stack
            final MOS6502Operation[][] operations = opcode.getOperations();
            for (int tick=operations.length-1; tick>=0; tick--){
                opsInTicksStack.push(operations[tick]);
            }
        } else {
            final MOS6502Operation[] opsThisTick = opsInTicksStack.pop();

            for (MOS6502Operation op : opsThisTick){
                op.execute(environment, latchedMemory, alu);
                /*DEBUG*///System.out.println("TICK> " + op + "\t - " + environment);
            }

            //Waste a clock tick if indicated
            if (environment.additionalTickPending()){
                opsInTicksStack.push(new MOS6502Operation[] { environment.getPendingOperation() });
            }
        }
    }
}
