package com.rox;

import java.util.ArrayList;
import java.util.List;

/**
 * Something which ticks
 */
public class Ticker {
    private final List<TickListener> listeners = new ArrayList<>();

    public void addListener(final TickListener listener){
        this.listeners.add(listener);
    }

    public void removeListener(final TickListener listener){
        this.listeners.remove(listener);
    }

    public void tick(){
        listeners.forEach(listener -> listener.tick());
    }

    public int listeners(){
        return listeners.size();
    }
}
