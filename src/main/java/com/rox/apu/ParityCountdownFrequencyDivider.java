package com.rox.apu;

import com.rox.clock.ClockWatcher;

/**
 * Every other call to {@link #tick()}, counts down from {@link #getCounterPeriod()} to 0 then
 * executes the given action - the "do real work on every other CPU cycle" gating that pulse and
 * noise channel timers both need (one APU cycle = two CPU cycles).
 *
 * Whether the very first call already counts as an "active" one (and so can run the action
 * immediately, if the countdown is already at 0) is set via the constructor's
 * {@code initialParityGate} argument, which seeds {@link #parityGate}.
 */
public class ParityCountdownFrequencyDivider implements ClockWatcher {
    private final Runnable action;

    private boolean parityGate;
    private int countdown;
    private int counterPeriod;

    public ParityCountdownFrequencyDivider(final Runnable action,
                                           final boolean initialParityGate,
                                           final int counterPeriod) {
        if (counterPeriod < 0){
            throw new IllegalArgumentException("Period must be non-negative since it's a decreasing count that ends at 0.");
        }

        this.action = action;
        this.parityGate = initialParityGate;
        this.counterPeriod = counterPeriod;
    }

    public int getCounterPeriod() {
        return counterPeriod;
    }

    public void setCounterPeriod(final int newCounterPeriod) {
        if (newCounterPeriod < 0){
            throw new IllegalArgumentException("Period must be non-negative since it's a decreasing count that ends at 0.");
        }

        this.counterPeriod = newCounterPeriod;
    }

    /** Toggle the parity boolean and return the result */
    private boolean toggleParityAndGet(){
        parityGate = !parityGate;
        return parityGate;
    }

    @Override
    public void tick() {
        //Only actions half the time, effectively dividing by 2
        if (toggleParityAndGet()){
            //Counts down from `counterPeriod` using `countdown`, firing `action` when we reach zero
            if (countdown == 0){
                countdown = counterPeriod;
                action.run();
            } else {
                countdown--;
            }
        }
    }
}
