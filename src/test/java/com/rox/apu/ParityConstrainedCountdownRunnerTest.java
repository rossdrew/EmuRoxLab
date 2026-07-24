package com.rox.apu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ParityConstrainedCountdownRunnerTest {

    @Test
    public void withInitialParityGateFalseTheFirstCallRunsTheActionImmediately(){
        final Runnable action = mock(Runnable.class);
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(action, false, 0);

        runner.run();

        verify(action, times(1)).run();
    }

    @Test
    public void withInitialParityGateTrueTheFirstCallSkipsTheAction(){
        final Runnable action = mock(Runnable.class);
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(action, true, 0);

        runner.run();

        verify(action, never()).run();
    }

    @Test
    public void actionRunsOnEveryOtherCallThereafter(){
        final Runnable action = mock(Runnable.class);
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(action, false, 0);

        runner.run(); //1st: runs (countdown starts at 0)
        runner.run(); //2nd: skipped
        runner.run(); //3rd: runs
        runner.run(); //4th: skipped

        verify(action, times(2)).run();
    }

    @Test
    public void actionRunsOncePerFullCounterPeriodOfActiveCalls(){
        final Runnable action = mock(Runnable.class);
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(action, false, 2);

        for (int i = 0; i < 6; i++){
            runner.run();
        }
        verify(action, times(1)).run();

        runner.run(); //7th call: the 4th active call, countdown reaches 0 again -> 2nd run
        verify(action, times(2)).run();
    }

    @Test
    public void counterPeriodCanBeReadAndUpdated(){
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(() -> { }, false, 5);

        assertEquals(5, runner.getCounterPeriod());

        runner.setCounterPeriod(9);

        assertEquals(9, runner.getCounterPeriod());
    }

    @Test
    public void changingCounterPeriodMidCountdownOnlyTakesEffectOnTheNextReload(){
        final Runnable action = mock(Runnable.class);
        final ParityConstrainedCountdownRunner runner = new ParityConstrainedCountdownRunner(action, false, 5);

        runner.run(); //active: countdown 0->5 (reload), action runs (1st)
        runner.run(); //skipped
        runner.run(); //active: countdown 5->4

        runner.setCounterPeriod(0); //changing the period mid-countdown must not touch the in-flight countdown

        runner.run(); //skipped
        runner.run(); //active: countdown 4->3, unaffected by the new period until the next reload

        verify(action, times(1)).run();
    }
}
