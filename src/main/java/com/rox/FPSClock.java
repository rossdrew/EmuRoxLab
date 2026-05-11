package com.rox;

import java.util.ArrayList;
import java.util.List;

/**
 * A clock which generates {@link Clock} ticket to {@link ClockWatcher}s at the
 * specified <cc>hz</cc> in sub second time frame chunks defined by the specified
 * <cc>framesPerSecond</cc>
 */
public class FPSClock implements Clock {
    /** Number of clock ticks per second **/
    private final double HZ;
    /** Number of frames per second at the user level */
    private final double FPS;
    /** The size of a frame in nanoseconds */
    private final long FRAME_TIME_NS;

    private final List<ClockWatcher> listeners = new ArrayList<>();

    private boolean running = true;

    public FPSClock(final long hz, final int framesPerSecond){
        HZ = hz;
        FPS = framesPerSecond;
        FRAME_TIME_NS = (long) (1_000_000_000 / FPS);
    }

    //XXX Make this work
    public void run(){
        while (running) {
            //XXX This could be wrapped in a executeWithinFrame(()->{})
            long frameStartTime = System.nanoTime();
            tick();
            long elapsedSinceFrameStart = System.nanoTime() - frameStartTime;
            long timeRemainingInFrame = FRAME_TIME_NS - elapsedSinceFrameStart;

            //Wait till next frame
            if (timeRemainingInFrame > 0){
                sleepFor(timeRemainingInFrame);
            }
        }
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

    public void addListener(final ClockWatcher listener){
        this.listeners.add(listener);
    }

    public void removeListener(final ClockWatcher listener){
        this.listeners.remove(listener);
    }

    public void tick(){
        listeners.forEach(listener -> listener.tick());
    }

    public int listeners(){
        return listeners.size();
    }
}
