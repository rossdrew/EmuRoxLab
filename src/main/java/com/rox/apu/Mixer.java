package com.rox.apu;

/**
 * Combines the five NES APU channel outputs into a single sample (~0.0-1.0).
 *
 * <p>Real NES hardware doesn't mix its 5 channels with a simple average, and it doesn't mix them
 * all together in one place either. Two separate analog resistor networks on the chip each sum a
 * subset of the channels, and each network's output voltage is a <em>non-linear</em> function of
 * its inputs (a consequence of the physical circuit, not a deliberate audio design choice):
 * <ul>
 *     <li>one network sums {@code pulse1} + {@code pulse2} ({@link #pulseOut});</li>
 *     <li>the other sums {@code triangle} + {@code noise} + {@code dmc} - commonly abbreviated
 *     "TND" in NES documentation and emulator source - ({@link #tndOut}).</li>
 * </ul>
 * The two formulas below are curve-fits (reverse-engineered from real hardware measurements) for
 * those two circuits' voltage output, not something derived from first principles - which is why
 * their constants (95.88, 8128, 8227, 12241, 22638, ...) don't individually mean anything; they're
 * just the coefficients that reproduce the measured curves. {@code output} is simply the sum of
 * both circuits' voltages, since on real hardware that's the final combined analog signal.
 *
 * <p>{@code pulse1}/{@code pulse2}/{@code triangle}/{@code noise} are 0-15, {@code dmc} is 0-127.
 *
 * <p>Deliberately not precomputing a single lookup table for {@link #tndOut} keyed by a combined
 * index like {@code 3*triangle + 2*noise + dmc} (a common optimisation in other NES emulators):
 * that collapses three independently-weighted terms (divisors 8227/12241/22638 - not proportional
 * to 3:2:1) into one dimension, which is only an approximation of the real circuit's curve, not an
 * exact optimisation of it.
 *
 * <p>{@link #mix} is called on every CPU cycle, so both circuits are precomputed instead - but exactly: one
 * entry per possible input (31 pulse sums; every triangle/noise/dmc combination, 16 x 16 x 128), each
 * computed by the same formula as before, so a lookup returns the identical double.
 */
public final class Mixer {
    /** Curve-fit coefficients for the pulse1+pulse2 summing circuit's output voltage. */
    private static final double PULSE_CIRCUIT_NUMERATOR = 95.88;
    private static final double PULSE_CIRCUIT_DIVISOR_CONSTANT = 8128.0;
    private static final double PULSE_CIRCUIT_OFFSET = 100.0;

    /** Curve-fit coefficients for the triangle+noise+dmc ("TND") summing circuit's output voltage. */
    private static final double TND_CIRCUIT_NUMERATOR = 159.79;
    private static final double TRIANGLE_CONTRIBUTION_DIVISOR = 8227.0;
    private static final double NOISE_CONTRIBUTION_DIVISOR = 12241.0;
    private static final double DMC_CONTRIBUTION_DIVISOR = 22638.0;
    private static final double TND_CIRCUIT_OFFSET = 100.0;

    private static final int FOUR_BIT_VALUES = 16;
    private static final int SEVEN_BIT_VALUES = 128;
    private static final double[] PULSE_OUT = new double[2 * FOUR_BIT_VALUES - 1];
    private static final double[] TND_OUT = new double[FOUR_BIT_VALUES * FOUR_BIT_VALUES * SEVEN_BIT_VALUES];

    static {
        for (int sum = 0; sum < PULSE_OUT.length; sum++){
            PULSE_OUT[sum] = pulseOut(sum);
        }
        for (int triangle = 0; triangle < FOUR_BIT_VALUES; triangle++){
            for (int noise = 0; noise < FOUR_BIT_VALUES; noise++){
                for (int dmc = 0; dmc < SEVEN_BIT_VALUES; dmc++){
                    TND_OUT[tndIndex(triangle, noise, dmc)] = tndOut(triangle, noise, dmc);
                }
            }
        }
    }

    private Mixer(){
        /**
         * XXX Code coverage fails us here. This constructors only job is to make `new Mixer()` a compile error,
         * since a utility class with only static methods being instantiable is meaningless. If I remove it,
         * Java auto-generates a public no-arg constructor, so `new Mixer()` becomes legal (harmless, but there's
         * no longer anything stopping it).
         */
    }

    /** The combined analog output voltage of both summing circuits, as they'd appear on real hardware. */
    public static double mix(final int pulse1,
                             final int pulse2,
                             final int triangle,
                             final int noise,
                             final int dmc){
        return PULSE_OUT[pulse1 + pulse2] + TND_OUT[tndIndex(triangle, noise, dmc)];
    }

    private static int tndIndex(final int triangle, final int noise, final int dmc){
        return (triangle * FOUR_BIT_VALUES + noise) * SEVEN_BIT_VALUES + dmc;
    }

    /** The pulse1+pulse2 summing circuit's output voltage for {@code pulse1 + pulse2} (0 when both are silent). */
    private static double pulseOut(final int sum){
        if (sum == 0){
            return 0;
        }
        return PULSE_CIRCUIT_NUMERATOR / (PULSE_CIRCUIT_DIVISOR_CONSTANT / sum + PULSE_CIRCUIT_OFFSET);
    }

    /** The triangle+noise+dmc ("TND") summing circuit's output voltage (0 when all three are silent). */
    private static double tndOut(final int triangle, final int noise, final int dmc){
        if (triangle == 0 && noise == 0 && dmc == 0){
            return 0;
        }
        final double combinedContribution = triangle / TRIANGLE_CONTRIBUTION_DIVISOR
                + noise / NOISE_CONTRIBUTION_DIVISOR
                + dmc / DMC_CONTRIBUTION_DIVISOR;
        return TND_CIRCUIT_NUMERATOR / (1.0 / combinedContribution + TND_CIRCUIT_OFFSET);
    }
}
