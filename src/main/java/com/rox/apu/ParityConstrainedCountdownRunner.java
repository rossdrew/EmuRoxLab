package com.rox.apu;

/**
 * Every other call to {@link #run()}, counts down from {@link #getCounterPeriod()} to 0 then
 * executes the given action - the "do real work on every other CPU cycle" gating that pulse and
 * noise channel timers both need (one APU cycle = two CPU cycles).
 *
 * Whether the very first call already counts as an "active" one (and so can run the action
 * immediately, if the countdown is already at 0) is set via the constructor's
 * {@code initialParityGate} argument, which seeds {@link #parityGate}.
 */
public class ParityConstrainedCountdownRunner implements Runnable {
    private final Runnable action;

    private boolean parityGate;
    private int countdown;
    private int counterPeriod;

    public ParityConstrainedCountdownRunner(final Runnable action,
                                            final boolean initialParityGate,
                                            final int counterPeriod) {
        if (counterPeriod < 0){
            throw new IllegalArgumentException("Period must be positive since it's a decreasing count that ends at 0.");
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
            throw new IllegalArgumentException("Period must be positive since it's a decreasing count that ends at 0.");
        }

        this.counterPeriod = newCounterPeriod;
    }

    @Override
    public void run(){
        parityGate = !parityGate;
        if (!parityGate){
            return;
        }

        if (countdown == 0){
            countdown = counterPeriod;
            action.run();
        } else {
            countdown--;
        }
    }
}