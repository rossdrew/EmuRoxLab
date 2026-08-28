package com.rox.input;

/**
 * The 8 buttons of a standard NES controller, in the exact order the hardware shift register reads
 * them out in ({@link ControllerPort}'s latch) - never reorder, {@link #ordinal()} IS the
 * shift-register bit position.
 */
public enum Button {
    A, B, SELECT, START, UP, DOWN, LEFT, RIGHT
}
