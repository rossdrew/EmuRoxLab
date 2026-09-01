package com.rox.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControllerConfigLoaderTest {
    private static final Panel DUMMY_SOURCE = new Panel();

    private static KeyEvent press(final int keyCode){
        return new KeyEvent(DUMMY_SOURCE, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    public void keyboardSourceBuildsAWorkingKeyboardControllerFromItsBindings(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "keyboard");
        properties.setProperty("player1.keyboard.a", "Z");

        final Controller player1 = ControllerConfigLoader.parse(properties).player1();
        assertTrue(player1 instanceof KeyboardController);

        final KeyboardController keyboardController = (KeyboardController) player1;
        assertFalse(keyboardController.isPressed(Button.A));
        keyboardController.keyPressed(press(KeyEvent.VK_Z));
        assertTrue(keyboardController.isPressed(Button.A));
        assertFalse(keyboardController.isPressed(Button.B));
    }

    @Test
    public void noneSourceIsControllerNone(){
        final Properties properties = new Properties();
        properties.setProperty("player2.source", "none");

        assertSame(Controller.NONE, ControllerConfigLoader.parse(properties).player2());
    }

    @Test
    public void missingSourceDefaultsToNoneForEveryPlayer(){
        final ControllerConfiguration configuration = ControllerConfigLoader.parse(new Properties());

        assertSame(Controller.NONE, configuration.player1());
        assertSame(Controller.NONE, configuration.player2());
        assertSame(Controller.NONE, configuration.player3());
        assertSame(Controller.NONE, configuration.player4());
    }

    @Test
    public void unknownSourceThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "psychic");

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties));
    }

    @Test
    public void unknownKeyNameThrowsAClearException(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "keyboard");
        properties.setProperty("player1.keyboard.a", "NOT_A_REAL_KEY");

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties));
    }

    @Test
    public void fourScoreEnabledParsesTrue(){
        final Properties properties = new Properties();
        properties.setProperty("fourscore.enabled", "true");

        assertTrue(ControllerConfigLoader.parse(properties).fourScoreEnabled());
    }

    @Test
    public void fourScoreEnabledDefaultsToFalse(){
        assertFalse(ControllerConfigLoader.parse(new Properties()).fourScoreEnabled());
    }

    @Test
    public void keyNameResolutionIsLocaleIndependent(){
        //Turkish uppercases "i" to "İ" (dotted capital I), not ASCII "I" - a JVM running under this
        //locale must still resolve a lowercase "i" key name to VK_I, not throw or silently mis-map
        final Locale originalDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            final Properties properties = new Properties();
            properties.setProperty("player1.source", "keyboard");
            properties.setProperty("player1.keyboard.a", "i");

            final KeyboardController controller = (KeyboardController) ControllerConfigLoader.parse(properties).player1();
            controller.keyPressed(press(KeyEvent.VK_I));

            assertTrue(controller.isPressed(Button.A));
        } finally {
            Locale.setDefault(originalDefault);
        }
    }

    @Test
    public void loadReadsAPropertiesFileFromDisk(@TempDir final Path tempDir) throws IOException {
        //"keyboard" rather than "none" - "none" is also parse()'s own default for a missing key, so a
        //mutant that skips reading the file entirely would pass an assertion of "none" undetected
        final Path configFile = tempDir.resolve("controllers.properties");
        Files.writeString(configFile, "player1.source=keyboard\n");

        assertTrue(ControllerConfigLoader.load(configFile).player1() instanceof KeyboardController);
    }
}
