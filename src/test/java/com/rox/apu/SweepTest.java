package com.rox.apu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SweepTest {
    private static final int ENABLED = 0x80;
    private static final int NEGATE = 0x08;

    @Test
    public void onesComplementNegateSubtractsChangeAndOne(){
        final Sweep sweep = new Sweep(true); //pulse 1
        sweep.writeControlRegister(ENABLED | NEGATE | 0x01); //shift=1

        assertEquals(49, sweep.clockHalfFrame(100)); //100 - (100>>1) - 1
    }

    @Test
    public void twosComplementNegateSubtractsChangeOnly(){
        final Sweep sweep = new Sweep(false); //pulse 2
        sweep.writeControlRegister(ENABLED | NEGATE | 0x01); //shift=1

        assertEquals(50, sweep.clockHalfFrame(100)); //100 - (100>>1)
    }

    @Test
    public void positiveChangeWhenNotNegating(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(ENABLED | 0x01); //shift=1, no negate

        assertEquals(150, sweep.clockHalfFrame(100)); //100 + (100>>1)
    }

    @Test
    public void muteConditionsAreIndependentOfEnabledFlag(){
        final Sweep sweep = new Sweep(true); //never enabled

        assertTrue(sweep.isMuted(7), "below the minimum unmuted period");
        assertTrue(sweep.isMuted(1024), "default shift=0 doubles the period, pushing it out of range");
        assertFalse(sweep.isMuted(100), "in range despite the sweep never being enabled");
    }

    @Test
    public void periodOfExactlyMinimumUnmutedIsNotMuted(){
        final Sweep sweep = new Sweep(true); //shift=0 default -> target=16, well in range

        assertFalse(sweep.isMuted(8), "8 is the minimum unmuted period, only below it should mute");
    }

    @Test
    public void targetPeriodOfExactlyMaximumUnmutedIsNotMuted(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(0x01); //shift=1, no negate, not enabled (isMuted ignores enabled)

        assertFalse(sweep.isMuted(1365), "1365 + (1365>>1) == 2047, the maximum unmuted target period");
    }

    @Test
    public void clockHalfFrameDoesNotAdjustWhenShiftIsZero(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(ENABLED); //shift=0

        assertEquals(100, sweep.clockHalfFrame(100));
    }

    @Test
    public void clockHalfFrameDoesNotAdjustWhenNotEnabled(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(0x01); //shift=1, enabled bit not set

        assertEquals(100, sweep.clockHalfFrame(100));
    }

    @Test
    public void clockHalfFrameDoesNotAdjustWhenMuted(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(ENABLED | 0x01); //shift=1

        assertEquals(5, sweep.clockHalfFrame(5)); //below minimum unmuted period
    }

    @Test
    public void dividerGatesAdjustmentToOncePerPeriodReloadCycle(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(ENABLED | 0x20 | 0x01); //period reload=2 (bits4-6), shift=1

        assertEquals(150, sweep.clockHalfFrame(100), "divider starts at 0, so this clock adjusts immediately");
        assertEquals(150, sweep.clockHalfFrame(150), "divider now counting down from the reload value, no adjustment yet");
        assertEquals(150, sweep.clockHalfFrame(150), "still counting down");
        assertEquals(225, sweep.clockHalfFrame(150), "divider reached 0 again, adjustment applies");
    }

    @Test
    public void writingRegisterForcesDividerReloadOnNextClockWithoutAnAdjustment(){
        final Sweep sweep = new Sweep(true);
        sweep.writeControlRegister(ENABLED | 0x50 | 0x01); //period reload=5, shift=1

        assertEquals(150, sweep.clockHalfFrame(100), "divider starts at 0, adjusts immediately");
        assertEquals(150, sweep.clockHalfFrame(150), "divider counting down from 5, no adjustment");

        sweep.writeControlRegister(ENABLED | 0x50 | 0x01); //re-write mid-countdown, sets the reload flag

        assertEquals(150, sweep.clockHalfFrame(150),
                "reload flag forces the divider to reset here, but doesn't itself trigger an adjustment");
    }
}
