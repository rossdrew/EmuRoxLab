package com.rox;

/**
 * Something which generates ticks to {@link ClockWatcher}s
 */
public interface Clock {
    /** Add a {@link com.rox.ClockWatcher} to watch for <cc>tick()</cc> events **/
    void addListener(final ClockWatcher listener);
    /** Remove a {@link com.rox.ClockWatcher} to no longer watch for <cc>tick()</cc> events **/
    void removeListener(final ClockWatcher listener);
    /** Inform all {@link com.rox.ClockWatcher}s of a <cc>tick()</cc> event **/
    void tick();
    /** Returns the number of {@link com.rox.ClockWatcher}s on this {@link Ticker} **/
    int listeners();

    void run();
    void stop();
}
