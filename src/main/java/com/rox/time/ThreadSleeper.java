package com.rox.time;

public final class ThreadSleeper implements Sleeper {
    @Override
    public void sleepFor(long nanos) throws InterruptedException {
        long millis = nanos / 1_000_000;
        int remainingNanos = (int) (nanos % 1_000_000);

        Thread.sleep(millis, remainingNanos);
    }
}
