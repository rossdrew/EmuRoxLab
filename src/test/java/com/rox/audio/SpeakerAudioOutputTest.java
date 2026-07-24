package com.rox.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import javax.sound.sampled.SourceDataLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SpeakerAudioOutputTest {
    private SourceDataLine line;
    private SpeakerAudioOutput output;

    @BeforeEach
    public void setup(){
        line = mock(SourceDataLine.class);
        output = new SpeakerAudioOutput(line);
    }

    @AfterEach
    public void teardown(){
        output.stop(); //don't leak the writer thread across tests
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0",       //true silence (e.g. Mixer.mix(0,0,0,0,0)) -> PCM 0, not some centered baseline
            "1.0, 32767",   //top of the range -> max positive PCM16
            "0.5, 16384",   //halfway up the range
            "0.25, 8192",   //quarter-way up the range
            "0.75, 24575",  //three-quarters up the range
    })
    public void toPcm16ScalesTheUnipolarAnalogRangeDirectlyOntoSignedPcm16(final double sample, final short expectedPcm){
        assertEquals(expectedPcm, SpeakerAudioOutput.toPcm16(sample));
    }

    @Test
    public void toPcm16MapsZeroInputToDigitalSilence(){
        //the specific regression this guards: silence must be PCM 0, not a large non-zero DC value
        assertEquals((short) 0, SpeakerAudioOutput.toPcm16(0.0));
    }

    @Test
    public void toPcm16ClampsInputsAboveTheExpectedRange(){
        assertEquals(Short.MAX_VALUE, SpeakerAudioOutput.toPcm16(1.5));
    }

    @Test
    public void toPcm16ClampsInputsBelowTheExpectedRange(){
        assertEquals(Short.MIN_VALUE, SpeakerAudioOutput.toPcm16(-1.5));
    }

    @Test
    public void startOnlyStartsTheLineOnce(){
        output.start();
        output.start();

        verify(line, times(1)).start();
    }

    @Test
    public void stopWithoutStartDoesNotTouchTheLine(){
        output.stop();

        verify(line, never()).stop();
        verify(line, never()).close();
    }

    @Test
    public void stopAfterStartStopsAndClosesTheLineOnce(){
        output.start();

        output.stop();
        output.stop();

        verify(line, times(1)).stop();
        verify(line, times(1)).close();
    }

    @Test
    public void writingBeyondBufferCapacityDropsTheOldestSampleNotTheNewest(){
        output.write(1.0); //PCM 32767 - the oldest sample, should be the one dropped
        for (int i = 0; i < SpeakerAudioOutput.RING_BUFFER_CAPACITY; i++){
            output.write(0.0); //PCM 0, fills the buffer exactly full and overflows it by one
        }

        output.start();

        final ArgumentCaptor<byte[]> frame = ArgumentCaptor.forClass(byte[].class);
        verify(line, timeout(1000).atLeastOnce()).write(frame.capture(), anyInt(), anyInt());
        final byte[] firstFrame = frame.getAllValues().get(0);
        final short firstDeliveredSample = (short) ((firstFrame[1] << 8) | (firstFrame[0] & 0xFF));
        assertEquals((short) 0, firstDeliveredSample, "the oldest sample (1.0) should have been dropped, not the newest");
    }

    @Test
    public void queuedSamplesBelowChunkSizeAreDeliveredInOneWriteCall(){
        //regression guard for the click/pop bug: writing one sample per line.write() call risks
        //underrunning the hardware buffer, so queued samples must be batched, not written one at a time
        final int sampleCount = 100;
        for (int i = 0; i < sampleCount; i++){
            output.write(1.0);
        }

        output.start();

        verify(line, timeout(1000).times(1)).write(any(byte[].class), eq(0), eq(sampleCount * 2));
    }

    @Test
    public void queuedSamplesAboveChunkSizeAreSplitAcrossMultipleCappedWriteCalls(){
        final int overflow = 100;
        final int sampleCount = SpeakerAudioOutput.WRITE_CHUNK_SAMPLES + overflow;
        for (int i = 0; i < sampleCount; i++){
            output.write(1.0);
        }

        output.start();

        verify(line, timeout(1000)).write(any(byte[].class), eq(0), eq(SpeakerAudioOutput.WRITE_CHUNK_SAMPLES * 2));
        verify(line, timeout(1000)).write(any(byte[].class), eq(0), eq(overflow * 2));
    }

    @Test
    public void writtenSampleReachesTheLineAsLittleEndianPcm16(){
        output.start();

        output.write(1.0); //-> PCM16 32767 -> 0x7FFF -> little-endian bytes {0xFF, 0x7F}

        final ArgumentCaptor<byte[]> frame = ArgumentCaptor.forClass(byte[].class);
        verify(line, timeout(1000)).write(frame.capture(), anyInt(), anyInt());
        assertEquals((byte) 0xFF, frame.getValue()[0]);
        assertEquals((byte) 0x7F, frame.getValue()[1]);
    }
}
