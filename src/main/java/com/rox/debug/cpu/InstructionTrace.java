package com.rox.debug.cpu;

import com.rox.clock.ClockWatcher;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * A rolling history of where the CPU has been: after every tick, if the CPU is between instructions,
 * the address of the instruction it's about to start is recorded into a fixed-size ring buffer. This is
 * the "how did we get here" a forward-only disassembler can't give (6502 code can't be decoded
 * backwards). The newest entry is therefore the current PC whenever the CPU is between instructions.
 *
 * <p>Must be ticked <em>after</em> anything that can start a DMA stall on the same tick, so a stall
 * holds recording back until it ends rather than the same address being seen twice. {@link #tick()}
 * runs on the clock thread; {@link #recent()} may be read from another thread for display, so a live
 * read is best-effort - exact while the clock is paused.
 */
public final class InstructionTrace implements ClockWatcher {
    public static final int DEFAULT_CAPACITY = 32;

    private final BooleanSupplier atInstructionBoundary;
    private final IntSupplier programCounter;
    private final int[] addresses;
    private int nextSlot;
    private int filled;

    /**
     * @param atInstructionBoundary e.g. {@code cpu::isAtInstructionBoundary}
     * @param programCounter e.g. {@code cpu::programCounter}
     */
    public InstructionTrace(final BooleanSupplier atInstructionBoundary, final IntSupplier programCounter){
        this(atInstructionBoundary, programCounter, DEFAULT_CAPACITY);
    }

    public InstructionTrace(final BooleanSupplier atInstructionBoundary, final IntSupplier programCounter, final int capacity){
        this.atInstructionBoundary = atInstructionBoundary;
        this.programCounter = programCounter;
        this.addresses = new int[capacity];
    }

    @Override
    public void tick(){
        if (atInstructionBoundary.getAsBoolean()){
            addresses[nextSlot] = programCounter.getAsInt();
            nextSlot = (nextSlot + 1) % addresses.length;
            if (filled < addresses.length){
                filled++;
            }
        }
    }

    /** The recorded instruction addresses, oldest first - at most the buffer's capacity. */
    public int[] recent(){
        final int count = filled;
        final int[] result = new int[count];
        final int oldest = (nextSlot - count + addresses.length) % addresses.length;
        for (int i = 0; i < count; i++){
            result[i] = addresses[(oldest + i) % addresses.length];
        }
        return result;
    }
}
