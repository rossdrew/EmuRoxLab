package com.rox.input;

/**
 * One NES-style gamepad's live button state, decoupled from any particular input backend (keyboard,
 * physical gamepad, or none) - mirrors {@link com.rox.audio.AudioOutput}/{@link com.rox.video.VideoOutput}'s
 * role for their own pluggable-device layers.
 */
public interface Controller {
    /** Never pressed - the default for an unconfigured/absent controller slot. */
    Controller NONE = button -> false;

    boolean isPressed(Button button);

    /**
     * Refreshes this controller's live state from its backing device. A no-op until a real polling
     * backend (a physical gamepad) needs it once per frame - cheap to add now as a default method so
     * it isn't a later breaking change to the interface.
     */
    default void poll(){
    }
}
