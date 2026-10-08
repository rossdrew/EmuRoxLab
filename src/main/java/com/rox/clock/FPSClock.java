package com.rox.clock;

import com.rox.time.Sleeper;
import com.rox.time.TimeSource;

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
    private final TimeSource timeSource;
    private final Sleeper sleeper;

    /** List of subscribers to this {@link Clock}s <cc>tick()</cc> events **/
    private final List<ClockWatcher> listeners = new ArrayList<>();

    private volatile boolean running = false;

    //pause()/resume() handshake with run()'s loop - both guarded by pauseLock. pauseRequested is also
    //volatile so run() can check it once per frame without taking the lock in the common, unpaused case
    private final Object pauseLock = new Object();
    private volatile boolean pauseRequested = false;
    private boolean parked = false;

    /** Current total of remainder ticks after rounding */
    private long tickRemainderBuffer = 0;

    public FPSClock(final long hz,
                    final int framesPerSecond,
                    final TimeSource timeSource,
                    final Sleeper sleeper){
        HZ = hz;
        FPS = framesPerSecond;
        FRAME_TIME_NS = 1_000_000_000L / FPS;
        TICKS_PER_FRAME = HZ / FPS;
        TICKS_REMAINDER_PER_FRAME = HZ % FPS;

        this.timeSource = timeSource;
        this.sleeper = sleeper;
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

        //anchored to a single fixed reference point (runStartTime), not re-measured fresh each
        //frame - Thread.sleep() (via Sleeper) routinely oversleeps its requested duration by tens to
        //hundreds of microseconds (OS scheduler granularity), and a fresh-each-frame measurement
        //never recovers that lost time, so it accumulates: ~10ms/s of real-time drift measured
        //empirically, enough to exhaust a 200ms audio buffer margin in about 20 seconds. Anchoring
        //each frame's deadline to runStartTime + frameIndex*FRAME_TIME_NS means an oversleep on one
        //frame simply shortens (or skips) the next frame's sleep, so the schedule self-corrects
        //instead of drifting indefinitely.
        long runStartTime = timeSource.nanoTime();
        long frameIndex = 0;

        while (running) {
            //XXX This could be wrapped in a executeWithinFrame(()->{})
            runFrame();
            try {
                if (parkWhilePaused()){
                    //however long the pause lasted, it isn't time the schedule should try to catch up
                    //on - re-anchor rather than racing through a burst of unthrottled frames
                    runStartTime = timeSource.nanoTime();
                    frameIndex = 0;
                    continue;
                }
                throttle(runStartTime + frameIndex * FRAME_TIME_NS);
            } catch (InterruptedException e) {
                /* LOG */System.out.println("Encountered issues using Thread.sleep(), terminating clock!");
                running = false;
            }
            frameIndex++;
        }
        //however the loop ended (stop(), or an interrupt above), never leave a pause() waiting forever
        //for a park that can no longer happen
        stop();
    }

    /**
     * Parks the {@link #run()} loop (between frames, never mid-frame) for as long as a {@link #pause()}
     * is in effect.
     *
     * @return true if it parked at all
     */
    private boolean parkWhilePaused() throws InterruptedException {
        if (!pauseRequested){
            return false;
        }
        synchronized (pauseLock){
            parked = true;
            pauseLock.notifyAll(); //release pause()'s wait for this loop to genuinely stop ticking
            try {
                //no separate running check needed: stop() clears pauseRequested under this same lock
                while (pauseRequested){
                    pauseLock.wait();
                }
            } finally {
                parked = false;
            }
        }
        return true;
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
    void throttle(final long frameStartTime) throws InterruptedException {
        long elapsedSinceFrameStart = timeSource.nanoTime() - frameStartTime;
        long timeRemainingInFrame = FRAME_TIME_NS - elapsedSinceFrameStart;

        if (timeRemainingInFrame > 0) {

            //XXX Do we want to just throw the exception here?
            sleeper.sleepFor(timeRemainingInFrame);
        }
    }

    /**
     * Stop the execution of this {@link Clock}
     */
    public void stop(){
        running = false;
        synchronized (pauseLock){
            //a stale request would otherwise park a later run() straight away with nobody to resume it
            pauseRequested = false;
            pauseLock.notifyAll(); //release a parked run() loop, and a pause() still waiting on one
        }
    }

    /**
     * Freezes {@link #run()} at the end of its current frame (up to one frame's worth of ticks away),
     * blocking until it's genuinely parked. Like {@code NES.powerOn()}, waits out interrupts rather
     * than returning early - the caller relies on a {@code true} meaning nothing else is ticking.
     */
    @Override
    public boolean pause(){
        boolean interrupted = false;
        final boolean paused;
        synchronized (pauseLock){
            if (running){
                pauseRequested = true;
            }
            while (pauseRequested && !parked){
                try {
                    pauseLock.wait();
                } catch (InterruptedException e){
                    interrupted = true;
                }
            }
            paused = parked;
        }
        if (interrupted){
            Thread.currentThread().interrupt();
        }
        return paused;
    }

    @Override
    public void resume(){
        synchronized (pauseLock){
            pauseRequested = false;
            pauseLock.notifyAll();
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
