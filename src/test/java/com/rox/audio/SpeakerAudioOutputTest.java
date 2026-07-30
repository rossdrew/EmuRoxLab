package com.rox.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import javax.sound.sampled.SourceDataLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    public void writtenSampleReachesTheLineAsLittleEndianPcm16(){
        output.start();

        output.write(1.0); //-> PCM16 32767 -> 0x7FFF -> little-endian bytes {0xFF, 0x7F}

        final ArgumentCaptor<byte[]> frame = ArgumentCaptor.forClass(byte[].class);
        verify(line, timeout(1000)).write(frame.capture(), anyInt(), anyInt());
        assertEquals((byte) 0xFF, frame.getValue()[0]);
        assertEquals((byte) 0x7F, frame.getValue()[1]);
    }

    /**
     * The writer thread waits for a full WRITE_CHUNK_SAMPLES batch when it can (real per-call
     * overhead/GC churn from draining 1-3 samples at a time was itself a source of audible
     * underruns), but a partial batch that never reaches that size must still get flushed rather
     * than waiting forever - bounded by PARTIAL_BATCH_TIMEOUT_MILLIS. This checks the flush happens
     * in that bounded window: not instantly (which would mean the batching was defeated again) and
     * not never (a regression to unbounded waiting).
     */
    @Test
    public void aPartialBatchIsFlushedAfterABoundedWaitRatherThanInstantlyOrNever(){
        final long startNanos = System.nanoTime();
        output.start();
        output.write(1.0); //a single sample - nowhere near a full WRITE_CHUNK_SAMPLES batch

        final ArgumentCaptor<byte[]> frame = ArgumentCaptor.forClass(byte[].class);
        verify(line, timeout(1000)).write(frame.capture(), anyInt(), anyInt());
        final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(elapsedMillis >= 20,
                "a partial batch should wait out roughly the partial-batch timeout before flushing, not flush near-instantly, took "
                        + elapsedMillis + "ms");
    }

    /** Contrast with the above: a batch that's already full when start() is called must flush promptly, not wait out the partial-batch timeout. */
    @Test
    public void aFullBatchIsFlushedPromptlyWithoutWaitingForThePartialBatchTimeout(){
        for (int i = 0; i < SpeakerAudioOutput.WRITE_CHUNK_SAMPLES; i++){
            output.write(0.0);
        }

        final long startNanos = System.nanoTime();
        output.start();

        verify(line, timeout(200).atLeastOnce()).write(any(byte[].class), anyInt(), anyInt());
        final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(elapsedMillis < 40,
                "a full batch should flush promptly rather than waiting out the partial-batch timeout, took " + elapsedMillis + "ms");
    }
}
