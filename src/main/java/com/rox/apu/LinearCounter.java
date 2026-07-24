package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES triangle channel's linear counter ($4008). A second, independently-configured counter
 * (alongside the triangle's shared {@link LengthCounter}) that must also be nonzero for the
 * triangle's sequencer to advance - this is what lets games give the triangle a note duration
 * shorter than the length counter's coarse table allows.
 *
 * The control flag (bit7 of $4008) is the same physical bit as the channel's length-counter halt
 * flag - a hardware quirk, not a coincidence - so callers read both meanings from the one write.
 */
public class LinearCounter implements ClockWatcher {
    private static final int CONTROL_FLAG_BIT = 0x80;
    private static final int RELOAD_VALUE_MASK = 0x7F;

    private int reloadValue;
    private boolean controlFlagSet;
    private int counter;
    private boolean reloadFlagSet;

    /**
     * Handle a $4008 write.
     *
     * Register: CRRR RRRR
     * C (bit 7): control flag (shared with the channel's length-counter halt flag)
     * R (bits 0-6): reload value
     */
    public void writeControlRegister(final int value){
        controlFlagSet = (value & CONTROL_FLAG_BIT) != 0;
        reloadValue = value & RELOAD_VALUE_MASK;
    }

    /** Requests a reload on the next {@link #tick()} - triggered by a $400B (timer high) write. */
    public void requestReload(){
        reloadFlagSet = true;
    }

    /**
     * Quarter-frame clock: reloads from {@link #reloadValue} if a reload is pending, else decrements
     * towards 0; the reload flag itself only survives past this tick if the control flag is set.
     */
    @Override
    public void tick(){
        if (reloadFlagSet){
            counter = reloadValue;
        } else if (counter > 0){
            counter--;
        }
        if (!controlFlagSet){
            reloadFlagSet = false;
        }
    }

    public boolean isZero(){
        return counter == 0;
    }
}
