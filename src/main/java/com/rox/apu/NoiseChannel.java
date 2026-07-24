package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES noise channel ($400C / $400E / $400F). Composes an {@link Envelope} and {@link LengthCounter}
 * (same shape as pulse, minus the sweep) with a period looked up from a fixed table and a 15-bit
 * linear-feedback shift register (LFSR) that produces the pseudo-random bit stream.
 *
 * Like pulse, the timer is clocked once per APU cycle (every other CPU cycle - see the parity flag
 * in {@link #tick()}). Unlike pulse, the period isn't an arbitrary 11-bit value split across two
 * register writes; $400E's low nibble simply selects one of 16 fixed periods from
 * {@link #NTSC_NOISE_PERIODS}.
 *
 * On each timer reload, the shift register is clocked: feedback is the XOR of bit 0 and either bit 1
 * (mode clear, the usual ~32767-step-long sequence) or bit 6 (mode set, a shorter, more metallic-
 * sounding ~93-step sequence that repeats sooner), the register shifts right by one, and the vacated
 * bit 14 is set to that feedback. The channel is silenced whenever bit 0 of the register is set -
 * this is what makes the output "pseudo-random" rather than a fixed tone.
 *
 * Simplification (Phase 5, revisit at Phase 7 when $4015 exists): channel enable isn't wired up yet,
 * so a $400F write always reloads the length counter - real hardware only does so when the channel
 * is enabled.
 */
public class NoiseChannel implements ClockWatcher {
    /** NTSC noise periods (in APU cycles), selected by the low nibble of $400E. */
    static final int[] NTSC_NOISE_PERIODS = {
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };

    private static final int LENGTH_HALT_BIT = 0x20;

    private static final int MODE_BIT = 0x80;
    private static final int PERIOD_INDEX_MASK = 0x0F;

    private static final int LENGTH_LOAD_SHIFT = 3;
    private static final int LENGTH_LOAD_MASK = 0x1F;

    private static final int SHIFT_REGISTER_RESET = 1;
    private static final int SHORT_MODE_FEEDBACK_TAP = 6;
    private static final int LONG_MODE_FEEDBACK_TAP = 1;
    private static final int VACATED_BIT_SHIFT = 14;

    private final Envelope envelope;
    private final LengthCounter lengthCounter;

    private boolean mode;
    private int shiftRegister = SHIFT_REGISTER_RESET;

    private final ParityConstrainedCountdownRunner runner;

    public NoiseChannel(){
        this(new Envelope(), new LengthCounter());
    }

    NoiseChannel(final Envelope envelope, final LengthCounter lengthCounter){
        this.envelope = envelope;
        this.lengthCounter = lengthCounter;

        this.runner = new ParityConstrainedCountdownRunner(this::clockShiftRegister, false, 0);
    }

    /**
     * Handle a $400C write.
     *
     * Register: --LC VVVV
     * L (bit 5): length-counter halt / envelope loop (shared bit)
     * C (bit 4): constant volume
     * V (bits 0-3): volume / envelope period
     */
    public void writeControlRegister(final int value){
        lengthCounter.setHalt((value & LENGTH_HALT_BIT) != 0);
        envelope.writeControlRegister(value);
    }

    /**
     * Handle a $400E write.
     *
     * Register: M--- PPPP
     * M (bit 7): mode flag (selects the feedback tap)
     * P (bits 0-3): index into {@link #NTSC_NOISE_PERIODS}
     */
    public void writeModeAndPeriod(final int value){
        mode = (value & MODE_BIT) != 0;
        runner.setCounterPeriod(NTSC_NOISE_PERIODS[value & PERIOD_INDEX_MASK]);
    }

    /**
     * Handle a $400F write.
     *
     * Register: LLLL L---
     * L (bits 3-7): length-counter table index
     */
    public void writeLengthLoad(final int value){
        lengthCounter.load((value >> LENGTH_LOAD_SHIFT) & LENGTH_LOAD_MASK);
        envelope.restart();
    }

    /** CPU-cycle clock: the timer runs at half this rate (once per APU cycle), same as pulse. */
    @Override
    public void tick(){
        runner.run();
    }

    private void clockShiftRegister(){
        final int otherTap = mode ? SHORT_MODE_FEEDBACK_TAP : LONG_MODE_FEEDBACK_TAP;
        final int feedback = (shiftRegister ^ (shiftRegister >> otherTap)) & 1;
        shiftRegister >>= 1;
        shiftRegister |= feedback << VACATED_BIT_SHIFT;
    }

    /** Quarter-frame clock: advances the envelope. */
    public void quarterFrameTick(){
        envelope.tick();
    }

    /** Half-frame clock: advances the length counter. */
    public void halfFrameTick(){
        lengthCounter.tick();
    }

    /** Current output: 0 if silenced by the length counter or the shift register's bit 0, else the envelope volume. */
    public int outputSample(){
        if (lengthCounter.isZero() || (shiftRegister & 1) != 0){
            return 0;
        }
        return envelope.volume();
    }

    int currentTimerPeriod(){
        return runner.getCounterPeriod();
    }

    int currentShiftRegister(){
        return shiftRegister;
    }

    boolean currentMode(){
        return mode;
    }
}
