package com.rox.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FrameJitterTest {
    private static final long MS = 1_000_000L;

    @Test
    public void noIntervalsGiveAllZeroStats(){
        final FrameJitter jitter = new FrameJitter();

        assertEquals(new FrameJitter.Stats(0, 0, 0, 0, 0), jitter.takeStats());
    }

    @Test
    public void theFirstEventOnlySetsTheBaseline(){
        final FrameJitter jitter = new FrameJitter();
        jitter.record(5 * MS);

        assertEquals(0, jitter.takeStats().count());
    }

    @Test
    public void statsDescribeTheGapsBetweenEvents(){
        final FrameJitter jitter = new FrameJitter();
        jitter.record(0);
        jitter.record(10 * MS);
        jitter.record(40 * MS); //a 30ms gap: late
        jitter.record(60 * MS);

        assertEquals(new FrameJitter.Stats(3, 20, 30, 30, 1), jitter.takeStats());
    }

    @Test
    public void aGapOfExactlyTheLateThresholdIsNotLate(){
        final FrameJitter jitter = new FrameJitter();
        jitter.record(0);
        jitter.record(25 * MS); //exactly 1.5 frames

        assertEquals(0, jitter.takeStats().late());
    }

    @Test
    public void p99IsTheValueNinetyNinePercentOfTheWayUp(){
        final FrameJitter jitter = new FrameJitter();
        long t = 0;
        jitter.record(t);
        for (int i = 1; i <= 200; i++){
            t += i * MS;
            jitter.record(t);
        }

        final FrameJitter.Stats stats = jitter.takeStats();
        assertEquals(200, stats.count());
        assertEquals(199, stats.p99Ms(), 1e-9);
        assertEquals(200, stats.maxMs(), 1e-9);
        assertEquals(100.5, stats.meanMs(), 1e-9);
    }

    @Test
    public void takingStatsStartsAFreshWindowFromTheSameBaseline(){
        final FrameJitter jitter = new FrameJitter();
        jitter.record(0);
        jitter.record(50 * MS);
        jitter.takeStats();
        jitter.record(66 * MS);

        assertEquals(new FrameJitter.Stats(1, 16, 16, 16, 0), jitter.takeStats());
    }

    @Test
    public void describeIsCompactAndFixedPrecision(){
        assertEquals("16.7 ms  p99 18.2  max 24.1  late 3",
                new FrameJitter.Stats(60, 16.66, 18.24, 24.1, 3).describe());
    }
}
