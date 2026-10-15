package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * NES length counter, shared by the pulse, triangle and noise channels. Silences its channel once
 * it decays to 0 unless halted, clocked once per half-frame.
 *
 * The halt flag is the same physical bit as the owning channel's envelope loop flag (pulse/noise)
 * or linear-counter control flag (triangle) - a hardware quirk, not a coincidence.
 *
 * Also holds the channel's $4015 enable bit, which on real hardware only ever acts on this counter:
 * <ul>
 * <li>disabled - the counter is held at 0, so the channel is silent</li>
 * <li>a load while disabled is ignored</li>
 * </ul>
 */
public class LengthCounter implements ClockWatcher {
    static final int[] LENGTH_TABLE = {
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };

    private int counter;
    private boolean haltEnabled;
    /** The channel's $4015 enable bit - off at power-on. */
    private boolean enabled;

    /** Loads the counter from the table - ignored while disabled. */
    public void load(final int index){
        if (enabled){
            counter = LENGTH_TABLE[index];
        }
    }

    /** A $4015 enable-bit change: disabling immediately forces the counter to 0. */
    public void setEnabled(final boolean enabled){
        this.enabled = enabled;
        if (!enabled){
            counter = 0;
        }
    }

    boolean isEnabled(){
        return enabled;
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

    /** Everything a {@link LengthCounter} needs to carry on exactly where it was. */
    public record Snapshot(int counter, boolean haltEnabled, boolean enabled) {
    }

    public Snapshot snapshot(){
        return new Snapshot(counter, haltEnabled, enabled);
    }

    public void restore(final Snapshot snapshot){
        counter = snapshot.counter();
        haltEnabled = snapshot.haltEnabled();
        enabled = snapshot.enabled();
    }
}
