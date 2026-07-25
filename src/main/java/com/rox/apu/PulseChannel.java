package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES pulse channel ($4000-$4003 / $4004-$4007). Composes an {@link Envelope}, {@link LengthCounter}
 * and {@link Sweep} with an 11-bit timer and an 8-step duty-cycle sequencer.
 *
 * Registered on the CPU clock directly via {@link #tick()} (timer/sequencer run at APU-cycle rate,
 * every other CPU cycle). {@link #quarterFrameTick()} and {@link #halfFrameTick()} are plain methods
 * rather than a shared listener interface - nothing needs to tell quarter and half frame clocks
 * apart, so a channel is registered with the frame sequencer as two separate method references.
 */
public class PulseChannel implements ClockWatcher {
    /**
     * The fraction of each waveform period the signal spends "high" versus "low,"<br/>
     * The NES lets you choose between four different duty cycle patterns per pulse channel.
     */
    public static final int[][] DUTY_TABLES = {
            {0, 1, 0, 0, 0, 0, 0, 0}, //0: 12.5%
            {0, 1, 1, 0, 0, 0, 0, 0}, //1: 25%
            {0, 1, 1, 1, 1, 0, 0, 0}, //2: 50%
            {1, 0, 0, 1, 1, 1, 1, 1}  //3: 75% (inverted 25%)
    };
    private static final int SEQUENCE_LENGTH = 8;

    private static final int DUTY_SHIFT = 6;
    private static final int DUTY_MASK = 0x03;
    private static final int LENGTH_HALT_BIT = 0x20;

    private static final int TIMER_LOW_MASK = 0xFF;
    private static final int TIMER_HIGH_MASK = 0x07;
    private static final int TIMER_HIGH_SHIFT = 8;
    private static final int LENGTH_LOAD_SHIFT = 3;
    private static final int LENGTH_LOAD_MASK = 0x1F;

    private final Envelope envelope;
    private final LengthCounter lengthCounter;
    private final Sweep sweep;

    private int dutyCycle;
    private int sequencePosition;
    private boolean enabled;

    private final ParityCountdownFrequencyDivider frequencyDivider;

    public PulseChannel(final boolean onesComplementNegate){
        this(new Envelope(), new LengthCounter(), new Sweep(onesComplementNegate));
    }

    PulseChannel(final Envelope envelope, final LengthCounter lengthCounter, final Sweep sweep){
        this.envelope = envelope;
        this.lengthCounter = lengthCounter;
        this.sweep = sweep;

        this.frequencyDivider = new ParityCountdownFrequencyDivider(this::advanceSequencePosition, false, 0);
    }

    /**
     * Handle a $4000/$4004 write.
     *
     * Register: DDLC VVVV
     * D (bits 6-7): duty cycle select
     * L (bit 5): length-counter halt / envelope loop (shared bit)
     * C (bit 4): constant volume
     * V (bits 0-3): volume / envelope period
     */
    public void writeControlRegister(final int value){
        dutyCycle = (value >> DUTY_SHIFT) & DUTY_MASK;
        lengthCounter.setHalt((value & LENGTH_HALT_BIT) != 0);
        envelope.writeControlRegister(value);
    }

    /** Handle a $4001/$4005 sweep-unit write. */
    public void writeSweepRegister(final int value){
        sweep.writeControlRegister(value);
    }

    /** Handle a $4002/$4006 write: low 8 bits of the 11-bit timer period. */
    public void writeTimerLow(final int value){
        frequencyDivider.setCounterPeriod((frequencyDivider.getCounterPeriod() & (TIMER_HIGH_MASK << TIMER_HIGH_SHIFT)) | (value & TIMER_LOW_MASK));
    }

    /**
     * Handle a $4003/$4007 write.
     *
     * Register: LLLL LHHH
     * L (bits 3-7): length-counter table index
     * H (bits 0-2): high 3 bits of the 11-bit timer period
     */
    public void writeTimerHighAndLengthLoad(final int value){
        frequencyDivider.setCounterPeriod((frequencyDivider.getCounterPeriod() & TIMER_LOW_MASK) | ((value & TIMER_HIGH_MASK) << TIMER_HIGH_SHIFT));
        if (enabled){
            lengthCounter.load((value >> LENGTH_LOAD_SHIFT) & LENGTH_LOAD_MASK);
        }
        sequencePosition = 0;
        envelope.restart();
    }

    /** Handle a $4015 enable-bit change: disabling immediately forces the length counter to 0. */
    public void setEnabled(final boolean enabled){
        this.enabled = enabled;
        if (!enabled){
            lengthCounter.forceZero();
        }
    }

    /** Whether the length counter is currently nonzero - for the $4015 status read. */
    public boolean isLengthCounterActive(){
        return !lengthCounter.isZero();
    }

    /** CPU-cycle clock: the timer/sequencer run at half this rate (once per APU cycle). */
    @Override
    public void tick(){
        frequencyDivider.tick();
    }

    private void advanceSequencePosition(){
        sequencePosition = (sequencePosition + 1) % SEQUENCE_LENGTH;
    }

    /** Quarter-frame clock: advances the envelope. */
    public void quarterFrameTick(){
        envelope.tick();
    }

    /** Half-frame clock: advances the length counter and lets the sweep unit retune the timer period. */
    public void halfFrameTick(){
        lengthCounter.tick();
        frequencyDivider.setCounterPeriod(sweep.clockHalfFrame(frequencyDivider.getCounterPeriod()));
    }

    /** Current output: 0 if silenced by the length counter, the sweep unit, or the duty waveform, else the envelope volume. */
    public int outputSample(){
        if (lengthCounter.isZero() || sweep.isMuted(frequencyDivider.getCounterPeriod()) || DUTY_TABLES[dutyCycle][sequencePosition] == 0){
            return 0;
        }
        return envelope.volume();
    }

    int currentSequencePosition(){
        return sequencePosition;
    }

    boolean isEnabled(){
        return enabled;
    }
}
