package com.rox;

import java.util.ArrayList;
import java.util.List;

/**
 * Something which ticks
 */
public class Ticker implements Clock {
    private final List<ClockWatcher> listeners = new ArrayList<>();

    public void addListener(final ClockWatcher listener){
        this.listeners.add(listener);
    }

    public void removeListener(final ClockWatcher listener){
        this.listeners.remove(listener);
    }

    public void tick(){
        listeners.forEach(listener -> listener.tick());
    }

    public int listeners(){
        return listeners.size();
    }

    @Override
    public void run() {
        throw new RuntimeException("This class is only for testing");
    }

    @Override
    public void stop() {
        throw new RuntimeException("This class is only for testing");
    }

    @Override
    public boolean isRunning() {
        throw new RuntimeException("This class is only for testing");
    }


}
