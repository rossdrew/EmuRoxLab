package com.rox.audio;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResamplerTest {
    private static final double DELTA = 1e-9;

    /** Feeds one input sample and returns the produced window size (calls to reach the next output), or -1 if never produced within the given budget. */
    private int windowSizeOf(final Resampler resampler, final double sampleValue){
        int count = 0;
        OptionalDouble result;
        do {
            result = resampler.accept(sampleValue);
            count++;
        } while (result.isEmpty() && count < 1000);
        return result.isPresent() ? count : -1;
    }

    @Test
    public void carriesFractionalWindowSizeAcrossOutputsLikeFPSClock(){
        //mirrors FPSClockTest.carriesFractionalTicksAcrossFrames: 10 input samples over 3 outputs -> 3,3,4
        final Resampler resampler = new Resampler(10, 3);

        assertEquals(3, windowSizeOf(resampler, 1.0));
        assertEquals(3, windowSizeOf(resampler, 1.0));
        assertEquals(4, windowSizeOf(resampler, 1.0));
    }

    @Test
    public void windowSizesSumExactlyToTheInputRateOverMultipleCycles(){
        //3 full cycles of the 3,3,4 pattern: 9 outputs must consume exactly 30 inputs, not "close to" 30
        final Resampler resampler = new Resampler(10, 3);

        int totalInputsConsumed = 0;
        for (int output = 0; output < 9; output++){
            totalInputsConsumed += windowSizeOf(resampler, 1.0);
        }

        assertEquals(30, totalInputsConsumed);
    }

    @Test
    public void outputIsTheAverageOfItsWindow(){
        final Resampler resampler = new Resampler(10, 3); //first window size is 3

        assertTrue(resampler.accept(2.0).isEmpty());
        assertTrue(resampler.accept(4.0).isEmpty());
        final OptionalDouble result = resampler.accept(6.0);

        assertTrue(result.isPresent());
        assertEquals(4.0, result.getAsDouble(), DELTA); //(2+4+6)/3
    }

    @Test
    public void emptyUntilTheWindowIsFull(){
        final Resampler resampler = new Resampler(10, 3);

        assertFalse(resampler.accept(1.0).isPresent());
        assertFalse(resampler.accept(1.0).isPresent());
        assertTrue(resampler.accept(1.0).isPresent());
    }

    @Test
    public void realNesRatioProducesTheExpectedAlternatingWindowSizePattern(){
        //1,789,773Hz -> 44,100Hz: base window 40, with a remainder that adds an extra sample most (but not every) other window
        final Resampler resampler = new Resampler(1_789_773, 44_100);

        final int[] expectedWindowSizes = {40, 41, 40, 41, 40, 41, 41, 40, 41, 40};
        for (final int expected : expectedWindowSizes){
            assertEquals(expected, windowSizeOf(resampler, 0.5));
        }
    }
}
