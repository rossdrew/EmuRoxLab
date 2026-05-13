package com.rox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FPSClockTest {
    @Test
    public void testUnitClock() {
        final FPSClock clock = new FPSClock(1,1);

        //TODO add watcher
        //TODO start
        //TODO validate 1 click
    }

    @Test
    void testRun() throws InterruptedException {
        final ClockWatcher watcher = mock(ClockWatcher.class);
        try (FPSClock clock = new FPSClock(1,1)){
            clock.addListener(watcher);
            final Thread thread = new Thread(clock::run);
            thread.start();
            Thread.sleep(900);
            clock.stop();
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        verify(watcher, times(1)).tick();
    }

    @Test
    void testRunWhenAlreadyRunning(){
        final ClockWatcher watcher = mock(ClockWatcher.class);
        try (FPSClock clock = new FPSClock(1,1)){
            clock.addListener(watcher);
            final Thread thread = new Thread(clock::run);
            thread.start();
            Thread.sleep(900);
            assertTrue(clock.isRunning());
            clock.run();
        } catch (Exception e) {
            assertTrue(e.getMessage().toLowerCase().contains("already running"));
            return;
        }
        fail("Expected exception, clock is already running");
    }

    @Test
    void testStop() throws InterruptedException {
        final FPSClock clock = new FPSClock(1,1);
        final ClockWatcher watcher = mock(ClockWatcher.class);
        clock.addListener(watcher);

        final Thread thread = new Thread(clock::run);
        thread.start();
        clock.stop();

        assertFalse(clock.isRunning());
    }

    @Test
    void carriesFractionalTicksAcrossFrames() {
        final FPSClock clock = new FPSClock(10, 3);

        long frame1 = clock.ticksThisFrame();
        long frame2 = clock.ticksThisFrame();
        long frame3 = clock.ticksThisFrame();

        assertEquals(3, frame1);
        assertEquals(3, frame2);
        assertEquals(4, frame3);

        assertEquals(10, frame1 + frame2 + frame3);
    }
}
