package com.rox.audio;

import java.util.OptionalDouble;

/**
 * Downsamples a per-input-cycle stream of audio samples (e.g. the APU's per-CPU-cycle mixed
 * output, ~1.79MHz) to a lower output rate (e.g. 44100Hz) by averaging each window of input
 * samples into one output sample (a box filter).
 *
 * The input:output ratio is rarely a whole number (1,789,773 / 44,100 ≈ 40.61), so a fixed
 * window size would drift the output rate over time. Instead this mirrors
 * {@link com.rox.clock.FPSClock#ticksThisFrame()}'s fractional-remainder accumulation: window
 * sizes are mostly {@code inputRate / outputRate} input samples, with an extra sample folded in
 * periodically so the long-run average window size is exactly {@code inputRate / outputRate}
 * (kept exact, not just close, the same way {@code FPSClock} keeps its tick rate exact).
 */
public class Resampler {
    private final long baseWindowSize;
    private final long outputRate;
    private final long windowSizeRemainderPerOutputSample;

    private long windowRemainderBuffer;
    private long samplesNeededForCurrentWindow;

    private double accumulatedSum;
    private long accumulatedSampleCount;

    public Resampler(final long inputRate, final long outputRate){
        this.outputRate = outputRate;
        this.baseWindowSize = inputRate / outputRate;
        this.windowSizeRemainderPerOutputSample = inputRate % outputRate;
        this.samplesNeededForCurrentWindow = nextWindowSize();
    }

    /**
     * Accepts one input-rate sample. Returns the averaged output sample once a full window has
     * been accumulated, otherwise empty.
     */
    public OptionalDouble accept(final double sample){
        accumulatedSum += sample;
        accumulatedSampleCount++;

        if (accumulatedSampleCount < samplesNeededForCurrentWindow){
            return OptionalDouble.empty();
        }

        final double average = accumulatedSum / accumulatedSampleCount;
        accumulatedSum = 0;
        accumulatedSampleCount = 0;
        samplesNeededForCurrentWindow = nextWindowSize();
        return OptionalDouble.of(average);
    }

    private long nextWindowSize(){
        long windowSize = baseWindowSize;
        windowRemainderBuffer += windowSizeRemainderPerOutputSample;
        if (windowRemainderBuffer >= outputRate){
            windowSize++;
            windowRemainderBuffer -= outputRate;
        }
        return windowSize;
    }
}
