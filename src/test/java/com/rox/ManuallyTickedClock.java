package com.rox;

import com.rox.clock.Clock;
import com.rox.clock.ClockWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Clock} double that only ever ticks when a test calls {@link #tick()} (reporting itself running
 * only if told to via {@link #setRunning}), and is always "paused" - lets a test put the CPU at a chosen point mid-instruction before calling
 * {@code NES.pause()}, which a real-time {@code FPSClock} frame boundary can't do deterministically.
 */
final class ManuallyTickedClock implements Clock {
    private final List<ClockWatcher> listeners = new ArrayList<>();
    private boolean running;

    @Override
    public void addListener(final ClockWatcher listener){
        listeners.add(listener);
    }

    @Override
    public void removeListener(final ClockWatcher listener){
        listeners.remove(listener);
    }

    @Override
    public void tick(){
        listeners.forEach(ClockWatcher::tick);
    }

    @Override
    public int listeners(){
        return listeners.size();
    }

    @Override
    public void run(){
    }

    @Override
    public void stop(){
    }

    /** Lets a test stand in for "the clock thread is running" while still ticking by hand. */
    void setRunning(final boolean running){
        this.running = running;
    }

    @Override
    public boolean isRunning(){
        return running;
    }

    @Override
    public boolean pause(){
        return true;
    }
}
