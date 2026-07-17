package com.rox.apu;

import com.rox.clock.ClockWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * NES frame counter ($4017). Counts CPU cycles and clocks two independent watcher streams at
 * fixed boundaries, in either 4-step (default, IRQ-generating) or 5-step (IRQ-free) mode: a
 * quarter-frame stream (envelopes, triangle linear counter) and a half-frame stream (length
 * counters, sweep). A half-frame boundary always implies a simultaneous quarter-frame clock.
 *
 * Boundary values are CPU-cycle counts (intentionally 2x the APU-cycle values quoted by NESdev),
 * since {@link #clock()} is driven once per CPU cycle rather than once per APU cycle.
 */
public class FrameSequencer {
    static final int QUARTER_FRAME_1 = 7457;
    static final int HALF_FRAME_1 = 14913;
    static final int QUARTER_FRAME_2 = 22371;
    static final int FOUR_STEP_END = 29829;
    static final int FIVE_STEP_END = 37281;

    private static final int FIVE_STEP_MODE_BIT = 0x80;
    private static final int IRQ_INHIBIT_BIT = 0x40;

    private final List<ClockWatcher> quarterFrameWatchers = new ArrayList<>();
    private final List<ClockWatcher> halfFrameWatchers = new ArrayList<>();

    private boolean fiveStepMode;
    private boolean irqInhibit;
    private int cycle; //counts up, when it reaches the max value for a mode, resets
    private boolean frameIrqPending;

    public void addQuarterFrameWatcher(final ClockWatcher watcher){
        quarterFrameWatchers.add(watcher);
    }

    public void addHalfFrameWatcher(final ClockWatcher watcher){
        halfFrameWatchers.add(watcher);
    }

    public void clock(){
        cycle++;
        if (fiveStepMode){
            clockFiveStepMode();
        } else {
            clockFourStepMode();
        }
    }

    private void clockFourStepMode(){
        switch (cycle){
            case QUARTER_FRAME_1, QUARTER_FRAME_2 -> fireQuarterFrame();
            case HALF_FRAME_1 -> fireHalfFrame();
            case FOUR_STEP_END -> {
                fireHalfFrame();
                if (!irqInhibit){
                    frameIrqPending = true;
                }
                cycle = 0;
            }
            default -> {}
        }
    }

    private void clockFiveStepMode(){
        switch (cycle){
            case QUARTER_FRAME_1, QUARTER_FRAME_2 -> fireQuarterFrame();
            case HALF_FRAME_1 -> fireHalfFrame();
            case FIVE_STEP_END -> {
                fireHalfFrame();
                cycle = 0;
            }
            default -> {}
        }
    }

    private void fireQuarterFrame(){
        for (final ClockWatcher watcher : quarterFrameWatchers){
            watcher.tick();
        }
    }

    private void fireHalfFrame(){
        fireQuarterFrame();
        for (final ClockWatcher watcher : halfFrameWatchers){
            watcher.tick();
        }
    }

    /** Handle a $4017 write: sets mode/inhibit, resets the cycle counter, and (5-step only) fires an immediate clock. */
    public void writeControlRegister(final int value){
        fiveStepMode = (value & FIVE_STEP_MODE_BIT) != 0;
        irqInhibit = (value & IRQ_INHIBIT_BIT) != 0;
        cycle = 0;
        if (irqInhibit){
            frameIrqPending = false;
        }
        if (fiveStepMode){
            fireHalfFrame();
        }
    }

    public boolean isFrameIrqPending(){
        return frameIrqPending;
    }

    public void clearFrameIrq(){
        frameIrqPending = false;
    }
}
