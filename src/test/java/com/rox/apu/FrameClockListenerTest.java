package com.rox.apu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FrameClockListenerTest {

    @Test
    public void defaultMethodsAreSafeNoOps(){
        final FrameClockListener listener = new FrameClockListener() {};

        assertDoesNotThrow(listener::quarterFrameClock);
        assertDoesNotThrow(listener::halfFrameClock);
    }
}
