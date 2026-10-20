package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

import java.util.List;
import java.util.Optional;

/**
 * What a CPU debug view reads and controls - implemented next to {@code NES} (which keeps its
 * components package-private), so the view itself never touches emulator internals. Reads may come
 * from a UI thread while the emulation runs, so they're best-effort live values; exact while paused.
 */
public interface CpuDebugSource {
    /** Registers and flags right now - see {@code MOS6502.liveState()}. */
    MOS6502Snapshot state();

    /** {@code count} instructions disassembled forward from the current one (about to start, or part-way through). */
    List<DisassembledInstruction> upcomingInstructions(int count);

    /** Recently executed instructions with the state each started with, oldest first - not including the current one. */
    List<TracedInstruction> recentInstructions();

    /** The state the current instruction started with, if it's been recorded yet. */
    Optional<MOS6502Snapshot> currentInstructionState();

    /** How many instructions have started so far - goes up by one per instruction step. */
    long instructionCount();

    /** The clock rate actually being achieved - see {@code TickRateMonitor}. */
    double measuredHz();

    /** The clock rate real hardware runs at. */
    long intendedHz();

    /** The emulation speed multiplier, 1 being real time - see {@code NES.setSpeed(double)}. */
    double speed();

    /** @throws IllegalArgumentException outside {@code NES.MIN_SPEED}-{@code NES.MAX_SPEED} */
    void setSpeed(double multiplier);

    /** @return false if there was nothing running to pause */
    boolean pause();

    void resume();

    boolean isPaused();

    /** Runs one instruction - only while paused. See {@code NES.stepInstruction()}. */
    void stepInstruction();

    /** Runs exactly {@code cycles} CPU cycles - only while paused. See {@code NES.stepCycles(int)}. */
    void stepCycles(int cycles);

    /** Runs until the next frame is finished - only while paused. See {@code NES.stepFrame()}. */
    void stepFrame();
}
