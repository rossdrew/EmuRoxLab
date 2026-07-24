package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES triangle channel ($4008 / $400A / $400B). Composes a {@link LinearCounter} and
 * {@link LengthCounter} with an 11-bit timer and a 32-step sequencer that produces a stepped
 * triangle waveform.
 *
 * Unlike the pulse/noise timers, the triangle's timer is clocked every CPU cycle rather than every
 * APU cycle (no parity gating) - this is what gives the triangle its doubled effective frequency
 * range relative to pulse/noise for the same timer period.
 *
 * The sequencer only advances while both the length counter and linear counter are nonzero; the
 * output is always whatever the current sequence step holds regardless - real hardware doesn't
 * silence the channel outright when either counter hits 0, it just freezes the waveform at
 * whichever step it was on (a documented quirk, not a bug).
 *
 * Simplification (Phase 4, revisit at Phase 7 when $4015 exists): channel enable isn't wired up
 * yet, so a high-byte timer write always reloads the length counter - real hardware only does so
 * when the channel is enabled.
 */
public class TriangleChannel implements ClockWatcher {
    static final int[] SEQUENCE = {
            15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };
    private static final int SEQUENCE_LENGTH = 32;

    private static final int CONTROL_FLAG_BIT = 0x80; //shared: linear-counter control / length-counter halt

    private static final int TIMER_LOW_MASK = 0xFF;
    private static final int TIMER_HIGH_MASK = 0x07;
    private static final int TIMER_HIGH_SHIFT = 8;
    private static final int LENGTH_LOAD_SHIFT = 3;
    private static final int LENGTH_LOAD_MASK = 0x1F;

    private final LinearCounter linearCounter;
    private final LengthCounter lengthCounter;

    private int timerPeriod;
    private int timerCounter;
    private int sequencePosition;

    public TriangleChannel(){
        this(new LinearCounter(), new LengthCounter());
    }

    TriangleChannel(final LinearCounter linearCounter, final LengthCounter lengthCounter){
        this.linearCounter = linearCounter;
        this.lengthCounter = lengthCounter;
    }

    /**
     * Handle a $4008 write.
     *
     * Register: CRRR RRRR
     * C (bit 7): linear-counter control flag / length-counter halt (shared bit)
     * R (bits 0-6): linear counter reload value
     */
    public void writeLinearCounterRegister(final int value){
        lengthCounter.setHalt((value & CONTROL_FLAG_BIT) != 0);
        linearCounter.writeControlRegister(value);
    }

    /** Handle a $400A write: low 8 bits of the 11-bit timer period. */
    public void writeTimerLow(final int value){
        timerPeriod = (timerPeriod & (TIMER_HIGH_MASK << TIMER_HIGH_SHIFT)) | (value & TIMER_LOW_MASK);
    }

    /**
     * Handle a $400B write.
     *
     * Register: LLLL LHHH
     * L (bits 3-7): length-counter table index
     * H (bits 0-2): high 3 bits of the 11-bit timer period
     */
    public void writeTimerHighAndLengthLoad(final int value){
        timerPeriod = (timerPeriod & TIMER_LOW_MASK) | ((value & TIMER_HIGH_MASK) << TIMER_HIGH_SHIFT);
        lengthCounter.load((value >> LENGTH_LOAD_SHIFT) & LENGTH_LOAD_MASK);
        linearCounter.requestReload();
    }

    /** CPU-cycle clock: unlike pulse/noise, runs at full CPU rate (no APU-cycle parity gating). */
    @Override
    public void tick(){
        if (timerCounter == 0){
            timerCounter = timerPeriod;
            if (!lengthCounter.isZero() && !linearCounter.isZero()){
                sequencePosition = (sequencePosition + 1) % SEQUENCE_LENGTH;
            }
        } else {
            timerCounter--;
        }
    }

    /** Quarter-frame clock: advances the linear counter. */
    public void quarterFrameTick(){
        linearCounter.tick();
    }

    /** Half-frame clock: advances the length counter. */
    public void halfFrameTick(){
        lengthCounter.tick();
    }

    /** Current output: always the current sequence step (real hardware never silences the channel outright). */
    public int outputSample(){
        return SEQUENCE[sequencePosition];
    }

    int currentSequencePosition(){
        return sequencePosition;
    }

    int currentTimerPeriod(){
        return timerPeriod;
    }
}
