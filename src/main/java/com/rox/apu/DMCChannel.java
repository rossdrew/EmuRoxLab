package com.rox.apu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * NES delta modulation channel (DMC, $4010-$4013). Plays back 1-bit delta-encoded PCM samples
 * fetched via DMA from CPU-addressable memory, driving a 7-bit output/delta counter that the
 * {@link Mixer} reads directly as this channel's level (0-127) - unlike the other four channels,
 * DMC has no envelope, sweep, or length counter, and isn't clocked by the {@link FrameSequencer}
 * at all; its only rhythm is its own timer.
 *
 * <p>The timer ({@link ParityConstrainedCountdownTicker}, same "once per APU cycle" gating as
 * pulse/noise) drives an 8-bit shift register that empties one bit at a time, LSB first: a 1 bit
 * nudges the delta counter up by 2 (clamped at 127), a 0 bit nudges it down by 2 (clamped at 0).
 * Every 8 shifts, the shift register is refilled by a memory fetch, provided sample bytes remain -
 * if none remain, the output freezes at its last delta value (real DMC behaviour, not a
 * simplification) until the channel is restarted.
 *
 * <p>Sample playback parameters: start address = $C000 + ($4012 * 64), length = ($4013 * 16) + 1
 * bytes, fetch address wraps $FFFF back to $8000. On exhausting the sample, either the loop flag
 * ($4010 bit 6) restarts playback from the original address/length, or - if the loop flag is clear
 * and the IRQ-enable flag ($4010 bit 7) is set - an IRQ becomes pending.
 *
 * <p>Simplifications (Phase 6):
 * <ul>
 *     <li>CPU cycle-stealing/stall on each DMA sample fetch is not modeled - real hardware stalls
 *     the CPU 1-4 cycles per fetch; no stall mechanism exists in this codebase's Clock/ClockWatcher
 *     model yet. Tracked as a separate, deferred follow-up.</li>
 *     <li>$4015 enable/status semantics (channel-enable bit, DMC-IRQ status bit surfaced on a
 *     $4015 read) aren't wired yet - that's a later phase. {@link #start()} is a direct stand-in
 *     for the real $4015-bit-4 enable path so this channel can be driven/tested without it.</li>
 *     <li>Real hardware's memory-reader and output unit are two independent state machines linked
 *     only by the one-byte sample buffer (the reader can prefetch ahead of the output unit
 *     finishing its current byte); here they're combined into one synchronous fetch-on-refill path
 *     - functionally equivalent for audio output, but not a basis for cycle-accurate CPU stalling.</li>
 *     <li>{@link #isIrqPending()} is exposed but not wired to the CPU's IRQ line yet - deferred
 *     alongside the frame sequencer's own (also still-unwired) IRQ, since both need combining once
 *     $4015 exists.</li>
 * </ul>
 */
public class DMCChannel implements ClockWatcher {
    /** NTSC DMC rates (in APU cycles), selected by the low nibble of $4010. */
    static final int[] NTSC_DMC_RATES = {
            428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
    };

    private static final int IRQ_ENABLE_BIT = 0x80;
    private static final int LOOP_BIT = 0x40;
    private static final int RATE_INDEX_MASK = 0x0F;

    private static final int DIRECT_LOAD_MASK = 0x7F;

    private static final int SAMPLE_ADDRESS_BASE = 0xC000;
    private static final int SAMPLE_ADDRESS_MULTIPLIER = 64;

    private static final int SAMPLE_LENGTH_MULTIPLIER = 16;
    private static final int SAMPLE_LENGTH_OFFSET = 1;

    private static final int MAX_ADDRESS = 0xFFFF;
    private static final int ADDRESS_WRAP_TARGET = 0x8000;

    private static final int DELTA_MIN = 0;
    private static final int DELTA_MAX = 127;
    private static final int DELTA_STEP = 2;

    private static final int SHIFT_REGISTER_BITS = 8;

    private final MemoryBus memoryBus;
    private final ParityConstrainedCountdownTicker runner;

    private boolean irqEnabled;
    private boolean loop;
    private boolean irqPending;

    private int sampleStartAddress;
    private int sampleLength;

    private int currentAddress;
    private int bytesRemaining;

    private int shiftRegister;
    private int bitsRemainingInShiftRegister;
    private boolean outputSilenced = true;

    private int deltaCounter;

    public DMCChannel(final MemoryBus memoryBus){
        this.memoryBus = memoryBus;
        this.runner = new ParityConstrainedCountdownTicker(this::clockOutputUnit, false, NTSC_DMC_RATES[0]);
    }

    /**
     * Handle a $4010 write.
     *
     * Register: IL-- RRRR
     * I (bit 7): IRQ enable
     * L (bit 6): loop flag
     * R (bits 0-3): index into {@link #NTSC_DMC_RATES}
     */
    public void writeControlRegister(final int value){
        irqEnabled = (value & IRQ_ENABLE_BIT) != 0;
        loop = (value & LOOP_BIT) != 0;
        if (!irqEnabled){
            irqPending = false;
        }
        runner.setCounterPeriod(NTSC_DMC_RATES[value & RATE_INDEX_MASK]);
    }

    /** Register: -DDD DDDD - direct 7-bit load of the delta counter ($4011), bypassing the shift/delta logic. */
    public void writeDirectLoad(final int value){
        deltaCounter = value & DIRECT_LOAD_MASK;
    }

    /** Register: AAAA AAAA - sample start address = $C000 + value*64 ($4012). */
    public void writeSampleAddress(final int value){
        sampleStartAddress = SAMPLE_ADDRESS_BASE + value * SAMPLE_ADDRESS_MULTIPLIER;
    }

    /** Register: LLLL LLLL - sample length in bytes = value*16 + 1 ($4013). */
    public void writeSampleLength(final int value){
        sampleLength = value * SAMPLE_LENGTH_MULTIPLIER + SAMPLE_LENGTH_OFFSET;
    }

    /**
     * Starts (or restarts) sample playback: reloads the current address and bytes-remaining
     * counter from the last-written $4012/$4013 values, then immediately primes the shift register
     * (fetching the first sample byte if one is available). Stand-in for the real $4015 bit 4
     * enable path - see the class Javadoc's simplifications.
     */
    public void start(){
        currentAddress = sampleStartAddress;
        bytesRemaining = sampleLength;
        reloadShiftRegister();
    }

    /** CPU-cycle clock: the timer runs at half this rate (once per APU cycle), same as pulse/noise. */
    @Override
    public void tick(){
        runner.tick();
    }

    private void clockOutputUnit(){
        if (!outputSilenced){
            if ((shiftRegister & 1) != 0){
                deltaCounter = Math.min(DELTA_MAX, deltaCounter + DELTA_STEP);
            } else {
                deltaCounter = Math.max(DELTA_MIN, deltaCounter - DELTA_STEP);
            }
        }
        shiftRegister >>= 1;
        bitsRemainingInShiftRegister--;
        if (bitsRemainingInShiftRegister == 0){
            reloadShiftRegister();
        }
    }

    /**
     * Refills the 8-bit shift register for the next 8 output clocks. On real hardware this is fed
     * by an independent one-byte sample buffer that can be prefetched ahead of the output unit
     * needing it; here the fetch happens synchronously in the same call (see the class Javadoc's
     * simplifications), so there's no separate buffer state to model.
     */
    private void reloadShiftRegister(){
        bitsRemainingInShiftRegister = SHIFT_REGISTER_BITS;
        if (bytesRemaining == 0){
            outputSilenced = true;
            return;
        }
        shiftRegister = memoryBus.read(currentAddress);
        outputSilenced = false;
        currentAddress = currentAddress == MAX_ADDRESS ? ADDRESS_WRAP_TARGET : currentAddress + 1;
        bytesRemaining--;
        if (bytesRemaining == 0){
            if (loop){
                currentAddress = sampleStartAddress;
                bytesRemaining = sampleLength;
            } else if (irqEnabled){
                irqPending = true;
            }
        }
    }

    /** Current output: the 7-bit delta counter (0-127), fed directly into {@link Mixer#mix}. */
    public int outputSample(){
        return deltaCounter;
    }

    public boolean isIrqPending(){
        return irqPending;
    }

    public void clearIrq(){
        irqPending = false;
    }

    int sampleStartAddress(){
        return sampleStartAddress;
    }

    int sampleLength(){
        return sampleLength;
    }

    int currentAddress(){
        return currentAddress;
    }

    int bytesRemaining(){
        return bytesRemaining;
    }

    int currentDeltaCounter(){
        return deltaCounter;
    }

    int currentShiftRegister(){
        return shiftRegister;
    }

    int bitsRemainingInShiftRegister(){
        return bitsRemainingInShiftRegister;
    }

    boolean isOutputSilenced(){
        return outputSilenced;
    }

    int currentTimerPeriod(){
        return runner.getCounterPeriod();
    }

    boolean isLoopFlagSet(){
        return loop;
    }
}
