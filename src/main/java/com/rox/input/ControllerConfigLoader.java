package com.rox.input;

import de.gurkenlabs.input4j.ComponentType;
import de.gurkenlabs.input4j.InputComponent;
import de.gurkenlabs.input4j.InputDevice;
import de.gurkenlabs.input4j.InputDevicePlugin;
import de.gurkenlabs.input4j.InputDevices;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Loads a {@link ControllerConfiguration} from a {@code .properties} file - see
 * {@code controllers.properties.example} at the repo root for the format. {@link #load} is the thin
 * I/O entry point; {@link #parse(Properties)} is the pure, directly-testable core that does the actual
 * interpretation.
 */
public final class ControllerConfigLoader {
    private static final String FOUR_SCORE_ENABLED_KEY = "fourscore.enabled";
    private static final int PLAYER_COUNT = 4;
    private static final float DEFAULT_AXIS_THRESHOLD = 0.5f;

    //input4j's own InputComponent.ID.get(String) only finds IDs whose owning component class has
    //already been loaded by the JVM (each class registers its constants as a static-initializer side
    //effect) - relying on that here would make a config's resolvability depend on incidental
    //class-loading order (e.g. which real devices happened to be enumerated first), not the config
    //itself. Reading de.gurkenlabs.input4j.components.Button/Axis's own declared fields directly (the
    //same reflection approach as resolveKeyCode below, against KeyEvent.VK_*) sidesteps that entirely -
    //friendly per-controller-type names (e.g. XInput's "A") are deliberately not included here since
    //ID's own equals()/registration logic means only the canonical generic name ever survives anyway.
    private static final Map<String, InputComponent.ID> KNOWN_COMPONENTS = loadKnownComponents();

    private ControllerConfigLoader(){
    }

    private static Map<String, InputComponent.ID> loadKnownComponents(){
        final Map<String, InputComponent.ID> components = new HashMap<>();
        for (final Class<?> componentClass : List.of(
                de.gurkenlabs.input4j.components.Button.class, de.gurkenlabs.input4j.components.Axis.class)){
            for (final Field field : componentClass.getFields()){
                if (InputComponent.ID.class.isAssignableFrom(field.getType())){
                    try {
                        final InputComponent.ID id = (InputComponent.ID) field.get(null);
                        components.put(id.name, id);
                    } catch (IllegalAccessException e){
                        throw new IllegalStateException("Could not read input4j component field " + field, e);
                    }
                }
            }
        }
        return components;
    }

    public static ControllerConfiguration load(final Path path) throws IOException {
        final Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)){
            properties.load(in);
        }
        return parse(properties);
    }

    public static ControllerConfiguration parse(final Properties properties){
        return parse(properties, ControllerConfigLoader::availableDevices);
    }

    /**
     * Test seam: a fake {@code deviceSupplier} makes {@code source=gamepad} parsing fully testable
     * without a real physical gamepad, mirroring how {@code SpeakerAudioOutput} takes a real-line
     * seam - matches {@link #availableDevices()}'s own doc for why that's the one genuinely
     * hardware-dependent piece of this class.
     */
    static ControllerConfiguration parse(final Properties properties, final Supplier<Collection<InputDevice>> deviceSupplier){
        final boolean fourScoreEnabled = Boolean.parseBoolean(properties.getProperty(FOUR_SCORE_ENABLED_KEY, "false"));
        final LazyDevices devices = new LazyDevices(deviceSupplier);
        final Controller[] players = new Controller[PLAYER_COUNT];
        for (int i = 0; i < PLAYER_COUNT; i++){
            players[i] = parsePlayer(properties, i + 1, devices);
        }
        return new ControllerConfiguration(players[0], players[1], players[2], players[3], fourScoreEnabled);
    }

    /**
     * Enumerates real, currently-connected gamepads via input4j.
     * <p>
     * XXX Mutation coverage expected to have issues here since it deals with real hardware (and CI has
     * no gamepad attached) - just accepting it for now, same as {@code SpeakerAudioOutput.openDefaultLine()}.
     */
    private static Collection<InputDevice> availableDevices(){
        final InputDevicePlugin plugin = InputDevices.init();
        return plugin == null ? List.of() : plugin.getAll();
    }

    /** Only calls {@code deviceSupplier} if some player actually needs a gamepad - not for keyboard/none-only configs. */
    private static final class LazyDevices {
        private final Supplier<Collection<InputDevice>> source;
        private Collection<InputDevice> cached;

        LazyDevices(final Supplier<Collection<InputDevice>> source){
            this.source = source;
        }

        Collection<InputDevice> get(){
            if (cached == null){
                cached = source.get();
            }
            return cached;
        }
    }

    private static Controller parsePlayer(final Properties properties, final int playerNumber, final LazyDevices devices){
        final String prefix = "player" + playerNumber;
        final String source = properties.getProperty(prefix + ".source", "none");
        if ("keyboard".equalsIgnoreCase(source)){
            return new KeyboardController(parseKeyBindings(properties, prefix));
        }
        if ("gamepad".equalsIgnoreCase(source)){
            return new GamepadController(resolveDevice(properties, prefix, devices.get()), parseGamepadBindings(properties, prefix));
        }
        if ("none".equalsIgnoreCase(source)){
            return Controller.NONE;
        }
        throw new IllegalArgumentException("Unknown controller source '" + source + "' for " + prefix);
    }

    private static InputDevice resolveDevice(final Properties properties, final String prefix, final Collection<InputDevice> devices){
        final List<InputDevice> deviceList = List.copyOf(devices);
        final String nameSubstring = properties.getProperty(prefix + ".gamepad.device");
        if (nameSubstring != null){
            final String needle = nameSubstring.toLowerCase(Locale.ROOT);
            return deviceList.stream()
                    .filter(d -> d.getDisplayName().toLowerCase(Locale.ROOT).contains(needle))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No connected gamepad matches '" + nameSubstring + "' for " + prefix));
        }

        //no name given - fall back to enumeration order, which is stabler within a single run than
        //across runs/OS-reboots, hence name-substring being the recommended, primary option above
        final int index = Integer.parseInt(properties.getProperty(prefix + ".gamepad.deviceIndex", "0"));
        if (index < 0 || index >= deviceList.size()){
            throw new IllegalArgumentException(
                    "No connected gamepad at index " + index + " for " + prefix + " (" + deviceList.size() + " available)");
        }
        return deviceList.get(index);
    }

    private static Map<Button, GamepadBinding> parseGamepadBindings(final Properties properties, final String prefix){
        final Map<Button, GamepadBinding> bindings = new HashMap<>();
        for (final Button button : Button.values()){
            final String value = properties.getProperty(prefix + ".gamepad." + button.name().toLowerCase(Locale.ROOT));
            if (value != null){
                bindings.put(button, resolveGamepadBinding(value, prefix, button));
            }
        }
        return bindings;
    }

    private static GamepadBinding resolveGamepadBinding(final String value, final String prefix, final Button button){
        final String[] parts = value.split(":", 2);
        final String componentName = parts[0].trim();
        final InputComponent.ID componentId = KNOWN_COMPONENTS.get(componentName);
        if (componentId == null){
            throw new IllegalArgumentException(
                    "Unknown gamepad component '" + componentName + "' bound to " + button + " for " + prefix);
        }
        if (componentId.type != ComponentType.AXIS){
            if (parts.length > 1){
                throw new IllegalArgumentException(
                        "Button component '" + componentName + "' bound to " + button + " for " + prefix
                                + " must not have a direction suffix");
            }
            return new GamepadBinding.ButtonBinding(componentId);
        }
        if (parts.length < 2){
            throw new IllegalArgumentException(
                    "Axis component '" + componentName + "' bound to " + button + " for " + prefix
                            + " needs a direction suffix, e.g. '" + componentName + ":positive'");
        }
        final boolean positive = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "positive" -> true;
            case "negative" -> false;
            default -> throw new IllegalArgumentException(
                    "Unknown axis direction '" + parts[1] + "' bound to " + button + " for " + prefix
                            + " - expected 'positive' or 'negative'");
        };
        return new GamepadBinding.AxisBinding(componentId, positive, DEFAULT_AXIS_THRESHOLD);
    }

    private static Map<Integer, Button> parseKeyBindings(final Properties properties, final String prefix){
        final Map<Integer, Button> bindings = new HashMap<>();
        for (final Button button : Button.values()){
            final String keyName = properties.getProperty(prefix + ".keyboard." + button.name().toLowerCase());
            if (keyName != null){
                bindings.put(resolveKeyCode(keyName), button);
            }
        }
        return bindings;
    }

    private static int resolveKeyCode(final String keyName){
        try {
            //Locale.ROOT, not the JVM default - a Turkish default locale uppercases "i" to "İ" (dotted
            //capital I), which would look up a nonexistent "VK_İ..." field instead of "VK_I..."
            return KeyEvent.class.getField("VK_" + keyName.toUpperCase(Locale.ROOT)).getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException e){
            throw new IllegalArgumentException(
                    "Unknown key name '" + keyName + "' - expected a java.awt.event.KeyEvent.VK_* field name", e);
        }
    }
}
