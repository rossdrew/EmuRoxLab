package com.rox;

import com.rox.clock.Clock;
import com.rox.clock.ClockWatcher;

/**
 * A {@link Clock} double that reliably reproduces the exact race {@code NES.powerOn()} guards
 * against: on real hardware/{@code FPSClock}, there's a real but nanosecond-scale gap between
 * {@code clockThread} starting and {@code run()}'s own {@code running = true} - narrow enough that it
 * only manifested on CI's more constrained scheduler, never on a quiet dev machine. This double widens
 * that same gap to {@code startupDelayMillis} so a regression is deterministic to catch on any machine.
 * Like real {@code FPSClock.run()}, {@code run()} here unconditionally sets {@code running} back to
 * true the moment it starts, regardless of any {@code stop()} that already landed during the delay -
 * that's the bug being reproduced, not something this double works around.
 */
final class RaceReproducingClock implements Clock {
    private final long startupDelayMillis;
    private volatile boolean running;

    RaceReproducingClock(final long startupDelayMillis){
        this.startupDelayMillis = startupDelayMillis;
    }

    @Override
    public void run(){
        try {
            Thread.sleep(startupDelayMillis);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        running = true;
        while (running){
            try {
                Thread.sleep(5);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void stop(){
        running = false;
    }

    @Override
    public boolean isRunning(){
        return running;
    }

    @Override
    public void addListener(final ClockWatcher listener){
    }

    @Override
    public void removeListener(final ClockWatcher listener){
    }

    @Override
    public void tick(){
    }

    @Override
    public int listeners(){
        return 0;
    }
}
