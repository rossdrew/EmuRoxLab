package com.rox.time;

/**
 * Basically just a wrapper to pull system sleep out for unit testing
 */
public interface Sleeper {
    void sleepFor(long nanos) throws InterruptedException;
}
