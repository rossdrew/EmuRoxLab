package com.rox.video;

/**
 * A destination for completed frames, decoupled from any particular display backend so it can be
 * mocked in tests - mirrors {@link com.rox.audio.AudioOutput}'s role for audio. Each call is one full
 * {@code 256x240} frame of packed {@code 0xRRGGBB} colours (see {@code PPU#rgbFramebuffer()}); pacing
 * (one call per {@code PPU} frame-ready edge) and threading (called from the emulation's clock thread,
 * never the EDT) are the caller's concern, not this interface's.
 */
public interface VideoOutput {
    /** Does nothing - the default for callers that don't care about video (e.g. headless/audio-only runs). */
    VideoOutput NO_OP = rgbFrame -> { };

    void present(int[] rgbFrame);
}
