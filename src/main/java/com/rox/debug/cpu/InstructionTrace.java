package com.rox.debug.cpu;

import com.rox.clock.ClockWatcher;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.cpu.mos6502.assembler.Disassembler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * A rolling history of where the CPU has been: after every tick, if the CPU is between instructions,
 * the address of the instruction it's about to start - and its bytes, read there and then - are
 * recorded into a fixed-size ring buffer. This is the "how did we get here" a forward-only disassembler
 * can't give (6502 code can't be decoded backwards). The newest entry is therefore the current PC
 * whenever the CPU is between instructions.
 *
 * <p>The bytes are kept, rather than re-read when displayed, because the same address can hold
 * different code later: a mapper bank switch (common - MMC1/MMC3 games swap PRG-ROM banks constantly)
 * or self-modifying code in RAM would otherwise show history as instructions that never ran.
 *
 * <p>Must be ticked <em>after</em> anything that can start a DMA stall on the same tick, so a stall
 * holds recording back until it ends rather than the same address being seen twice. {@link #tick()}
 * runs on the clock thread; {@link #recent()} may be read from another thread for display, so a live
 * read is best-effort - exact while the clock is paused.
 */
public final class InstructionTrace implements ClockWatcher {
    public static final int DEFAULT_CAPACITY = 32;
    /** The longest 6502 instruction: opcode plus a 2-byte operand. */
    private static final int MAX_INSTRUCTION_BYTES = 3;
    private static final int ADDRESS_MASK = 0xFFFF;
    private static final int BYTE_MASK = 0xFF;
    private static final int BITS_PER_BYTE = 8;

    private final BooleanSupplier atInstructionBoundary;
    private final IntSupplier programCounter;
    private final IntUnaryOperator peekByte;
    private final int[] addresses;
    private final int[] packedBytes; //up to MAX_INSTRUCTION_BYTES per entry, first byte lowest
    private int nextSlot;
    private int filled;

    /**
     * @param atInstructionBoundary e.g. {@code cpu::isAtInstructionBoundary}
     * @param programCounter e.g. {@code cpu::programCounter}
     * @param peekByte reads a byte the CPU could fetch, without side effects - called on the clock thread
     */
    public InstructionTrace(final BooleanSupplier atInstructionBoundary, final IntSupplier programCounter,
                            final IntUnaryOperator peekByte){
        this(atInstructionBoundary, programCounter, peekByte, DEFAULT_CAPACITY);
    }

    public InstructionTrace(final BooleanSupplier atInstructionBoundary, final IntSupplier programCounter,
                            final IntUnaryOperator peekByte, final int capacity){
        this.atInstructionBoundary = atInstructionBoundary;
        this.programCounter = programCounter;
        this.peekByte = peekByte;
        this.addresses = new int[capacity];
        this.packedBytes = new int[capacity];
    }

    @Override
    public void tick(){
        if (atInstructionBoundary.getAsBoolean()){
            final int address = programCounter.getAsInt();
            int bytes = 0;
            for (int i = 0; i < MAX_INSTRUCTION_BYTES; i++){
                bytes |= (peekByte.applyAsInt((address + i) & ADDRESS_MASK) & BYTE_MASK) << (i * BITS_PER_BYTE);
            }
            addresses[nextSlot] = address;
            packedBytes[nextSlot] = bytes;
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
        final int oldest = oldestSlot(count);
        for (int i = 0; i < count; i++){
            result[i] = addresses[(oldest + i) % addresses.length];
        }
        return result;
    }

    /** The recorded instructions, oldest first, decoded from the bytes they had when they ran. */
    public List<DisassembledInstruction> recentInstructions(){
        final int count = filled;
        final List<DisassembledInstruction> result = new ArrayList<>(count);
        final int oldest = oldestSlot(count);
        for (int i = 0; i < count; i++){
            final int slot = (oldest + i) % addresses.length;
            final int address = addresses[slot];
            final int bytes = packedBytes[slot];
            result.add(Disassembler.disassembleOne(
                    peekAddress -> (bytes >> (((peekAddress - address) & ADDRESS_MASK) * BITS_PER_BYTE)) & BYTE_MASK,
                    address));
        }
        return result;
    }

    private int oldestSlot(final int count){
        return (nextSlot - count + addresses.length) % addresses.length;
    }
}
