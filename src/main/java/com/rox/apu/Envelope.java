package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES pulse/noise envelope generator ($4000/$4004/$400C). Produces a 4-bit volume that either
 * decays from 15 to 0 over time or holds a constant value, clocked once per quarter-frame.
 *
 * The loop flag (bit5 of the control byte) is the same physical bit as the owning channel's
 * length-counter halt flag - a hardware quirk, not a coincidence, so callers read both meanings
 * from the one write.
 */
public class Envelope implements ClockWatcher {
    private static final int RELOAD_VALUE_MASK = 0x0F;
    private static final int CONSTANT_VOLUME_BIT = 0x10;
    private static final int LOOP_BIT = 0x20;
    private static final int MAX_DECAY_LEVEL = 15;

    private int reloadValue;
    private boolean volumeIsConstant;
    private boolean loopEnabled;

    private boolean restartRequested;
    /** The countdown from {@link #reloadValue} and represents the speed of decay, 0 is fastest */
    private int divider;
    /** The countdown from {@link MAX_DECAY_LEVEL} */
    private int decayLevel; //the decay countdown from

    /**
     * Handle a $4000/$4004/$400C write: sets reload value V, constant-volume flag, loop flag.
     *
     * Register: --LC VVVV
     * V (bits 0-3): a 4-bit value with a dual purpose (fixed volume level or decay speed)
     * C (bit 4): the constant volume flag
     * L (bit 5): the loop flag
     */
    public void writeControlRegister(final int value){
        reloadValue = value & RELOAD_VALUE_MASK;
        volumeIsConstant = (value & CONSTANT_VOLUME_BIT) != 0;
        loopEnabled = (value & LOOP_BIT) != 0;
    }

    /** Requests a restart on the next {@link #tick()} - triggered by a channel's high-byte timer write. */
    public void restart(){
        restartRequested = true;
    }

    /** Quarter-frame clock: advances the decay envelope or services a pending restart. */
    @Override
    public void tick(){
        if (restartRequested){
            restartRequested = false;
            decayLevel = MAX_DECAY_LEVEL;
            divider = reloadValue;
            return;
        }
        /**
         * XXX this could probably be expressed better:
         * Each clock: reduce the {@link #divider},
         *             if it reaches zero, reset it and reduce {@link #decayLevel}
         *                                 if {@link #loopEnabled} then reset decay level when it reaches 0
         */
        if (divider == 0){
            divider = reloadValue;
            if (decayLevel > 0){
                decayLevel--;
            } else if (loopEnabled){
                decayLevel = MAX_DECAY_LEVEL;
            }
        } else {
            divider--;
        }
    }

    /** Current output volume: the constant reload value if constant-volume is set, else the decay level. */
    public int volume(){
        return volumeIsConstant ? reloadValue : decayLevel;
    }
}
