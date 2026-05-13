package com.rox;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A clock which generates {@link Clock} ticket to {@link ClockWatcher}s at the
 * specified <cc>hz</cc> in sub second time frame chunks defined by the specified
 * <cc>framesPerSecond</cc>
 */
public class FPSClock implements Clock, AutoCloseable {
    /** Number of clock ticks per second **/
    private final double HZ;
    /** Number of frames per second at the user level */
    private final double FPS;
    /** The size of a frame in nanoseconds */
    private final long FRAME_TIME_NS;
    /** The number of approximate ticks per frame */
    private final double TICKS_PER_FRAME;
    /** List of subscribers to this {@link Clock}s <cc>tick()</cc> events **/
    private final List<ClockWatcher> listeners = new ArrayList<>();

    private volatile boolean running = false;

    /** Current total of remainder ticks after rounding */
    private double tickRemainderBuffer = 0.0;

    public FPSClock(final long hz, final int framesPerSecond){
        HZ = hz;
        FPS = framesPerSecond;
        FRAME_TIME_NS = (long) (1_000_000_000 / FPS);
        TICKS_PER_FRAME = (double) HZ / FPS;
        //TODO Need to deal with franctional drift
    }

    /**
     * Keeps track fo fractional ticks while returning the rounded current set of ticks
     *
     * XXX this is package private to test but it modifies state (tickRemainderBuffer)
     *
     * @return the number of ticks this frame
     */
    long ticksThisFrame(){
        double exactTicks = TICKS_PER_FRAME + tickRemainderBuffer;
        long wholeTicks = (long) exactTicks;
        tickRemainderBuffer = exactTicks - wholeTicks;
        return wholeTicks;
    }

    /**
     * Start the execution of this {@link Clock}
     */
    public void run(){
        if (running){
            throw new IllegalStateException("Clock already running");
        }

        running = true;

        while (running) {
            //XXX This could be wrapped in a executeWithinFrame(()->{})
            long frameStartTime = System.nanoTime();
            tick(ticksThisFrame());
            long elapsedSinceFrameStart = System.nanoTime() - frameStartTime;
            long timeRemainingInFrame = FRAME_TIME_NS - elapsedSinceFrameStart;

            //Wait till next frame
            if (timeRemainingInFrame > 0){
                sleepFor(timeRemainingInFrame);
            }
        }
    }

    /**
     * Stop the execution of this {@link Clock}
     */
    public void stop(){
        running = false;
    }

    private void sleepFor(long nanos) {
        long millis = nanos / 1_000_000;
        int remainingNanos = (int) (nanos % 1_000_000);

        try {
            Thread.sleep(millis, remainingNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            /*LOGGER*/System.out.println("Error while trying to sleep.");
        }
    }

    public boolean isRunning(){
        return running;
    }

    public void addListener(final ClockWatcher listener){
        this.listeners.add(listener);
    }

    public void removeListener(final ClockWatcher listener){
        this.listeners.remove(listener);
    }

    public void tick(final long count){
        for (long i = 0; i < count; i++){
            tick();
        }
    }

    public void tick(){
        listeners.forEach(ClockWatcher::tick);
    }

    public int listeners(){
        return listeners.size();
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
