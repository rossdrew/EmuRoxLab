package com.rox.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ControllerTest {

    @Test
    public void noneNeverReportsAnyButtonPressed(){
        for (final Button button : Button.values()){
            assertFalse(Controller.NONE.isPressed(button));
        }
    }

    @Test
    public void defaultPollIsANoOp(){
        Controller.NONE.poll(); //no exception, no observable effect - unused until phase 3's gamepad polling
    }
}
