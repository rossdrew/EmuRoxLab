package com.rox.clock;

/**
 * Something which generates ticks to {@link ClockWatcher}s
 */
public interface Clock {
    /** Add a {@link ClockWatcher} to watch for <cc>tick()</cc> events **/
    void addListener(final ClockWatcher listener);
    /** Remove a {@link ClockWatcher} to no longer watch for <cc>tick()</cc> events **/
    void removeListener(final ClockWatcher listener);
    /** Inform all {@link ClockWatcher}s of a <cc>tick()</cc> event **/
    void tick();
    /** Returns the number of {@link ClockWatcher}s on this {@link Ticker} **/
    int listeners();

    void run();
    void stop();
    boolean isRunning();
}
