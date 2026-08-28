package com.rox.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * A {@link Controller} driven by real keyboard input via a configurable AWT keycode→{@link Button}
 * mapping. Register on a video output's key listener (e.g. {@code SwingVideoOutput.addKeyListener}) to
 * receive key events.
 *
 * {@code keyPressed}/{@code keyReleased} run on the EDT, while {@link #isPressed} is polled from the
 * emulation's clock thread - a single {@code volatile int} bitmask is enough to make that safe (the EDT
 * is the sole writer, the clock thread the sole reader, so no read-modify-write race is possible).
 */
public class KeyboardController extends KeyAdapter implements Controller {
    private final Map<Integer, Button> keyBindings;
    private volatile int pressedMask;

    public KeyboardController(final Map<Integer, Button> keyBindings){
        this.keyBindings = keyBindings;
    }

    @Override
    public void keyPressed(final KeyEvent e){
        final Button button = keyBindings.get(e.getKeyCode());
        if (button != null){
            pressedMask |= 1 << button.ordinal();
        }
    }

    @Override
    public void keyReleased(final KeyEvent e){
        final Button button = keyBindings.get(e.getKeyCode());
        if (button != null){
            pressedMask &= ~(1 << button.ordinal());
        }
    }

    @Override
    public boolean isPressed(final Button button){
        return (pressedMask & (1 << button.ordinal())) != 0;
    }
}
