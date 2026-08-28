package com.rox.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControllerConfigurationTest {

    @Test
    public void eachAccessorReturnsItsOwnConstructorArgument(){
        final Controller player1 = button -> false;
        final Controller player2 = button -> false;
        final Controller player3 = button -> false;
        final Controller player4 = button -> false;

        final ControllerConfiguration configuration = new ControllerConfiguration(player1, player2, player3, player4, true);

        assertSame(player1, configuration.player1());
        assertSame(player2, configuration.player2());
        assertSame(player3, configuration.player3());
        assertSame(player4, configuration.player4());
        assertTrue(configuration.fourScoreEnabled());
    }

    @Test
    public void fourScoreEnabledCanBeFalse(){
        final ControllerConfiguration configuration =
                new ControllerConfiguration(Controller.NONE, Controller.NONE, Controller.NONE, Controller.NONE, false);

        assertFalse(configuration.fourScoreEnabled());
    }

    @Test
    public void noneHasAllFourSlotsAbsentAndFourScoreDisabled(){
        assertSame(Controller.NONE, ControllerConfiguration.NONE.player1());
        assertSame(Controller.NONE, ControllerConfiguration.NONE.player2());
        assertSame(Controller.NONE, ControllerConfiguration.NONE.player3());
        assertSame(Controller.NONE, ControllerConfiguration.NONE.player4());
        assertFalse(ControllerConfiguration.NONE.fourScoreEnabled());
    }

    @Test
    public void twoPlayersFillsOnlyTheFirstTwoSlotsAndDisablesFourScore(){
        final Controller player1 = button -> false;
        final Controller player2 = button -> false;

        final ControllerConfiguration configuration = ControllerConfiguration.twoPlayers(player1, player2);

        assertSame(player1, configuration.player1());
        assertSame(player2, configuration.player2());
        assertSame(Controller.NONE, configuration.player3());
        assertSame(Controller.NONE, configuration.player4());
        assertFalse(configuration.fourScoreEnabled());
    }
}
