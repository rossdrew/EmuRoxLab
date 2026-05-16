package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FPSClockTest {
    private class CountingClockWatcher implements ClockWatcher {
        long ticks = 0;
        @Override
        public void tick() {
            ticks++;
        }
    }

    @Test
    public void testUnitClock() {
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

    @ParameterizedTest(name = "{0}: {2}Hz at {1}fps")
    @CsvSource({
            "NES_NTSC,60,1789773,",
            "NES_PAL,50,1789773",
            //"GAMEBOY_DMG, 59.7275, 4194304" //Requires fractional framerate
    })
    public void testRealWorldExamples(final String description, final int fps, final long hz) {
        final FPSClock clock = new FPSClock(hz, fps);
        final CountingClockWatcher watcher = new CountingClockWatcher();

        clock.addListener(watcher);
        for (int frame = 0; frame < fps; frame++) {
            clock.runFrame();
        }
        assertEquals(hz, watcher.ticks);
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
