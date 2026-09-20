package com.rox.input;

import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    //fully-qualified rather than imported - de.gurkenlabs.input4j.components.Button/Axis share this
    //loader's own reflection-based lookup, so referencing the real constants directly (not a name
    //lookup) avoids the classloading-order footgun explained on ControllerConfigLoader.KNOWN_COMPONENTS
    private static final InputComponent.ID BUTTON_0 = de.gurkenlabs.input4j.components.Button.BUTTON_0;
    private static final InputComponent.ID LEFT_AXIS_X = de.gurkenlabs.input4j.components.Axis.AXIS_X;

    private static InputDevice fakeDevice(final String productName){
        return fakeDevice(productName, d -> new float[0], LEFT_AXIS_X);
    }

    private static InputDevice fakeDevice(final String productName, final Function<InputDevice, float[]> pollCallback){
        return fakeDevice(productName, pollCallback, LEFT_AXIS_X);
    }

    private static InputDevice fakeDevice(final String productName, final Function<InputDevice, float[]> pollCallback,
                                           final InputComponent.ID... componentIds){
        final InputDevice device = new InputDevice("id", "name", productName, pollCallback, (d, i) -> {});
        device.setComponents(List.of(componentIds).stream().map(id -> new InputComponent(device, id)).toList());
        return device;
    }

    @Test
    public void gamepadSourceDefaultsToDeviceIndexZeroAndBuildsAWorkingGamepadController(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.a", "BUTTON_0");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller", d -> new float[]{1f}, BUTTON_0));

        final Controller player1 = ControllerConfigLoader.parse(properties, () -> devices).player1();
        assertTrue(player1 instanceof GamepadController);

        final GamepadController controller = (GamepadController) player1;
        controller.poll();

        assertTrue(controller.isPressed(Button.A));
    }

    @Test
    public void gamepadSourceSelectsDeviceByNameSubstringCaseInsensitively(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.device", "xbox");
        properties.setProperty("player1.gamepad.a", "BUTTON_0");
        //both devices have the same component, but only the (non-matching) first one reports pressed -
        //proves the *matching* device was actually the one selected, not just "some" device
        final InputDevice nonMatching = fakeDevice("DualShock 4", d -> new float[]{1f}, BUTTON_0);
        final InputDevice matching = fakeDevice("Xbox Wireless Controller", d -> new float[]{0f}, BUTTON_0);
        final List<InputDevice> devices = List.of(nonMatching, matching);

        final GamepadController controller = (GamepadController) ControllerConfigLoader.parse(properties, () -> devices).player1();
        controller.poll();

        assertFalse(controller.isPressed(Button.A));
    }

    @Test
    public void gamepadSourceSelectsDeviceByExplicitIndex(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.deviceIndex", "1");
        properties.setProperty("player1.gamepad.a", "BUTTON_0");
        final InputDevice device0 = fakeDevice("DualShock 4", d -> new float[]{1f}, BUTTON_0);
        final InputDevice device1 = fakeDevice("Xbox Wireless Controller", d -> new float[]{0f}, BUTTON_0);
        final List<InputDevice> devices = List.of(device0, device1);

        final GamepadController controller = (GamepadController) ControllerConfigLoader.parse(properties, () -> devices).player1();
        controller.poll();

        //device0 (index 0) reports pressed, device1 (the configured index 1) doesn't - proves index 1
        //specifically was selected, not just any/the first device
        assertFalse(controller.isPressed(Button.A));
    }

    @Test
    public void gamepadSourceWithDeviceIndexExactlyAtTheListSizeThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        //exactly deviceList.size() (not merely "too big") - the precise off-by-one boundary a
        //`>=` vs `>` mutant in the range check would otherwise miss
        properties.setProperty("player1.gamepad.deviceIndex", "1");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadSourceWithANegativeDeviceIndexThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.deviceIndex", "-1");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadSourceWithNoMatchingDeviceNameThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.device", "nonexistent brand");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadUnknownComponentNameThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.a", "NOT_A_REAL_COMPONENT");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadButtonBindingWithADirectionSuffixThrows(){
        //only axis components take a ":positive"/":negative" suffix - a button component with one
        //attached (e.g. a typo'd config) must be rejected, not silently accepted with the suffix ignored
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.a", "BUTTON_0:positive");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadAxisBindingWithoutADirectionSuffixThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.right", "LEFT_AXIS_X");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadAxisBindingWithAnInvalidDirectionThrows(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.right", "LEFT_AXIS_X:sideways");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));

        assertThrows(IllegalArgumentException.class, () -> ControllerConfigLoader.parse(properties, () -> devices));
    }

    @Test
    public void gamepadAxisBindingWithAPositiveDirectionOnlyTriggersOnAPositiveValue(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.right", "LEFT_AXIS_X:positive");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller", d -> new float[]{0.9f}));

        final GamepadController controller = (GamepadController) ControllerConfigLoader.parse(properties, () -> devices).player1();
        controller.poll();

        assertTrue(controller.isPressed(Button.RIGHT));
    }

    @Test
    public void gamepadAxisBindingWithANegativeDirectionOnlyTriggersOnANegativeValue(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.left", "LEFT_AXIS_X:negative");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller", d -> new float[]{-0.9f}));

        final GamepadController controller = (GamepadController) ControllerConfigLoader.parse(properties, () -> devices).player1();
        controller.poll();

        assertTrue(controller.isPressed(Button.LEFT));
    }

    @Test
    public void gamepadDeviceSupplierIsNeverCalledWhenNoPlayerUsesAGamepad(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "keyboard");
        properties.setProperty("player1.keyboard.a", "Z");

        //a supplier that throws proves it was never invoked - keyboard/none-only configs must not pay
        //for (or fail because of) real gamepad enumeration
        ControllerConfigLoader.parse(properties, () -> {
            throw new AssertionError("device supplier should not be called");
        });
    }

    @Test
    public void gamepadDeviceSupplierIsOnlyCalledOnceAcrossMultipleGamepadPlayers(){
        final Properties properties = new Properties();
        properties.setProperty("player1.source", "gamepad");
        properties.setProperty("player1.gamepad.a", "BUTTON_0");
        properties.setProperty("player2.source", "gamepad");
        properties.setProperty("player2.gamepad.a", "BUTTON_0");
        final List<InputDevice> devices = List.of(fakeDevice("Xbox Wireless Controller"));
        final int[] callCount = {0};

        ControllerConfigLoader.parse(properties, () -> {
            callCount[0]++;
            return devices;
        });

        assertEquals(1, callCount[0]);
    }
}
