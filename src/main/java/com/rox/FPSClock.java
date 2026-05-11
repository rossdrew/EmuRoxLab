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
    /** The number of approximate ticks per frame */
    private final double TICKS_PER_FRAME;
    /** List of subscribers to this {@link Clock}s <cc>tick()</cc> events **/
    private final List<ClockWatcher> listeners = new ArrayList<>();

    private boolean running = true;

    public FPSClock(final long hz, final int framesPerSecond){
        HZ = hz;
        FPS = framesPerSecond;
        FRAME_TIME_NS = (long) (1_000_000_000 / FPS);
        TICKS_PER_FRAME = (double) HZ / FPS;
        //TODO Need to deal with franctional drift
    }

    public void run(){
        while (running) {
            //XXX This could be wrapped in a executeWithinFrame(()->{})
            long frameStartTime = System.nanoTime();
            tick(TICKS_PER_FRAME);
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

    public void tick(final double count){
        for (int i=0; i<count; i++){
            tick();
        }
    }

    public void tick(){
        listeners.forEach(ClockWatcher::tick);
    }

    public int listeners(){
        return listeners.size();
    }
}
