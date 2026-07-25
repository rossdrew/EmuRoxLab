package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES length counter, shared by the pulse, triangle and noise channels. Silences its channel once
 * it decays to 0 unless halted, clocked once per half-frame.
 *
 * The halt flag is the same physical bit as the owning channel's envelope loop flag (pulse/noise)
 * or linear-counter control flag (triangle) - a hardware quirk, not a coincidence.
 */
public class LengthCounter implements ClockWatcher {
    static final int[] LENGTH_TABLE = {
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };

    private int counter;
    private boolean haltEnabled;

    /** Loads the counter from the table. Callers are responsible for only doing so when the channel is enabled. */
    public void load(final int index){
        counter = LENGTH_TABLE[index];
    }

    /** Forces the counter to 0 - used when the owning channel is disabled via $4015. */
    public void forceZero(){
        counter = 0;
    }

    public void setHalt(final boolean halt){
        haltEnabled = halt;
    }

    /** Half-frame clock: decrements the counter unless halted or already at 0. */
    @Override
    public void tick(){
        if (!haltEnabled && counter > 0){
            counter--;
        }
    }

    public boolean isZero(){
        return counter == 0;
    }

    public int value(){
        return counter;
    }
}
