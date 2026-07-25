package com.rox.apu;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Negative;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ParityCountdownFrequencyDividerTest {

    @Property
    public void rejectNegativePeriodsInCreation(@ForAll @Negative int negativePeriods){
        assertThrows(IllegalArgumentException.class, () -> {
                    new ParityCountdownFrequencyDivider(mock(Runnable.class), false, negativePeriods);
                }
        );
    }

    @Test
    public void withInitialParityGateFalseTheFirstCallRunsTheActionImmediately(){
        final Runnable action = mock(Runnable.class);
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(action, false, 0);

        runner.tick();

        verify(action, times(1)).run();
    }

    @Test
    public void withInitialParityGateTrueTheFirstCallSkipsTheAction(){
        final Runnable action = mock(Runnable.class);
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(action, true, 0);

        runner.tick();

        verify(action, never()).run();
    }

    @Test
    public void actionRunsOnEveryOtherCallThereafter(){
        final Runnable action = mock(Runnable.class);
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(action, false, 0);

        runner.tick(); //1st: runs (countdown starts at 0)
        runner.tick(); //2nd: skipped
        runner.tick(); //3rd: runs
        runner.tick(); //4th: skipped

        verify(action, times(2)).run();
    }

    @Test
    public void actionRunsOncePerFullCounterPeriodOfActiveCalls(){
        final Runnable action = mock(Runnable.class);
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(action, false, 2);

        for (int i = 0; i < 6; i++){
            runner.tick();
        }
        verify(action, times(1)).run();

        runner.tick(); //7th call: the 4th active call, countdown reaches 0 again -> 2nd run
        verify(action, times(2)).run();
    }

    @Property
    public void rejectChangingToNegativePeriods(@ForAll @Negative int negativePeriods){
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(() -> { }, false, 5);

        assertEquals(5, runner.getCounterPeriod());

        assertThrows(IllegalArgumentException.class, () -> runner.setCounterPeriod(negativePeriods));
    }

    @Test
    public void counterPeriodCanBeReadAndUpdated(){
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(() -> { }, false, 5);

        assertEquals(5, runner.getCounterPeriod());

        runner.setCounterPeriod(9);

        assertEquals(9, runner.getCounterPeriod());
    }

    @Test
    public void changingCounterPeriodMidCountdownOnlyTakesEffectOnTheNextReload(){
        final Runnable action = mock(Runnable.class);
        final ParityCountdownFrequencyDivider runner = new ParityCountdownFrequencyDivider(action, false, 5);

        runner.tick(); //active: countdown 0->5 (reload), action runs (1st)
        runner.tick(); //skipped
        runner.tick(); //active: countdown 5->4

        runner.setCounterPeriod(0); //changing the period mid-countdown must not touch the in-flight countdown

        runner.tick(); //skipped
        runner.tick(); //active: countdown 4->3, unaffected by the new period until the next reload

        verify(action, times(1)).run();
    }
}
