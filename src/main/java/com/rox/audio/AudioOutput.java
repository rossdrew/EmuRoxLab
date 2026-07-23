package com.rox.audio;

/**
 * A destination for resampled audio, decoupled from any particular playback backend so it can be
 * mocked in tests. Each sample is a single ~0.0-1.0 value from the {@link Resampler} - conversion
 * to whatever format the backend actually needs (e.g. PCM16) is the implementation's job, not the
 * caller's.
 */
public interface AudioOutput {
    void start();

    void write(double sample);

    void stop();
}
