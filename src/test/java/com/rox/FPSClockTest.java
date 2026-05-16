package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

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
    public void addListener(){
        final Clock clock = new FPSClock(1,1);

        clock.addListener(mock(ClockWatcher.class));
        clock.addListener(mock(ClockWatcher.class));

        assertEquals(2, clock.listeners());
    }

    @Test
    public void removeListener(){
        final Clock clock = new FPSClock(1,1);

        final ClockWatcher a = mock(ClockWatcher.class);
        final ClockWatcher b = mock(ClockWatcher.class);
        final ClockWatcher c = mock(ClockWatcher.class);
        final ClockWatcher d = mock(ClockWatcher.class);

        Arrays.asList(a,b,c,d).forEach(clock::addListener);
        assert clock.listeners() == 4 : "Test not setup properly: Expected 4 test ClockWatchers";

        clock.removeListener(b);

        assertEquals(3, clock.listeners());
    }

    @Test
    public void unitClock() {
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
    public void realWorldExamples(final String description, final int fps, final long hz) {
        final FPSClock clock = new FPSClock(hz, fps);
        final CountingClockWatcher watcher = new CountingClockWatcher();

        clock.addListener(watcher);
        for (int frame = 0; frame < fps; frame++) {
            clock.runFrame();
        }
        assertEquals(hz, watcher.ticks);
    }

    @Test
    void run() throws InterruptedException {
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
    void runWhenAlreadyRunning(){
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
    void stop() throws InterruptedException {
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
