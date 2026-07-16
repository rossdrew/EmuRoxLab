package com.rox.cpu.mos6502;

import com.rox.clock.ClockWatcher;
import com.rox.mem.*;

import java.util.*;

import static com.rox.cpu.mos6502.MOS6502MicroOp.*;

public class MOS6502 implements ClockWatcher {
    /** 7-cycle hardware interrupt sequence: 2 dummy PC reads, push PCH/PCL/status (B=0), set I, jump to vector */
    private static final MOS6502Operation[][] IRQ_SEQUENCE = {
            { ADDRESS_CURRENT_PC, DUMMY_READ },
            { ADDRESS_CURRENT_PC, DUMMY_READ },
            { PUSH_PCH },
            { PUSH_PCL },
            { PUSH_PROCESSOR_STATUS_WITHOUT_BREAK, INTERRUPT },
            { ADDRESS_IV_LOW, PCL_FROM_MEM },
            { ADDRESS_IV_HIGH, PCH_FROM_MEM }
    };

    private static final MOS6502Operation[][] NMI_SEQUENCE = {
            { ADDRESS_CURRENT_PC, DUMMY_READ },
            { ADDRESS_CURRENT_PC, DUMMY_READ },
            { PUSH_PCH },
            { PUSH_PCL },
            { PUSH_PROCESSOR_STATUS_WITHOUT_BREAK, INTERRUPT },
            { ADDRESS_NMI_LOW, PCL_FROM_MEM },
            { ADDRESS_NMI_HIGH, PCH_FROM_MEM }
    };

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

    /** Set the level-sensitive hardware IRQ line, as a device such as the APU would */
    public void setIRQLine(boolean asserted){
        environment.setIRQLine(asserted);
    }

    /** Signal a non-maskable interrupt, latched until serviced */
    public void signalNMI(){
        environment.signalNMI();
    }

    @Override
    public void tick() {
        if (opsInTicksStack.isEmpty()){
            if (environment.hasPendingInterrupt()){
                // Mirrors fetchNextOp(): the cycle that schedules the sequence also performs its first tick's work
                stackMicroOperations(environment.consumeNMI() ? NMI_SEQUENCE : IRQ_SEQUENCE);
                executeNextInstructionInStack();
                performAdditionalRequestedMicroOp();
            } else {
                stackMicroOperations(fetchNextOp());
            }
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
        stackMicroOperations(opcode.getOperations());
    }

    /** Schedule the given per-tick operations in reverse order onto the stack */
    private void stackMicroOperations(MOS6502Operation[][] operations) {
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
