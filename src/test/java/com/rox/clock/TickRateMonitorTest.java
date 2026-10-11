package com.rox.clock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TickRateMonitorTest {
    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong(5 * SECOND); //an arbitrary non-zero start
    private final TickRateMonitor monitor = new TickRateMonitor(now::get, SECOND);

    private void tick(final int times){
        for (int i = 0; i < times; i++){
            monitor.tick();
        }
    }

    @Test
    public void countsEveryTick(){
        tick(3);

        assertEquals(3, monitor.ticks());
    }

    @Test
    public void reportsZeroUntilTheFirstWindowCompletes(){
        tick(1000);
        now.addAndGet(SECOND - 1);

        assertEquals(0.0, monitor.measuredHz());
    }

    @Test
    public void reportsTicksPerSecondOverACompletedWindow(){
        tick(1000);
        now.addAndGet(SECOND);

        assertEquals(1000.0, monitor.measuredHz());
    }

    @Test
    public void scalesALongerWindowToPerSecond(){
        tick(3000);
        now.addAndGet(2 * SECOND);

        assertEquals(1500.0, monitor.measuredHz());
    }

    @Test
    public void holdsTheLastRateUntilTheNextWindowCompletes(){
        tick(1000);
        now.addAndGet(SECOND);
        monitor.measuredHz();

        tick(50); //only part-way into the next window
        now.addAndGet(SECOND / 2);

        assertEquals(1000.0, monitor.measuredHz());
    }

    @Test
    public void eachWindowOnlyCountsItsOwnTicks(){
        tick(1000);
        now.addAndGet(SECOND);
        monitor.measuredHz();

        tick(250);
        now.addAndGet(SECOND);

        assertEquals(250.0, monitor.measuredHz());
    }

    @Test
    public void dropsToZeroWhenNothingTicksForAWholeWindow(){
        tick(1000);
        now.addAndGet(SECOND);
        monitor.measuredHz();

        now.addAndGet(SECOND); //e.g. paused

        assertEquals(0.0, monitor.measuredHz());
    }

    @Test
    public void defaultWindowIsOneSecond(){
        final TickRateMonitor defaultMonitor = new TickRateMonitor(now::get);
        defaultMonitor.tick();
        now.addAndGet(SECOND - 1);
        assertEquals(0.0, defaultMonitor.measuredHz());

        now.addAndGet(1);

        assertEquals(1.0, defaultMonitor.measuredHz(), 1e-9);
    }
}
