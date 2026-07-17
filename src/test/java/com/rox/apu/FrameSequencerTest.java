package com.rox.apu;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;

import static net.jqwik.api.Arbitraries.integers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.apu.FrameSequencer.FIVE_STEP_END;
import static com.rox.apu.FrameSequencer.FOUR_STEP_END;
import static com.rox.apu.FrameSequencer.HALF_FRAME_1;
import static com.rox.apu.FrameSequencer.QUARTER_FRAME_1;
import static com.rox.apu.FrameSequencer.QUARTER_FRAME_2;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class FrameSequencerTest {
    private static final int FOUR_STEP_MODE = 0x00;
    private static final int FIVE_STEP_MODE = 0x80;
    private static final int IRQ_INHIBIT = 0x40;

    private FrameSequencer sequencer;
    private FrameClockListener listener;

    @Provide
    Arbitrary<Integer> tickCount(){
        return integers().between(1, 100_000);
    }

    @BeforeEach
    @BeforeTry
    public void setup(){
        sequencer = new FrameSequencer();
        listener = mock(FrameClockListener.class);
        sequencer.addListener(listener);
    }

    private void clock(final int times){
        for (int i = 0; i < times; i++){
            sequencer.clock();
        }
    }

    @Test
    public void fourStepModeFirstQuarterBoundary(){
        clock(QUARTER_FRAME_1);

        verify(listener, times(1)).quarterFrameClock();
        verify(listener, times(0)).halfFrameClock();
    }

    @Test
    public void fourStepModeHalfBoundaryAlsoFiresQuarter(){
        clock(HALF_FRAME_1);

        verify(listener, times(2)).quarterFrameClock();
        verify(listener, times(1)).halfFrameClock();
    }

    @Test
    public void fourStepModeSecondQuarterBoundary(){
        clock(QUARTER_FRAME_2);

        verify(listener, times(3)).quarterFrameClock();
        verify(listener, times(1)).halfFrameClock();
    }

    @Test
    public void fourStepModeEndBoundaryFiresHalfAndSetsIrq(){
        clock(FOUR_STEP_END);

        verify(listener, times(4)).quarterFrameClock();
        verify(listener, times(2)).halfFrameClock();
        assertTrue(sequencer.isFrameIrqPending());
    }

    @Test
    public void fourStepModeIrqNotSetWhenInhibited(){
        sequencer.writeControlRegister(FOUR_STEP_MODE | IRQ_INHIBIT);

        clock(FOUR_STEP_END);

        assertFalse(sequencer.isFrameIrqPending());
    }

    @Test
    public void fourStepModeIrqIsSetWhenNotInhibitedAfterExplicitWrite(){
        sequencer.writeControlRegister(FOUR_STEP_MODE); //explicitly clears any prior inhibit state

        clock(FOUR_STEP_END);

        assertTrue(sequencer.isFrameIrqPending());
    }

    @Test
    public void writingWithIrqInhibitSetClearsAnyAlreadyPendingFrameIrq(){
        clock(FOUR_STEP_END);
        assertTrue(sequencer.isFrameIrqPending());

        sequencer.writeControlRegister(FOUR_STEP_MODE | IRQ_INHIBIT);

        assertFalse(sequencer.isFrameIrqPending());
    }

    @Test
    public void fourStepModeWrapsAndRepeatsPeriod(){
        clock(FOUR_STEP_END);
        clock(FOUR_STEP_END);

        verify(listener, times(8)).quarterFrameClock();
        verify(listener, times(4)).halfFrameClock();
    }

    @Test
    public void fiveStepModeFirstQuarterBoundary(){
        sequencer.writeControlRegister(FIVE_STEP_MODE); //fires 1 immediate quarter+half, resets cycle to 0

        clock(QUARTER_FRAME_1);

        verify(listener, times(2)).quarterFrameClock();
        verify(listener, times(1)).halfFrameClock();
    }

    @Test
    public void fiveStepModeNoEventAtOldFourStepEndBoundary(){
        sequencer.writeControlRegister(FIVE_STEP_MODE); //fires 1 immediate quarter+half, resets cycle to 0

        clock(FOUR_STEP_END); //passes Q1, H1, Q2, but 29829 is not a boundary in 5-step mode

        verify(listener, times(4)).quarterFrameClock();
        verify(listener, times(2)).halfFrameClock();
        assertFalse(sequencer.isFrameIrqPending());
    }

    @Test
    public void fiveStepModeEndBoundaryFiresHalfButNeverIrq(){
        sequencer.writeControlRegister(FIVE_STEP_MODE); //fires 1 immediate quarter+half, resets cycle to 0

        clock(FIVE_STEP_END);

        verify(listener, times(5)).quarterFrameClock();
        verify(listener, times(3)).halfFrameClock();
        assertFalse(sequencer.isFrameIrqPending());
    }

    @Test
    public void writingFourStepModeResetsCycleWithoutExtraClock(){
        clock(1000); //safely below the first boundary at QUARTER_FRAME_1
        sequencer.writeControlRegister(FOUR_STEP_MODE);

        verifyNoMoreInteractions(listener);
    }

    @Test
    public void writingFiveStepModeFiresOneImmediateHalfAndQuarterClock(){
        sequencer.writeControlRegister(FIVE_STEP_MODE);

        verify(listener, times(1)).quarterFrameClock();
        verify(listener, times(1)).halfFrameClock();
    }

    @Test
    public void writingFourStepModeAfterFiveStepDoesNotFireExtraClock(){
        sequencer.writeControlRegister(FIVE_STEP_MODE);
        sequencer.writeControlRegister(FOUR_STEP_MODE);

        verify(listener, times(1)).quarterFrameClock();
        verify(listener, times(1)).halfFrameClock();
    }

    @Test
    public void clearFrameIrqClearsPendingFlag(){
        clock(FOUR_STEP_END);
        assertTrue(sequencer.isFrameIrqPending());

        sequencer.clearFrameIrq();

        assertFalse(sequencer.isFrameIrqPending());
    }

    @Property
    public void quarterClocksAreRoughlyTwiceHalfClocks(@ForAll("tickCount") int tickCount){
        final int[] counts = new int[2];
        sequencer.addListener(new FrameClockListener(){
            @Override public void quarterFrameClock(){ counts[0]++; }
            @Override public void halfFrameClock(){ counts[1]++; }
        });

        for (int i = 0; i < tickCount; i++){
            sequencer.clock();
        }

        assertTrue(Math.abs(counts[0] - 2 * counts[1]) <= 1,
                "quarter=" + counts[0] + " half=" + counts[1] + " should differ from a strict 2:1 ratio by at most 1");
    }
}
