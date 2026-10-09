package com.rox.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class AudioOutputTest {
    /** Outputs that don't override pause()/resume() (e.g. ones with nothing buffered to hold back) must accept them as no-ops. */
    @Test
    public void pauseAndResumeDefaultToNoOps(){
        final AudioOutput bareOutput = new AudioOutput(){
            @Override public void start(){ }
            @Override public void write(final double sample){ }
            @Override public void stop(){ }
        };

        assertDoesNotThrow(bareOutput::pause);
        assertDoesNotThrow(bareOutput::resume);
    }
}
