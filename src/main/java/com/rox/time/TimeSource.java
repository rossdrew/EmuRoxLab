package com.rox.time;

/**
 * Basically just a wrapper to pull system time out for unit testing
 */
public interface TimeSource {
    long nanoTime();
}