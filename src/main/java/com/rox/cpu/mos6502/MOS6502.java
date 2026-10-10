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

    private static final int RESET_VECTOR_LOW_ADDRESS = 0xFFFC;
    private static final int RESET_VECTOR_HIGH_ADDRESS = 0xFFFD;

    private final LatchedMemoryBus latchedMemory;

    private Deque<MOS6502Operation[]> opsInTicksStack = new ArrayDeque<>();
    private MOS6502Environment environment;
    private MOS6502ALU alu;
    private int stallCycles;

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

    /** The current program counter - cheap enough to read every instruction, unlike {@link #getEnvironmentSnapshot()}'s copy. */
    public int programCounter(){
        return environment.getPC();
    }

    /** Set the program counter, e.g. to point at the start of an assembled program */
    public void setPC(final int newPC){
        environment.setPC(newPC);
    }

    /**
     * Reset: read the reset vector ($FFFC/$FFFD) and jump the program counter there, as real
     * hardware does on power-on/reset - mirrors the IRQ ($FFFE/$FFFF) / NMI ($FFFA/$FFFB)
     * vector-read pattern already used by the interrupt sequences above.
     */
    public void reset(){
        latchedMemory.loadMemoryAddress(RESET_VECTOR_LOW_ADDRESS);
        final int low = latchedMemory.fetch();
        latchedMemory.loadMemoryAddress(RESET_VECTOR_HIGH_ADDRESS);
        final int high = latchedMemory.fetch();
        setPC((high << 8) | low);
    }

    /**
     * Stall the CPU for the given number of cycles (e.g. OAM DMA's 513/514-cycle pause) - real
     * hardware freezes the CPU mid-instruction rather than letting it run ahead, so this must take
     * effect on the very next {@link #tick()}, not just after the current instruction finishes.
     */
    public void stall(final int cycles){
        stallCycles += cycles;
    }

    /**
     * True when the last {@link #tick()} completed an instruction (or interrupt sequence) and no DMA
     * stall is outstanding, so the next tick begins a fresh fetch or interrupt entry - the only point
     * at which the CPU's state is fully described by {@link #getEnvironmentSnapshot()}. Variable-cycle
     * extra ticks (page crosses, taken branches) are pushed onto {@code opsInTicksStack} in the same
     * tick that requests them, so an empty stack already accounts for them.
     */
    public boolean isAtInstructionBoundary(){
        return opsInTicksStack.isEmpty() && stallCycles == 0;
    }

    /**
     * Captures the CPU's complete state - only meaningful at an instruction boundary, where nothing is
     * left part-way through {@code opsInTicksStack} or a DMA stall to be lost.
     *
     * @throws IllegalStateException if not {@link #isAtInstructionBoundary()}
     */
    public MOS6502Snapshot snapshot(){
        if (!isAtInstructionBoundary()){
            throw new IllegalStateException("CPU state can only be captured at an instruction boundary");
        }
        return liveState();
    }

    /**
     * The CPU's registers and flags right now, at any point - including part-way through an
     * instruction, when they may be half-updated and nothing in flight is captured. For display only;
     * use {@link #snapshot()} for anything that will be {@link #restore}d.
     */
    public MOS6502Snapshot liveState(){
        return new MOS6502Snapshot(environment.getPC(), environment.getA(), environment.getX(), environment.getY(),
                environment.getStackPointer(), environment.getIR(), environment.getADL(), environment.getADH(),
                environment.negative, environment.signedOverflow, environment.breakFlag, environment.d,
                environment.i, environment.zero, environment.carry,
                environment.isIRQLineAsserted(), environment.isNMIPending());
    }

    /** Puts the CPU back exactly as {@code snapshot} captured it, abandoning anything part-way through. */
    public void restore(final MOS6502Snapshot snapshot){
        final MOS6502Environment restored = new MOS6502Environment(snapshot.carry(), snapshot.zero(), snapshot.negative(),
                snapshot.signedOverflow(), snapshot.breakFlag(), snapshot.pc(), snapshot.ir(), snapshot.adl(), snapshot.adh(),
                snapshot.a(), snapshot.x(), snapshot.y());
        restored.d = snapshot.decimal();
        restored.i = snapshot.interruptDisable();
        restored.setStackPointer(snapshot.stackPointer());
        restored.setIRQLine(snapshot.irqLineAsserted());
        if (snapshot.nmiPending()){
            restored.signalNMI();
        }
        environment = restored;
        alu = new MOS6502ALU(environment); //the ALU reads/writes flags through its own environment reference
        opsInTicksStack.clear();
        stallCycles = 0;
    }

    @Override
    public void tick() {
        if (stallCycles > 0){
            stallCycles--;
            return;
        }
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
