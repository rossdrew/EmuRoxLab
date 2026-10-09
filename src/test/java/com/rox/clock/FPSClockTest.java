package com.rox.clock;

import com.rox.time.Sleeper;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;
import com.rox.time.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

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

    private FPSClock clock;

    @BeforeEach
    public void setup(){
        clock = new FPSClock(1,1, new SystemTimeSource(), new ThreadSleeper());
    }

    @Test
    public void addListener(){
        clock.addListener(mock(ClockWatcher.class));
        clock.addListener(mock(ClockWatcher.class));

        assertEquals(2, clock.listeners());
    }

    @Test
    public void removeListener(){
        final ClockWatcher a = mock(ClockWatcher.class);
        final ClockWatcher b = mock(ClockWatcher.class);
        final ClockWatcher c = mock(ClockWatcher.class);
        final ClockWatcher d = mock(ClockWatcher.class);

        Arrays.asList(a, b, c, d).forEach(clock::addListener);
        assert clock.listeners() == 4 : "Test not setup properly: Expected 4 test ClockWatchers";

        clock.removeListener(b);

        assertEquals(3, clock.listeners());
    }

    @Test
    public void unitClock() {
        final ClockWatcher watcher = mock(ClockWatcher.class);
        try (FPSClock clock = new FPSClock(1,1, new SystemTimeSource(), new ThreadSleeper())){
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
        final FPSClock clock = new FPSClock(hz, fps, new SystemTimeSource(), new ThreadSleeper());
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
        try (FPSClock clock = new FPSClock(1,1, new SystemTimeSource(), new ThreadSleeper())){
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
        try (FPSClock clock = new FPSClock(1,1, new SystemTimeSource(), new ThreadSleeper())){
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
        final ClockWatcher watcher = mock(ClockWatcher.class);
        clock.addListener(watcher);

        final Thread thread = new Thread(clock::run);
        thread.start();
        clock.stop();

        assertFalse(clock.isRunning());
    }

    @Test
    void carriesFractionalTicksAcrossFrames() {
        final FPSClock clock = new FPSClock(10, 3, new SystemTimeSource(), new ThreadSleeper());

        long frame1 = clock.ticksThisFrame();
        long frame2 = clock.ticksThisFrame();
        long frame3 = clock.ticksThisFrame();

        assertEquals(3, frame1);
        assertEquals(3, frame2);
        assertEquals(4, frame3);

        assertEquals(10, frame1 + frame2 + frame3);
    }

    @Test
    void sleepsForRemainingFrameTime() throws InterruptedException {
        final TimeSource timeSource = () -> 100L;
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        clock.throttle(0L);

        verify(sleeper).sleepFor(999_999_900L);
    }

    @Test
    void doesNotSleepWhenFrameAlreadyExceeded() throws InterruptedException {
        final TimeSource timeSource = () -> 1_000_000_001L;
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        clock.throttle(0L);

        verifyNoInteractions(sleeper);
    }

    @Test
    void throttleGracefullyHandlesThreadSleepExceptions() {
        final TimeSource timeSource = () -> 100L;
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        try {
            doThrow(new InterruptedException("Interrupted during sleep"))
                    .when(sleeper)
                    .sleepFor(anyLong());
        } catch (InterruptedException e) {
            fail("God knows how this could happen");
        }

        try {
            clock.throttle(0L);
            fail("Thread.sleep() threw an exception, expected that to be passed on");
        } catch (InterruptedException e) {
            try {
                verify(sleeper).sleepFor(999_999_900L);
            } catch (InterruptedException ex) {
                fail("God knows how this could happen");
            }
        }
    }

    /**
     * The whole point of anchoring run()'s schedule to a fixed runStartTime rather than
     * re-measuring "now" fresh each frame: if one frame's sleep overshoots its target (real
     * Thread.sleep() routinely does, by tens-hundreds of microseconds - this is what caused ~10ms/s
     * of accumulating real-time drift before this fix), the next frame's requested sleep duration
     * must shrink by roughly that same overshoot to catch back up, rather than requesting the full
     * nominal frame time again and letting the lost time compound forever.
     */
    @Test
    void runAnchorsEachFramesDeadlineToAFixedStartSoOneFramesOversleepShortensTheNextFramesSleep() throws InterruptedException {
        final long frameTimeNs = 1_000_000_000L; //FPS=1 below -> one frame is exactly 1 second
        final long oversleepNs = 50_000_000L; //frame 1 finished 50ms later than its target

        final TimeSource timeSource = mock(TimeSource.class);
        when(timeSource.nanoTime()).thenReturn(
                0L,                              //run(): runStartTime
                0L,                              //frame 1's throttle() check: right on schedule
                frameTimeNs + oversleepNs         //frame 2's throttle() check: 50ms late
        );
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        final ClockWatcher watcher = mock(ClockWatcher.class);
        //stop the clock from inside the 2nd tick() so run() performs exactly 2 frames then exits
        doAnswer(invocation -> null).doAnswer(invocation -> {
            clock.stop();
            return null;
        }).when(watcher).tick();
        clock.addListener(watcher);

        clock.run();

        verify(sleeper).sleepFor(frameTimeNs); //frame 1: right on schedule, full nominal sleep
        verify(sleeper).sleepFor(frameTimeNs - oversleepNs); //frame 2: shortened to compensate
    }

    @Test
    void runGracefullyHandlesThreadSleepExceptions() throws InterruptedException {
        final TimeSource timeSource = () -> 100L;
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        try {
            doThrow(new InterruptedException("Interrupted during sleep"))
                    .when(sleeper)
                    .sleepFor(anyLong());
        } catch (InterruptedException e) {
            fail("God knows how this could happen");
        }

        assertDoesNotThrow(clock::run);
        assertFalse(clock.isRunning());
        verify(sleeper).sleepFor(anyLong());
    }

    /** Spins (no guessed sleeps) until {@code condition} holds, failing after a generous deadline. */
    private static void awaitTrue(final BooleanSupplier condition, final String description){
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()){
            if (System.nanoTime() > deadline){
                fail("Timed out waiting for: " + description);
            }
            Thread.onSpinWait();
        }
    }

    private static void awaitBlocked(final Thread thread){
        awaitTrue(() -> thread.getState() == Thread.State.WAITING, thread.getName() + " to block");
    }

    private static void joinOrFail(final Thread thread) throws InterruptedException {
        //join() on a not-yet-started thread returns at once - some threads here are started from inside a tick
        awaitTrue(() -> thread.getState() != Thread.State.NEW, thread.getName() + " to start");
        thread.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(thread.isAlive(), thread.getName() + " should have finished");
    }

    /** An FPSClock that never really sleeps or reads the wall clock - frames run back to back. */
    private static FPSClock unthrottledClock(){
        return new FPSClock(1, 1, () -> 0L, mock(Sleeper.class));
    }

    @Test
    void pauseReturnsFalseWhenTheClockIsNotRunning(){
        assertFalse(clock.pause());
    }

    @Test
    void resumeWhenNotPausedIsHarmless(){
        assertDoesNotThrow(clock::resume);
        assertFalse(clock.isRunning());
    }

    @Test
    void pauseParksTheRunLoopUntilResumed() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final AtomicLong ticks = new AtomicLong();
        clock.addListener(ticks::incrementAndGet);
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();
        awaitTrue(() -> ticks.get() > 0, "the clock to start ticking");

        assertTrue(clock.pause());
        awaitBlocked(runThread);
        final long ticksWhilePaused = ticks.get();
        assertTrue(clock.isRunning(), "pausing must not end run()");
        assertEquals(Thread.State.WAITING, runThread.getState());
        assertEquals(ticksWhilePaused, ticks.get(), "no ticks while parked");

        clock.resume();
        awaitTrue(() -> ticks.get() > ticksWhilePaused, "ticking to carry on after resume()");

        clock.stop();
        joinOrFail(runThread);
    }

    @Test
    void pauseWhileAlreadyPausedReturnsTrueStraightAway() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();
        awaitTrue(clock::isRunning, "the clock to start");

        assertTrue(clock.pause());
        assertTrue(clock.pause());

        clock.stop();
        joinOrFail(runThread);
    }

    @Test
    void pauseBlocksUntilTheCurrentFrameHasFinished() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final AtomicBoolean pauseReturned = new AtomicBoolean();
        final AtomicBoolean pauseReturnedMidFrame = new AtomicBoolean();
        final Thread pauser = new Thread(() -> {
            clock.pause();
            pauseReturned.set(true);
        }, "pauser");
        final AtomicBoolean firstTick = new AtomicBoolean(true);
        clock.addListener(() -> {
            if (firstTick.getAndSet(false)){
                pauser.start();
                awaitBlocked(pauser); //pause() is now waiting, while this frame is still mid-tick
                pauseReturnedMidFrame.set(pauseReturned.get());
            }
        });
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();

        joinOrFail(pauser);
        assertFalse(pauseReturnedMidFrame.get(), "pause() must not return while a frame is still ticking");
        assertTrue(pauseReturned.get());

        clock.stop();
        joinOrFail(runThread);
    }

    @Test
    void resumeReAnchorsTheScheduleInsteadOfCatchingUpOnThePausedTime() throws InterruptedException {
        final long frameTimeNs = 1_000_000_000L; //FPS=1 below -> one frame is exactly 1 second
        final long pausedUntilNs = 5 * frameTimeNs; //resumed 5 frames' worth later than the schedule expects
        final TimeSource timeSource = mock(TimeSource.class);
        when(timeSource.nanoTime()).thenReturn(
                0L,             //run(): runStartTime
                pausedUntilNs,  //re-anchor on resume
                pausedUntilNs   //frame 2's throttle() check
        );
        final Sleeper sleeper = mock(Sleeper.class);
        final FPSClock clock = new FPSClock(1, 1, timeSource, sleeper);

        final Thread pauser = new Thread(() -> {
            if (clock.pause()){
                clock.resume();
            }
        }, "pauser");
        final AtomicLong ticks = new AtomicLong();
        clock.addListener(() -> {
            final long tick = ticks.incrementAndGet();
            if (tick == 1){
                pauser.start();
                awaitBlocked(pauser); //frame 1 then parks, and the pauser resumes it straight away
            } else {
                clock.stop(); //frame 2 is the last
            }
        });

        clock.run();
        joinOrFail(pauser);

        //without re-anchoring, frame 2 would be 4 frames behind schedule and skip its sleep entirely
        verify(sleeper).sleepFor(frameTimeNs);
    }

    @Test
    void stopReleasesAParkedRunLoop() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();
        awaitTrue(clock::isRunning, "the clock to start");
        assertTrue(clock.pause());

        clock.stop();

        joinOrFail(runThread);
        assertFalse(clock.isRunning());
    }

    @Test
    void stopReleasesAPauseStillWaitingForTheFrameToFinish() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final CountDownLatch finishFrame = new CountDownLatch(1);
        final AtomicBoolean pauseResult = new AtomicBoolean(true);
        final Thread pauser = new Thread(() -> pauseResult.set(clock.pause()), "pauser");
        final AtomicBoolean firstTick = new AtomicBoolean(true);
        clock.addListener(() -> {
            if (firstTick.getAndSet(false)){
                pauser.start();
                awaitBlocked(pauser);
                clock.stop(); //stopped before the frame - and so the park - ever finishes
                try {
                    finishFrame.await();
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                }
            }
        });
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();

        joinOrFail(pauser);
        assertFalse(pauseResult.get(), "a pause() overtaken by stop() never parked anything");

        finishFrame.countDown();
        joinOrFail(runThread);
    }

    @Test
    void runInterruptedWhileParkedEndsCleanlyWithoutLeavingAStalePauseForTheNextRun() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final AtomicLong ticks = new AtomicLong();
        clock.addListener(ticks::incrementAndGet);
        final Thread firstRun = new Thread(clock::run, "clock-run-1");
        firstRun.start();
        awaitTrue(clock::isRunning, "the clock to start");
        assertTrue(clock.pause());
        awaitBlocked(firstRun);

        firstRun.interrupt();
        joinOrFail(firstRun);
        assertFalse(clock.isRunning());

        final long ticksBeforeSecondRun = ticks.get();
        final Thread secondRun = new Thread(clock::run, "clock-run-2");
        secondRun.start();
        awaitTrue(() -> ticks.get() > ticksBeforeSecondRun + 1, "a fresh run() to keep ticking rather than park");

        clock.stop();
        joinOrFail(secondRun);
    }

    @Test
    void pauseWaitsOutAnInterruptRatherThanReturningBeforeTheLoopHasParked() throws InterruptedException {
        final FPSClock clock = unthrottledClock();
        final AtomicBoolean pauseResult = new AtomicBoolean();
        final AtomicBoolean interruptFlagRestored = new AtomicBoolean();
        final Thread pauser = new Thread(() -> {
            pauseResult.set(clock.pause());
            interruptFlagRestored.set(Thread.currentThread().isInterrupted());
        }, "pauser");
        final AtomicBoolean firstTick = new AtomicBoolean(true);
        clock.addListener(() -> {
            if (firstTick.getAndSet(false)){
                pauser.start();
                awaitBlocked(pauser);
                pauser.interrupt();
                //the interrupted pause() must go straight back to waiting on this still-unfinished frame
                awaitTrue(() -> pauser.getState() == Thread.State.WAITING && !pauser.isInterrupted(), "pauser to resume waiting");
            }
        });
        final Thread runThread = new Thread(clock::run, "clock-run");
        runThread.start();

        joinOrFail(pauser);
        assertTrue(pauseResult.get(), "the loop did park, interrupt or not");
        assertTrue(interruptFlagRestored.get(), "the swallowed interrupt must be re-asserted for the caller");

        clock.stop();
        joinOrFail(runThread);
    }

    /** {@link Clock}'s defaults, for clocks with no run loop to pause (e.g. test doubles). */
    @Test
    void clockDefaultsHaveNothingToPause(){
        final Clock bareClock = new Clock(){
            @Override public void addListener(final ClockWatcher listener){ }
            @Override public void removeListener(final ClockWatcher listener){ }
            @Override public void tick(){ }
            @Override public int listeners(){ return 0; }
            @Override public void run(){ }
            @Override public void stop(){ }
            @Override public boolean isRunning(){ return false; }
        };

        assertFalse(bareClock.pause());
        assertDoesNotThrow(bareClock::resume);
    }
}
