package com.rox.debug.cpu;

import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Whether to flash the current instruction's address: for {@link #FLASH_MILLIS} after a step lands on it by a
 * jump - any way control moves other than to the next instruction (JMP, JSR, RTS/RTI, a taken branch, an
 * interrupt) - while it's still the current instruction. Only while paused: running, nearly every refresh
 * follows some jump, so it would never stop.
 */
public final class JumpFlash {
    static final long FLASH_MILLIS = 600;
    private static final int ADDRESS_MASK = 0xFFFF;

    private final LongSupplier clockMillis;
    private long lastInstructionCount;
    private boolean pausedLastTime;
    private long flashUntil = Long.MIN_VALUE;

    /** @param clockMillis e.g. {@code System::currentTimeMillis} */
    public JumpFlash(final LongSupplier clockMillis){
        this.clockMillis = clockMillis;
    }

    /**
     * @param instructionCount see {@link CpuDebugSource#instructionCount()} - a rise while paused is a step
     * @param recent recently executed instructions, oldest first - see {@link CpuDebugSource#recentInstructions()}
     * @param currentAddress where the current instruction starts
     * @return whether the address should be flashing now
     */
    public boolean update(final long instructionCount, final boolean paused, final List<DisassembledInstruction> recent,
                          final int currentAddress){
        final long now = clockMillis.getAsLong();
        if (!paused){
            flashUntil = Long.MIN_VALUE;
            pausedLastTime = false;
            lastInstructionCount = instructionCount;
            return false;
        }
        //an instruction run while already paused is a step; the first look after pausing is not
        if (pausedLastTime && instructionCount != lastInstructionCount){
            //a step that didn't jump ends any flash still showing for the instruction before it
            flashUntil = landedByJump(recent, currentAddress) ? now + FLASH_MILLIS : Long.MIN_VALUE;
        }
        pausedLastTime = true;
        lastInstructionCount = instructionCount;
        return now < flashUntil;
    }

    /** True unless {@code currentAddress} directly follows the last executed instruction. */
    static boolean landedByJump(final List<DisassembledInstruction> recent, final int currentAddress){
        if (recent.isEmpty()){
            return false;
        }
        final DisassembledInstruction previous = recent.get(recent.size() - 1);
        return ((previous.address() + previous.length()) & ADDRESS_MASK) != currentAddress;
    }
}
