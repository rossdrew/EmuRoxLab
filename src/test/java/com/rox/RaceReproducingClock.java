package com.rox;

import com.rox.clock.Clock;
import com.rox.clock.ClockWatcher;
import com.rox.clock.FPSClock;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

import java.util.concurrent.CountDownLatch;

/**
 * A real NES-rate {@link FPSClock} whose {@link #run()} waits {@code startupDelayMillis} before starting - widening
 * the gap between the clock thread starting and {@code run()} getting going, which on real hardware is only
 * as wide as the OS scheduler makes it. Lets a test land a {@code stop()} in that gap deterministically.
 */
final class RaceReproducingClock implements Clock {
    private final Clock clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());
    private final long startupDelayMillis;
    private final CountDownLatch runCalled = new CountDownLatch(1);

    RaceReproducingClock(final long startupDelayMillis){
        this.startupDelayMillis = startupDelayMillis;
    }

    /** Blocks until {@link #run()} has been called, i.e. its startup delay has begun. */
    void awaitRunCalled() throws InterruptedException {
        runCalled.await();
    }

    @Override
    public void run(){
        runCalled.countDown();
        try {
            Thread.sleep(startupDelayMillis);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        clock.run();
    }

    @Override
    public void stop(){
        clock.stop();
    }

    @Override
    public void prepareToRun(){
        clock.prepareToRun();
    }

    @Override
    public void stopAndWait(){
        clock.stopAndWait();
    }

    @Override
    public boolean pause(){
        return clock.pause();
    }

    @Override
    public void resume(){
        clock.resume();
    }

    @Override
    public boolean isRunning(){
        return clock.isRunning();
    }

    @Override
    public void addListener(final ClockWatcher listener){
        clock.addListener(listener);
    }

    @Override
    public void removeListener(final ClockWatcher listener){
        clock.removeListener(listener);
    }

    @Override
    public void tick(){
        clock.tick();
    }

    @Override
    public int listeners(){
        return clock.listeners();
    }
}
