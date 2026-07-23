package com.rox.apu;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MixerTest {
    private static final double ROUNDING_NOISE_TOLERANCE = 1e-9;

    @Provide
    Arbitrary<Integer> fourBitChannel(){
        return Arbitraries.integers().between(0, 15);
    }

    @Provide
    Arbitrary<Integer> incrementableFourBitChannel(){
        return Arbitraries.integers().between(0, 14);
    }

    @Provide
    Arbitrary<Integer> dmcChannel(){
        return Arbitraries.integers().between(0, 127);
    }

    @Provide
    Arbitrary<Integer> incrementableDmcChannel(){
        return Arbitraries.integers().between(0, 126);
    }

    @Test
    public void allZeroInputsProduceZeroOutput(){
        assertEquals(0.0, Mixer.mix(0, 0, 0, 0, 0), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void maxPulseOnlyMatchesGoldenValue(){
        assertEquals(0.25848310567936733, Mixer.mix(15, 15, 0, 0, 0), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void maxTriangleNoiseAndDmcOnlyMatchesGoldenValue(){
        assertEquals(0.7415162451475782, Mixer.mix(0, 0, 15, 15, 127), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void mixedChannelsMatchGoldenValue(){
        assertEquals(0.5371131941783692, Mixer.mix(8, 3, 5, 2, 64), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void singlePulseChannelAtMaxWithoutTheOtherMatchesGoldenValue(){
        assertEquals(0.14937681761528873, Mixer.mix(15, 0, 0, 0, 0), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void dmcAloneAtMaxMatchesGoldenValue(){
        assertEquals(0.574263682155187, Mixer.mix(0, 0, 0, 0, 127), ROUNDING_NOISE_TOLERANCE);
    }

    @Test
    public void zeroPulseWithNonZeroTndIsNotSilencedByThePulseZeroCase(){
        assertTrue(Mixer.mix(0, 0, 1, 0, 0) > 0.0);
    }

    @Test
    public void zeroTndWithNonZeroPulseIsNotSilencedByTheTndZeroCase(){
        assertTrue(Mixer.mix(1, 0, 0, 0, 0) > 0.0);
    }

    @Property
    public void increasingPulse1NeverDecreasesOutput(@ForAll("incrementableFourBitChannel") final int pulse1,
                                                     @ForAll("fourBitChannel") final int pulse2,
                                                     @ForAll("fourBitChannel") final int triangle,
                                                     @ForAll("fourBitChannel") final int noise,
                                                     @ForAll("dmcChannel") final int dmc){
        assertTrue(Mixer.mix(pulse1 + 1, pulse2, triangle, noise, dmc) >= Mixer.mix(pulse1, pulse2, triangle, noise, dmc));
    }

    @Property
    public void increasingPulse2NeverDecreasesOutput(@ForAll("fourBitChannel") final int pulse1,
                                                     @ForAll("incrementableFourBitChannel") final int pulse2,
                                                     @ForAll("fourBitChannel") final int triangle,
                                                     @ForAll("fourBitChannel") final int noise,
                                                     @ForAll("dmcChannel") final int dmc){
        assertTrue(Mixer.mix(pulse1, pulse2 + 1, triangle, noise, dmc) >= Mixer.mix(pulse1, pulse2, triangle, noise, dmc));
    }

    @Property
    public void increasingTriangleNeverDecreasesOutput(@ForAll("fourBitChannel") final int pulse1,
                                                         @ForAll("fourBitChannel") final int pulse2,
                                                         @ForAll("incrementableFourBitChannel") final int triangle,
                                                         @ForAll("fourBitChannel") final int noise,
                                                         @ForAll("dmcChannel") final int dmc){
        assertTrue(Mixer.mix(pulse1, pulse2, triangle + 1, noise, dmc) >= Mixer.mix(pulse1, pulse2, triangle, noise, dmc));
    }

    @Property
    public void increasingNoiseNeverDecreasesOutput(@ForAll("fourBitChannel") final int pulse1,
                                                      @ForAll("fourBitChannel") final int pulse2,
                                                      @ForAll("fourBitChannel") final int triangle,
                                                      @ForAll("incrementableFourBitChannel") final int noise,
                                                      @ForAll("dmcChannel") final int dmc){
        assertTrue(Mixer.mix(pulse1, pulse2, triangle, noise + 1, dmc) >= Mixer.mix(pulse1, pulse2, triangle, noise, dmc));
    }

    @Property
    public void increasingDmcNeverDecreasesOutput(@ForAll("fourBitChannel") final int pulse1,
                                                    @ForAll("fourBitChannel") final int pulse2,
                                                    @ForAll("fourBitChannel") final int triangle,
                                                    @ForAll("fourBitChannel") final int noise,
                                                    @ForAll("incrementableDmcChannel") final int dmc){
        assertTrue(Mixer.mix(pulse1, pulse2, triangle, noise, dmc + 1) >= Mixer.mix(pulse1, pulse2, triangle, noise, dmc));
    }
}
