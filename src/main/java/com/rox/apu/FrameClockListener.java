package com.rox.apu;

/**
 * Notified by the {@link FrameSequencer} on quarter- and half-frame boundaries. A half-frame
 * clock always implies a simultaneous quarter-frame clock.
 */
public interface FrameClockListener {
    default void quarterFrameClock(){}
    default void halfFrameClock(){}
}
