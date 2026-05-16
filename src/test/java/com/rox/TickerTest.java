package com.rox;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TickerTest {
    @ParameterizedTest
    @CsvSource({
            "1",
            "5",
            "10",
            "100"
    })
    public void testAdditionOfListeners(final int testListeners) {
        final Ticker ticker = new Ticker();
        for (int i=0; i<testListeners; i++){
            ticker.addListener(mock(ClockWatcher.class));
        }
        assertEquals(testListeners, ticker.listeners());
    }

    @Test
    public void testRemovalOfListener() {
        final Ticker ticker = new Ticker();
        final ClockWatcher a = mock(ClockWatcher.class);
        ticker.addListener(a);
        final ClockWatcher b = mock(ClockWatcher.class);
        ticker.addListener(b);

        ticker.removeListener(a);

        assertEquals(1, ticker.listeners());
    }

    @Test
    public void testSingleTick() {
        final Ticker ticker = new Ticker();
        final ClockWatcher listener = mock(ClockWatcher.class);
        ticker.addListener(listener);

        ticker.tick();

        verify(listener, times(1)).tick();
    }

    @Test
    public void testMultipleTicks() {
        final Ticker ticker = new Ticker();
        final ClockWatcher listener = mock(ClockWatcher.class);
        ticker.addListener(listener);

        ticker.tick();
        ticker.tick();
        ticker.tick();
        ticker.tick();

        verify(listener, times(4)).tick();
    }

    @Property
    public void propertyTestTicks(@ForAll @IntRange(min = 0, max = 489) int ticks) {
        final Ticker ticker = new Ticker();
        final ClockWatcher listener = mock(ClockWatcher.class);
        ticker.addListener(listener);

        for (int i=0; i<ticks; i++){
            ticker.tick();
        }

        verify(listener, times(ticks)).tick();
    }
}
