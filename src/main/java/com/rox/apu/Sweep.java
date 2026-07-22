package com.rox.apu;

/**
 * NES pulse-channel sweep unit ($4001/$4005). Periodically retunes the channel's timer period up
 * or down, clocked once per half-frame. Not shared with other channel types.<br/>
 * <br/>
 * Pulse 1 and pulse 2 negate differently when computing the target period<br/>
 * <ul>
 *     <li>one's-complement for pulse 1 <code>period - change - 1</code></li>
 *     <li>two's-complement for pulse 2 <code>period - change</code></li>
 * </ul>
 * an asymmetry that exists so both channels mute at the same frequency; which mode applies is fixed per
 * instance via the constructor, not part of the register write.
 */
public class Sweep {
    private static final int ENABLED_BIT_MASK = 0x80;
    private static final int PERIOD_MASK = 0x07;
    private static final int NEGATE_BIT_MASK = 0x08;
    private static final int SHIFT_MASK = 0x07;
    private static final int PERIOD_SHIFT = 4;
    private static final int MIN_UNMUTED_PERIOD = 8;
    private static final int MAX_UNMUTED_TARGET_PERIOD = 0x7FF;

    private final boolean onesComplementNegate;

    private boolean enabled;
    private int periodReload;
    /** which direction to sweet in: 0 = positive, 1 = negative */
    private boolean negate;
    /** The current timer period is right-shifted by this many bits to compute a "change amount." A shift of 0 means the change amount equals the period itself; higher shift values make each adjustment smaller/finer. */
    private int shiftCount;

    private boolean reloadRequested;
    private int divider;

    public Sweep(final boolean onesComplementNegate){
        this.onesComplementNegate = onesComplementNegate;
    }

    /**
     * Handle a $4001/$4005 write.
     *
     * Register: EPPP NSSS
     * E (bit 7): enabled
     * P (bits 4-6): divider reload period
     * N (bit 3): negate
     * S (bits 0-2): shift count
     */
    public void writeControlRegister(final int value){
        enabled = (value & ENABLED_BIT_MASK) != 0;
        periodReload = (value >> PERIOD_SHIFT) & PERIOD_MASK;
        negate = (value & NEGATE_BIT_MASK) != 0;
        shiftCount = value & SHIFT_MASK;
        reloadRequested = true;
    }

    /**
     * True if the channel should be silenced regardless of clocking or the enabled flag: the
     * current period is too low, or adjusting it would push the target period out of range.
     */
    public boolean isMuted(final int currentPeriod){
        return currentPeriod < MIN_UNMUTED_PERIOD || targetPeriod(currentPeriod) > MAX_UNMUTED_TARGET_PERIOD;
    }

    /** Half-frame clock: applies the target period (if due) and returns the channel's new timer period. */
    public int clockHalfFrame(final int currentPeriod){
        final int newPeriod = isDueToAdjust(currentPeriod) ? targetPeriod(currentPeriod) : currentPeriod;

        if (divider == 0 || reloadRequested){
            divider = periodReload;
            reloadRequested = false;
        } else {
            divider--;
        }

        return newPeriod;
    }

    /** True if the divider has just run out and sweeping is actually active (enabled, non-zero shift, not muted). */
    private boolean isDueToAdjust(final int currentPeriod){
        return divider == 0 && enabled && shiftCount != 0 && !isMuted(currentPeriod);
    }

    private int targetPeriod(final int currentPeriod){
        final int changeAmount = currentPeriod >> shiftCount;
        if (!negate){
            return currentPeriod + changeAmount;
        }
        return onesComplementNegate ? currentPeriod - changeAmount - 1 : currentPeriod - changeAmount;
    }
}
