package com.rox.time;

public final class ThreadSleeper implements Sleeper {
    @Override
    public void sleepFor(long nanos) {
        long millis = nanos / 1_000_000;
        int remainingNanos = (int) (nanos % 1_000_000);

        try {
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
