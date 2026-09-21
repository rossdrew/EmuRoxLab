package com.rox.input;

import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;

/**
 * Binds one NES {@link Button} to a physical gamepad component, as resolved from a device's live
 * {@link InputComponent} data by {@link GamepadController}. A missing component (the bound device
 * doesn't actually have it) reads as not-pressed rather than throwing - the same "absent input never
 * blocks the emulator" spirit as {@link Controller#NONE}.
 */
public sealed interface GamepadBinding {
    boolean isPressed(InputDevice device);

    /**
     * A digital button component (face buttons, shoulder buttons, and - per input4j's own per-platform
     * normalization - the D-pad, which every supported backend (XInput, DirectInput, Linux evdev,
     * macOS IOKit) already exposes as 4 discrete {@code DPAD_UP/DOWN/LEFT/RIGHT} button components
     * rather than a raw POV-hat axis, confirmed by reading input4j 1.3.1's own source rather than
     * assuming from its README). Pressed whenever the component's data is non-zero.
     */
    record ButtonBinding(InputComponent.ID componentId) implements GamepadBinding {
        @Override
        public boolean isPressed(final InputDevice device){
            return device.getComponent(componentId).map(c -> c.getData() != 0).orElse(false);
        }
    }

    /**
     * An analog axis component (e.g. a stick used as a D-pad, or a trigger used as a button),
     * pressed once its value crosses {@code threshold} in the direction given by {@code positive}.
     */
    record AxisBinding(InputComponent.ID componentId, boolean positive, float threshold) implements GamepadBinding {
        @Override
        public boolean isPressed(final InputDevice device){
            return device.getComponent(componentId)
                    .map(c -> positive ? c.getData() >= threshold : c.getData() <= -threshold)
                    .orElse(false);
        }
    }
}
