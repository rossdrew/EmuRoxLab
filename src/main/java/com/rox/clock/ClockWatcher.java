package com.rox.clock;

/**
 * Something which watches things that tick {@link Ticker}
 */
@FunctionalInterface
public interface ClockWatcher {
    void tick();
}
