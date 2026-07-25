package com.rox;

import com.rox.apu.APU;
import com.rox.audio.AudioOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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

        verify(audioOutput, timeout(2000).atLeastOnce()).write(anyDouble());

        nes.powerOff();
        thread.join(2000);

        assertFalse(thread.isAlive(), "clock thread should have stopped within the join budget");
        verify(audioOutput).start();
        verify(audioOutput).stop();
    }

    /**
     * End-to-end proof that the phase-7 IRQ wiring in the constructor actually reaches the real
     * {@code MOS6502} - APUTest only proves the wiring logic in isolation against a mocked APU.
     * Drives the real clock (single-stepped via {@code tick()}, not the real-time-throttled
     * {@code run()}) directly, rather than re-deriving the listener wiring in the test itself.
     */
    @Test
    public void dmcExhaustionIrqReachesTheCpusRealIrqLine(){
        final NES nes = new NES(mock(AudioOutput.class));
        final APU apu = nes.apu();

        apu.write(0x4010, 0x80); //DMC: IRQ enable, rate index 0 (period 428)
        apu.write(0x4012, 0x00); //sample address $C000
        apu.write(0x4013, 0x00); //sample length: 1 byte
        apu.write(0x4015, 0x10); //enable DMC - starts playback, fetching the single byte immediately

        assertFalse(nes.cpu().getEnvironmentSnapshot().isIRQLineAsserted(), "not pending until that one byte's 8 bits have shifted out");

        //generously bounded poll (real requirement is ~2*(428+1)*8 ticks) rather than a hand-derived
        //exact count, so this isn't fragile against the ticker's own timing details
        final int maxTicks = 100_000;
        int ticks = 0;
        while (!nes.cpu().getEnvironmentSnapshot().isIRQLineAsserted()){
            if (++ticks > maxTicks){
                fail("DMC IRQ never reached the CPU's IRQ line within " + maxTicks + " ticks");
            }
            nes.clock().tick();
        }
    }
}
