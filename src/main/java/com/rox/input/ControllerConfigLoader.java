package com.rox.input;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Loads a {@link ControllerConfiguration} from a {@code .properties} file - see
 * {@code controllers.properties.example} at the repo root for the format. {@link #load} is the thin
 * I/O entry point; {@link #parse} is the pure, directly-testable core that does the actual
 * interpretation.
 */
public final class ControllerConfigLoader {
    private static final String FOUR_SCORE_ENABLED_KEY = "fourscore.enabled";
    private static final int PLAYER_COUNT = 4;

    private ControllerConfigLoader(){
    }

    public static ControllerConfiguration load(final Path path) throws IOException {
        final Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)){
            properties.load(in);
        }
        return parse(properties);
    }

    public static ControllerConfiguration parse(final Properties properties){
        final boolean fourScoreEnabled = Boolean.parseBoolean(properties.getProperty(FOUR_SCORE_ENABLED_KEY, "false"));
        final Controller[] players = new Controller[PLAYER_COUNT];
        for (int i = 0; i < PLAYER_COUNT; i++){
            players[i] = parsePlayer(properties, i + 1);
        }
        return new ControllerConfiguration(players[0], players[1], players[2], players[3], fourScoreEnabled);
    }

    private static Controller parsePlayer(final Properties properties, final int playerNumber){
        final String prefix = "player" + playerNumber;
        final String source = properties.getProperty(prefix + ".source", "none");
        if ("keyboard".equalsIgnoreCase(source)){
            return new KeyboardController(parseKeyBindings(properties, prefix));
        }
        if ("none".equalsIgnoreCase(source)){
            return Controller.NONE;
        }
        throw new IllegalArgumentException("Unknown controller source '" + source + "' for " + prefix);
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
