package com.rox.debug.breakpoints;

import com.rox.debug.BeamPosition;

/**
 * What stopped the emulation.
 *
 * @param cause a breakpoint, or the kind of access a watchpoint caught
 * @param address the breakpoint's address, or the address accessed
 * @param value the byte read or written; 0 for a breakpoint
 * @param pc the instruction about to run (breakpoint), or the one that made the access (watchpoint)
 * @param beam where the PPU was drawing - at the access itself for a watchpoint (the start of that CPU cycle), at
 * the stop for a breakpoint
 */
public record BreakHit(Cause cause, int address, int value, int pc, BeamPosition beam) {
    /** A hit with no beam position recorded. */
    public BreakHit(final Cause cause, final int address, final int value, final int pc){
        this(cause, address, value, pc, BeamPosition.UNKNOWN);
    }

    public enum Cause {
        BREAKPOINT,
        READ,
        WRITE
    }
}
