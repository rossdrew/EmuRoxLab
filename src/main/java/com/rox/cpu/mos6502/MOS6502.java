package com.rox.cpu.mos6502;

import com.rox.clock.ClockWatcher;
import com.rox.mem.*;

import java.util.*;

import static com.rox.cpu.mos6502.MOS6502MicroOp.*;

public class MOS6502 implements ClockWatcher {
    private final LatchedMemoryBus latchedMemory;

    private Deque<MOS6502Operation[]> opsInTicksStack = new ArrayDeque<>();
    private MOS6502Environment environment;
    private MOS6502ALU alu;

    public MOS6502Environment getEnvironmentSnapshot(){
        return environment.clone();
    }

    public MOS6502(final LatchedMemoryBus latchedMemory){
        this(latchedMemory, new MOS6502Environment());
    }

    public MOS6502(final LatchedMemoryBus latchedMemory, final MOS6502Environment environment) {
        this.latchedMemory = latchedMemory;
        this.environment = environment;
        this.alu = new MOS6502ALU(this.environment);
    }

    @Override
    public void tick() {
        if (opsInTicksStack.isEmpty()){
            stackMicroOperations(fetchNextOp());
        } else {
            executeNextInstructionInStack();
            performAdditionalRequestedMicroOp();
        }
    }

    private MOS6502OpCode fetchNextOp() {
        FETCH.execute(environment, latchedMemory, alu);
        /*DEBUG*///System.out.println("TICK>(!) Fetched next opcode: " + opcode + "\t - " + environment);
        return MOS6502OpCode.from(environment.getIR()); //decode
    }

    /** Schedule micro operatiosn of opcdoe in reverse defined order in stack */
    private void stackMicroOperations(MOS6502OpCode opcode) {
        final MOS6502Operation[][] operations = opcode.getOperations();
        for (int tick=operations.length-1; tick>=0; tick--){
            opsInTicksStack.push(operations[tick]);
        }
    }

    private void executeNextInstructionInStack() {
        final MOS6502Operation[] opsThisTick = opsInTicksStack.pop();

        for (MOS6502Operation op : opsThisTick){
            op.execute(environment, latchedMemory, alu);
            /*DEBUG*///System.out.println("TICK> " + op + "\t - " + environment);
        }
    }

    /** Execute additional requested micro operations, for use in page crosses for example */
    private void performAdditionalRequestedMicroOp() {
        if (environment.additionalTickPending()){
            opsInTicksStack.push(new MOS6502Operation[] { environment.getPendingOperation() });
        }
    }
}
