package com.rox;

/**
 * Something which watches things that tick {@link Ticker}
 */
@FunctionalInterface
public interface ClockWatcher {
    void tick();
}
