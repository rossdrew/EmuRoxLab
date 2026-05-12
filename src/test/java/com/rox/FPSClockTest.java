package com.rox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class FPSClockTest {
    @Test
    public void testUnitClock() {
        final FPSClock clock = new FPSClock(1,1);

        //TODO add watcher
        //TODO start
        //TODO validate 1 click
    }

    //TODO run
    //TODO run when already running
    //TODO stop
    
    @Test
    void carriesFractionalTicksAcrossFrames() {
        FPSClock clock = new FPSClock(10, 3);

        long frame1 = clock.ticksThisFrame();
        long frame2 = clock.ticksThisFrame();
        long frame3 = clock.ticksThisFrame();

        assertEquals(3, frame1);
        assertEquals(3, frame2);
        assertEquals(4, frame3);

        assertEquals(10, frame1 + frame2 + frame3);
    }
}
