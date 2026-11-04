package com.rox;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The game window's command line - every flag optional:
 * {@code [--rom <file.nes>] [--controllers <file.properties>] [--seconds <n>]}.
 *
 * @param seconds how long to run before closing, or empty to run until the window is closed
 */
record LaunchOptions(Optional<Path> rom, Optional<Path> controllers, OptionalInt seconds) {
    static final String USAGE = "Usage: RomVideoSmokeDemo [--rom <file.nes>] [--controllers <file.properties>]"
            + " [--seconds <n>, or omit to run until the window is closed]";

    /** @throws IllegalArgumentException naming what's wrong: an unknown or repeated flag, a missing value, bad seconds */
    static LaunchOptions parse(final String... args){
        Path rom = null;
        Path controllers = null;
        Integer seconds = null;
        for (int i = 0; i < args.length; i += 2){
            final String flag = args[i];
            switch (flag){
                case "--rom" -> rom = once(flag, rom, Path.of(value(args, i)));
                case "--controllers" -> controllers = once(flag, controllers, Path.of(value(args, i)));
                case "--seconds" -> seconds = once(flag, seconds, seconds(value(args, i)));
                default -> throw new IllegalArgumentException("Unknown option: " + flag);
            }
        }
        return new LaunchOptions(Optional.ofNullable(rom), Optional.ofNullable(controllers),
                seconds == null ? OptionalInt.empty() : OptionalInt.of(seconds));
    }

    /** The value after the flag at {@code args[i]} - not another flag, so a missing value can't swallow one. */
    private static String value(final String[] args, final int i){
        if (i + 1 >= args.length || args[i + 1].startsWith("--")){
            throw new IllegalArgumentException(args[i] + " needs a value");
        }
        return args[i + 1];
    }

    private static <T> T once(final String flag, final T previous, final T value){
        if (previous != null){
            throw new IllegalArgumentException(flag + " given more than once");
        }
        return value;
    }

    private static int seconds(final String value){
        final int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException e){
            throw new IllegalArgumentException("--seconds must be a whole number, not " + value);
        }
        if (seconds < 0){
            throw new IllegalArgumentException("--seconds must be non-negative");
        }
        return seconds;
    }
}
