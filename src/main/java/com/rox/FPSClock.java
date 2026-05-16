package com.rox;

import java.util.ArrayList;
import java.util.List;

/**
 * A clock which generates {@link Clock} ticket to {@link ClockWatcher}s at the
 * specified <cc>hz</cc> in sub second time frame chunks defined by the specified
 * <cc>framesPerSecond</cc>
 *
 * XXX: This cannot deal with fractional FPS such as that of the GameBoy
 */
public class FPSClock implements Clock, AutoCloseable {
    /** Number of clock ticks per second **/
    private final long HZ;
    /** Number of frames per second at the user level */
    private final int FPS;
    /** The size of a frame in nanoseconds */
    private final long FRAME_TIME_NS;
    /** The number of approximate ticks per frame */
    private final long TICKS_PER_FRAME;

    private final long TICKS_REMAINDER_PER_FRAME;

    /** List of subscribers to this {@link Clock}s <cc>tick()</cc> events **/
    private final List<ClockWatcher> listeners = new ArrayList<>();

    private volatile boolean running = false;

    /** Current total of remainder ticks after rounding */
    private long tickRemainderBuffer = 0;

    public FPSClock(final long hz, final int framesPerSecond){
        HZ = hz;
        FPS = framesPerSecond;
        FRAME_TIME_NS = 1_000_000_000L / FPS;
        TICKS_PER_FRAME = HZ / FPS;
        TICKS_REMAINDER_PER_FRAME = HZ % FPS;
    }

    /**
     * Keeps track fo fractional ticks while returning the rounded current set of ticks
     *
     * XXX this is package private to test but it modifies state (tickRemainderBuffer)
     *
     * @return the number of ticks this frame
     */
    long ticksThisFrame(){
        long ticks = TICKS_PER_FRAME;
        tickRemainderBuffer += TICKS_REMAINDER_PER_FRAME;
        if (tickRemainderBuffer >= FPS) {
            ticks++;
            tickRemainderBuffer -= FPS;
        }
        return ticks;
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

            runFrame();
            throttle(frameStartTime);
        }
    }

    /**
     * Execute all <cc>tick()</cc>s in this frame
     */
    void runFrame() {
        tick(ticksThisFrame());
    }

    /**
     * Slow the rate of generating tickets by consuming the rest of the frame
     *
     * @param frameStartTime used to calculate when the frame is expected to end.
     */
    private void throttle(long frameStartTime) {
        long elapsedSinceFrameStart = System.nanoTime() - frameStartTime;
        long timeRemainingInFrame = FRAME_TIME_NS - elapsedSinceFrameStart;

        if (timeRemainingInFrame > 0) {
            sleepFor(timeRemainingInFrame);
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
