package com.rox.clock;

import com.rox.time.TimeSource;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures how many ticks a {@link Clock} actually delivers per wall-clock second - compare against the
 * intended rate (e.g. the NES CPU's 1,789,773Hz) to tell "the emulator can't keep up" from "the game
 * itself is slow". {@link #tick()} runs on the clock thread; {@link #measuredHz()} on a single reader
 * thread (e.g. a UI refresh timer).
 *
 * <p>{@code FPSClock} ticks a whole frame in a burst then sleeps, so a short measuring window would
 * swing wildly depending on how many bursts it caught. The rate is therefore only recalculated once at
 * least {@code windowNanos} has passed, and the last completed window's rate is reported in between.
 */
public final class TickRateMonitor implements ClockWatcher {
    public static final long DEFAULT_WINDOW_NANOS = 1_000_000_000L;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final TimeSource timeSource;
    private final long windowNanos;
    //written only by the clock thread: a plain counter, published with lazySet (a cheap ordered store,
    //no full fence) since this runs on every tick
    private long count;
    private final AtomicLong publishedCount = new AtomicLong();

    //reader-thread only
    private long windowStartNanos;
    private long windowStartCount;
    private double lastMeasuredHz;

    public TickRateMonitor(final TimeSource timeSource){
        this(timeSource, DEFAULT_WINDOW_NANOS);
    }

    public TickRateMonitor(final TimeSource timeSource, final long windowNanos){
        this.timeSource = timeSource;
        this.windowNanos = windowNanos;
        this.windowStartNanos = timeSource.nanoTime();
    }

    @Override
    public void tick(){
        publishedCount.lazySet(++count);
    }

    /** Every tick seen so far. */
    public long ticks(){
        return publishedCount.get();
    }

    /** Ticks per second over the last completed window - 0 until the first window completes. */
    public double measuredHz(){
        final long now = timeSource.nanoTime();
        final long elapsed = now - windowStartNanos;
        if (elapsed >= windowNanos){
            final long ticksNow = publishedCount.get();
            lastMeasuredHz = (ticksNow - windowStartCount) * NANOS_PER_SECOND / elapsed;
            windowStartNanos = now;
            windowStartCount = ticksNow;
        }
        return lastMeasuredHz;
    }
}
