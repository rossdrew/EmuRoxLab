package com.rox.input;

import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GamepadControllerTest {

    //a real device.poll() call, driven by a fake, injectable poll callback rather than real hardware -
    //this is how input4j's own InputDevice is designed to be constructed/tested, so nothing here needs
    //excluding from coverage the way SpeakerAudioOutput's real line does
    private static InputDevice fakeDevice(final Function<InputDevice, float[]> pollCallback, final InputComponent.ID... componentIds){
        final InputDevice device = new InputDevice("id", "name", "product", pollCallback, (d, i) -> {});
        final List<InputComponent> components = List.of(componentIds).stream()
                .map(id -> new InputComponent(device, id))
                .toList();
        device.setComponents(components);
        return device;
    }

    //fully-qualified rather than imported - de.gurkenlabs.input4j.components.Button shares a simple
    //name with this package's own Button enum. Referencing input4j's real constants directly (not
    //InputComponent.ID.get(name), which only finds IDs whose owning class the JVM has already loaded)
    //avoids a classloading-order footgun - see ControllerConfigLoader's own KNOWN_COMPONENTS comment.
    private static final InputComponent.ID BUTTON_0 = de.gurkenlabs.input4j.components.Button.BUTTON_0;
    private static final InputComponent.ID BUTTON_1 = de.gurkenlabs.input4j.components.Button.BUTTON_1;
    private static final InputComponent.ID LEFT_AXIS_X = de.gurkenlabs.input4j.components.Axis.AXIS_X;

    @Test
    public void pollReadsABoundButtonComponentIntoPressedMask(){
        final InputComponent.ID buttonId = BUTTON_0;
        final InputDevice device = fakeDevice(d -> new float[]{1f}, buttonId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.A, new GamepadBinding.ButtonBinding(buttonId)));

        assertFalse(controller.isPressed(Button.A));

        controller.poll();

        assertTrue(controller.isPressed(Button.A));
        assertFalse(controller.isPressed(Button.B));
    }

    @Test
    public void pollClearsAPreviouslyPressedButtonOnceReleased(){
        final InputComponent.ID buttonId = BUTTON_0;
        //Button.START (a non-zero ordinal), same reasoning as KeyboardControllerTest's own release test -
        //a shift mix-up in the mask rebuild is invisible for ordinal 0
        final GamepadController controller = new GamepadController(
                fakeDevice(d -> new float[]{1f}, buttonId),
                Map.of(Button.START, new GamepadBinding.ButtonBinding(buttonId)));
        controller.poll();
        assertTrue(controller.isPressed(Button.START));

        final InputDevice released = fakeDevice(d -> new float[]{0f}, buttonId);
        final GamepadController releasedController = new GamepadController(released,
                Map.of(Button.START, new GamepadBinding.ButtonBinding(buttonId)));
        releasedController.poll();

        assertFalse(releasedController.isPressed(Button.START));
    }

    @Test
    public void pollReadsABoundAxisComponentPastItsThreshold(){
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{0.9f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.RIGHT, new GamepadBinding.AxisBinding(axisId, true, 0.5f)));

        controller.poll();

        assertTrue(controller.isPressed(Button.RIGHT));
    }

    @Test
    public void axisBindingBelowThresholdIsNotPressed(){
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{0.2f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.RIGHT, new GamepadBinding.AxisBinding(axisId, true, 0.5f)));

        controller.poll();

        assertFalse(controller.isPressed(Button.RIGHT));
    }

    @Test
    public void negativeAxisDirectionOnlyTriggersOnTheOppositeSign(){
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{-0.9f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.LEFT, new GamepadBinding.AxisBinding(axisId, false, 0.5f),
                        Button.RIGHT, new GamepadBinding.AxisBinding(axisId, true, 0.5f)));

        controller.poll();

        assertTrue(controller.isPressed(Button.LEFT));
        assertFalse(controller.isPressed(Button.RIGHT));
    }

    @Test
    public void negativeAxisDirectionIsNotPressedByAPositiveValueWithinTheThreshold(){
        //0.3 is inside (-0.5, 0.5) - neither ">= 0.5" nor "<= -0.5" should fire. This specifically
        //catches a mutant that drops the negation on "-threshold" (making it read "<= threshold"),
        //which -0.9-style tests above can't tell apart from the correct behaviour
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{0.3f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.LEFT, new GamepadBinding.AxisBinding(axisId, false, 0.5f)));

        controller.poll();

        assertFalse(controller.isPressed(Button.LEFT));
    }

    @Test
    public void axisBindingIsPressedExactlyAtItsPositiveThreshold(){
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{0.5f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.RIGHT, new GamepadBinding.AxisBinding(axisId, true, 0.5f)));

        controller.poll();

        assertTrue(controller.isPressed(Button.RIGHT));
    }

    @Test
    public void axisBindingIsPressedExactlyAtItsNegativeThreshold(){
        final InputComponent.ID axisId = LEFT_AXIS_X;
        final InputDevice device = fakeDevice(d -> new float[]{-0.5f}, axisId);
        final GamepadController controller = new GamepadController(device,
                Map.of(Button.LEFT, new GamepadBinding.AxisBinding(axisId, false, 0.5f)));

        controller.poll();

        assertTrue(controller.isPressed(Button.LEFT));
    }

    @Test
    public void unboundButtonsAreNeverPressed(){
        final InputComponent.ID buttonId = BUTTON_0;
        final GamepadController controller = new GamepadController(
                fakeDevice(d -> new float[]{1f}, buttonId),
                Map.of());

        controller.poll();

        for (final Button button : Button.values()){
            assertFalse(controller.isPressed(button));
        }
    }

    @Test
    public void aBindingForAComponentTheDeviceDoesNotHaveIsNeverPressedRatherThanThrowing(){
        final InputComponent.ID presentButton = BUTTON_0;
        final InputComponent.ID missingButton = BUTTON_1;
        final GamepadController controller = new GamepadController(
                fakeDevice(d -> new float[]{1f}, presentButton),
                Map.of(Button.A, new GamepadBinding.ButtonBinding(missingButton)));

        controller.poll();

        assertFalse(controller.isPressed(Button.A));
    }

    @Test
    public void twoControllersOnDifferentDevicesTrackIndependently(){
        final InputComponent.ID buttonId = BUTTON_0;
        final GamepadController player1 = new GamepadController(
                fakeDevice(d -> new float[]{1f}, buttonId),
                Map.of(Button.A, new GamepadBinding.ButtonBinding(buttonId)));
        final GamepadController player2 = new GamepadController(
                fakeDevice(d -> new float[]{0f}, buttonId),
                Map.of(Button.A, new GamepadBinding.ButtonBinding(buttonId)));

        player1.poll();
        player2.poll();

        assertTrue(player1.isPressed(Button.A));
        assertFalse(player2.isPressed(Button.A));
    }
}
