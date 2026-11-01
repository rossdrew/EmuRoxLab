package com.rox.debug.breakpoints;

/**
 * What stopped the emulation.
 *
 * @param cause a breakpoint, or the kind of access a watchpoint caught
 * @param address the breakpoint's address, or the address accessed
 * @param value the byte read or written; 0 for a breakpoint
 * @param pc the instruction about to run (breakpoint), or the one that made the access (watchpoint)
 */
public record BreakHit(Cause cause, int address, int value, int pc) {
    public enum Cause {
        BREAKPOINT,
        READ,
        WRITE
    }
}
