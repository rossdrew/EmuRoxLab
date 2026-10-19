package com.rox.video;

import java.util.Arrays;
import java.util.Locale;

/**
 * Frame-to-frame intervals of one stream of events (frames produced, or frames shown), gathered between
 * {@link #takeStats()} calls. The average says whether a stream keeps up, but lag is felt in the spread:
 * one 50ms gap among 16.7ms ones is a visible hitch that barely moves the mean - hence p99, max and a count
 * of {@link #LATE_MS late} gaps. {@link #record} and {@link #takeStats} may be called from different threads.
 */
public final class FrameJitter {
    /** A gap this long means at least one display refresh showed the previous frame again: 1.5 frames at 60Hz. */
    public static final double LATE_MS = 1.5 * 1000 / 60;
    private static final int INITIAL_CAPACITY = 128;

    /** What one window of intervals looked like; all zero if there were none. */
    public record Stats(int count, double meanMs, double p99Ms, double maxMs, int late) {
        /** e.g. {@code 16.7 ms  p99 18.2  max 24.1  late 0}. */
        public String describe(){
            return String.format(Locale.ROOT, "%4.1f ms  p99 %4.1f  max %4.1f  late %d", meanMs, p99Ms, maxMs, late);
        }
    }

    private long previousNanos;
    private boolean started;
    private double[] intervalsMs = new double[INITIAL_CAPACITY];
    private int count;

    /** Notes an event at {@code nanos} (a {@link System#nanoTime()} reading); the first only sets a baseline. */
    public synchronized void record(final long nanos){
        if (started){
            if (count == intervalsMs.length){
                intervalsMs = Arrays.copyOf(intervalsMs, count * 2);
            }
            intervalsMs[count++] = (nanos - previousNanos) / 1e6;
        }
        previousNanos = nanos;
        started = true;
    }

    /** The intervals recorded since the last call, which are then forgotten (the baseline is kept). */
    public synchronized Stats takeStats(){
        if (count == 0){
            return new Stats(0, 0, 0, 0, 0);
        }
        final double[] sorted = Arrays.copyOf(intervalsMs, count);
        Arrays.sort(sorted);
        double sum = 0;
        int late = 0;
        for (final double ms : sorted){
            sum += ms;
            if (ms > LATE_MS){
                late++;
            }
        }
        final Stats stats = new Stats(count, sum / count, sorted[(int) (0.99 * count)],
                sorted[count - 1], late);
        count = 0;
        return stats;
    }
}
