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
    private final long outputRate;
    private long baseWindowSize;
    private long windowSizeRemainderPerOutputSample;

    private long windowRemainderBuffer;
    private long samplesNeededForCurrentWindow;

    private double accumulatedSum;
    private long accumulatedSampleCount;
    private double output;

    public Resampler(final long inputRate, final long outputRate){
        checkRates(inputRate, outputRate);
        this.outputRate = outputRate;
        this.baseWindowSize = inputRate / outputRate;
        this.windowSizeRemainderPerOutputSample = inputRate % outputRate;
        this.samplesNeededForCurrentWindow = nextWindowSize();
    }

    /**
     * Retargets to a new input rate - e.g. the CPU running at double speed, so twice as many input samples
     * make up each output sample (and the sound plays an octave up). The window in progress finishes at its
     * old size; later ones use the new rate.
     *
     * @throws IllegalArgumentException as the constructor
     */
    public void setInputRate(final long inputRate){
        checkRates(inputRate, outputRate);
        baseWindowSize = inputRate / outputRate;
        windowSizeRemainderPerOutputSample = inputRate % outputRate;
    }

    private static void checkRates(final long inputRate, final long outputRate){
        if (inputRate <= 0 || outputRate <= 0){
            throw new IllegalArgumentException(
                    "inputRate and outputRate must both be positive, got inputRate=" + inputRate + ", outputRate=" + outputRate);
        }
        if (inputRate < outputRate){
            throw new IllegalArgumentException(
                    "inputRate must be >= outputRate (this is a downsampler only), got inputRate=" + inputRate + ", outputRate=" + outputRate);
        }
    }

    /**
     * Accepts one input-rate sample. Returns the averaged output sample once a full window has
     * been accumulated, otherwise empty.
     */
    public OptionalDouble accept(final double sample){
        return add(sample) ? OptionalDouble.of(output) : OptionalDouble.empty();
    }

    /**
     * {@link #accept} without the wrapper, for a caller on the per-cycle hot path: returns true when this sample
     * completed a window, whose average is then {@link #output()}.
     */
    public boolean add(final double sample){
        accumulatedSum += sample;
        accumulatedSampleCount++;

        if (accumulatedSampleCount < samplesNeededForCurrentWindow){
            return false;
        }

        output = accumulatedSum / accumulatedSampleCount;
        accumulatedSum = 0;
        accumulatedSampleCount = 0;
        samplesNeededForCurrentWindow = nextWindowSize();
        return true;
    }

    /** The output sample from the last {@link #add} that returned true. */
    public double output(){
        return output;
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
