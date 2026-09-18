package com.rox.input;

import org.junit.jupiter.api.Test;

import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeyboardControllerTest {
    private static final Panel DUMMY_SOURCE = new Panel();

    private static KeyEvent keyEvent(final int id, final int keyCode){
        return new KeyEvent(DUMMY_SOURCE, id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    public void pressingABoundKeySetsThatButtonPressed(){
        final KeyboardController controller = new KeyboardController(Map.of(KeyEvent.VK_Z, Button.A));

        controller.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_Z));

        assertTrue(controller.isPressed(Button.A));
        assertFalse(controller.isPressed(Button.B));
    }

    @Test
    public void releasingABoundKeyClearsThatButton(){
        //Button.START (a non-zero ordinal) rather than A - a shift-left/shift-right mix-up in the
        //clearing mask is invisible for ordinal 0 (1<<0 == 1>>0), so this needs a higher bit to catch it
        final KeyboardController controller = new KeyboardController(Map.of(KeyEvent.VK_Z, Button.START));
        controller.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_Z));

        controller.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_Z));

        assertFalse(controller.isPressed(Button.START));
    }

    @Test
    public void unboundKeysAreIgnored(){
        final KeyboardController controller = new KeyboardController(Map.of(KeyEvent.VK_Z, Button.A));

        controller.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_Q));
        controller.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_Q));

        for (final Button button : Button.values()){
            assertFalse(controller.isPressed(button));
        }
    }

    @Test
    public void multipleBoundButtonsTrackIndependently(){
        final KeyboardController controller = new KeyboardController(Map.of(
                KeyEvent.VK_Z, Button.A,
                KeyEvent.VK_X, Button.B
        ));

        controller.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_Z));
        assertTrue(controller.isPressed(Button.A));
        assertFalse(controller.isPressed(Button.B));

        controller.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_X));
        assertTrue(controller.isPressed(Button.A));
        assertTrue(controller.isPressed(Button.B));

        controller.keyReleased(keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_Z));
        assertFalse(controller.isPressed(Button.A));
        assertTrue(controller.isPressed(Button.B));
    }

    @Test
    public void twoPlayersBindingsDoNotInterfere(){
        final KeyboardController player1 = new KeyboardController(Map.of(KeyEvent.VK_Z, Button.A));
        final KeyboardController player2 = new KeyboardController(Map.of(KeyEvent.VK_M, Button.A));

        player1.keyPressed(keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_Z));

        assertTrue(player1.isPressed(Button.A));
        assertFalse(player2.isPressed(Button.A));
    }
}
