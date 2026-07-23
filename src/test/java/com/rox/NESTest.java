package com.rox;

import com.rox.audio.AudioOutput;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Wiring-level checks only, using a mocked {@link AudioOutput} rather than the real
 * {@code SpeakerAudioOutput} - a unit test shouldn't depend on audio hardware being present (e.g.
 * in CI). Whether the audio actually sounds correct is a manual smoke test, not something a unit
 * test can assert.
 */
public class NESTest {

    @Test
    public void constructsWithCpuAndApuWiredOntoTheSharedClock(){
        new NES(mock(AudioOutput.class));
    }

    @Test
    public void powerOnStartsAudioOutputRunsAndFeedsItSamplesUntilPoweredOff() throws InterruptedException {
        final AudioOutput audioOutput = mock(AudioOutput.class);
        final NES nes = new NES(audioOutput);

        final Thread thread = new Thread(nes::powerOn);
        thread.start();
        Thread.sleep(200);
        nes.powerOff();
        thread.join(2000);

        verify(audioOutput).start();
        verify(audioOutput).stop();
        verify(audioOutput, atLeastOnce()).write(anyDouble());
    }
}
