package com.rox.input;

import de.gurkenlabs.input4j.InputDevice;

import java.util.Map;

/**
 * A {@link Controller} driven by a real physical gamepad via input4j's {@link InputDevice}. Unlike
 * {@link KeyboardController} (event-driven from the EDT), a gamepad has no push-based events - it must
 * be actively polled, which {@link #poll()} does once per emulated video frame (see {@code NES}'s
 * frame-ready clock listener), not once per {@code $4016}/{@code $4017} read - polling a real device is
 * comparatively expensive, and the NES only ever needs a snapshot from the start of the frame.
 *
 * {@link #resolvePressedMask} is deliberately extracted as a pure, directly-testable seam: it only
 * reads already-polled {@link de.gurkenlabs.input4j.InputComponent} data (plain Java, no native calls),
 * so - unlike {@code SpeakerAudioOutput}'s real hardware line - nothing about this class actually needs
 * excluding from coverage; {@link InputDevice#poll()} itself is exercised in tests via a fake,
 * injectable poll callback (matching how {@code InputDevice} was designed to be constructed).
 */
public final class GamepadController implements Controller {
    private final InputDevice device;
    private final Map<Button, GamepadBinding> bindings;
    //written only by poll() (the frame-ready clock listener thread), read only by isPressed() (the
    //CPU thread during $4016/$4017 reads) - mirrors KeyboardController's own single-writer/single-reader
    //volatile mask, just with a different pair of threads
    private volatile int pressedMask;

    public GamepadController(final InputDevice device, final Map<Button, GamepadBinding> bindings){
        this.device = device;
        this.bindings = bindings;
    }

    @Override
    public void poll(){
        device.poll();
        pressedMask = resolvePressedMask(bindings, device);
    }

    static int resolvePressedMask(final Map<Button, GamepadBinding> bindings, final InputDevice device){
        int mask = 0;
        for (final Map.Entry<Button, GamepadBinding> entry : bindings.entrySet()){
            if (entry.getValue().isPressed(device)){
                mask |= 1 << entry.getKey().ordinal();
            }
        }
        return mask;
    }

    @Override
    public boolean isPressed(final Button button){
        return (pressedMask & (1 << button.ordinal())) != 0;
    }
}
