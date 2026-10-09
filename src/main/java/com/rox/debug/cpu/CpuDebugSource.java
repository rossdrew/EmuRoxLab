package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

import java.util.List;

/**
 * What a CPU debug view reads and controls - implemented next to {@code NES} (which keeps its
 * components package-private), so the view itself never touches emulator internals. Reads may come
 * from a UI thread while the emulation runs, so they're best-effort live values; exact while paused.
 */
public interface CpuDebugSource {
    /** Registers and flags right now - see {@code MOS6502.liveState()}. */
    MOS6502Snapshot state();

    /** {@code count} instructions disassembled forward from the PC. */
    List<DisassembledInstruction> upcomingInstructions(int count);

    /** Recently executed instructions, oldest first - not including the one at the PC, which hasn't run yet. */
    List<DisassembledInstruction> recentInstructions();

    /** The clock rate actually being achieved - see {@code TickRateMonitor}. */
    double measuredHz();

    /** The clock rate real hardware runs at. */
    long intendedHz();

    /** @return false if there was nothing running to pause */
    boolean pause();

    void resume();

    boolean isPaused();
}
