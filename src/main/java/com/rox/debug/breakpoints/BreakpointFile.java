package com.rox.debug.breakpoints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A ROM's {@link Breakpoints}, kept between loads (e.g. {@code SavePaths.breakpointsFile(romPath)}) - one per line:
 * <pre>
 * break $C123
 * watch $2000 WRITE
 * </pre>
 * Blank lines and {@code #} comments are skipped.
 */
public final class BreakpointFile {
    private static final String BREAK = "break";
    private static final String WATCH = "watch";

    private BreakpointFile(){
    }

    /**
     * Adds what {@code path} holds to {@code breakpoints} - nothing, if there's no file yet.
     *
     * @return the lines that couldn't be read, for a warning - the rest still load
     */
    public static List<String> load(final Path path, final Breakpoints breakpoints) throws IOException {
        final List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (NoSuchFileException e){
            return List.of();
        }
        final List<String> unreadable = new ArrayList<>();
        for (final String line : lines){
            final String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")){
                continue;
            }
            if (!apply(trimmed.split("\\s+"), breakpoints)){
                unreadable.add(line);
            }
        }
        return unreadable;
    }

    private static boolean apply(final String[] parts, final Breakpoints breakpoints){
        try {
            if (parts.length == 2 && parts[0].equals(BREAK)){
                breakpoints.addBreakpoint(address(parts[1]));
                return true;
            }
            if (parts.length == 3 && parts[0].equals(WATCH)){
                breakpoints.addWatch(address(parts[1]), WatchKind.valueOf(parts[2].toUpperCase(Locale.ROOT)));
                return true;
            }
        } catch (IllegalArgumentException e){
            //a bad address or watch kind - reported as unreadable below
        }
        return false;
    }

    private static int address(final String text){
        if (!text.startsWith("$")){
            throw new IllegalArgumentException("Not a $-prefixed address: " + text);
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    /** Writes everything in {@code breakpoints} to {@code path}, creating its folder if need be. */
    public static void save(final Path path, final Breakpoints breakpoints) throws IOException {
        final List<String> lines = new ArrayList<>();
        lines.add("# EmuRoxLab breakpoints - one per line: break $ADDR | watch $ADDR READ|WRITE|ACCESS");
        breakpoints.breakpoints().forEach(address -> lines.add(String.format("%s $%04X", BREAK, address)));
        for (final Map.Entry<Integer, WatchKind> watch : breakpoints.watches().entrySet()){
            lines.add(String.format("%s $%04X %s", WATCH, watch.getKey(), watch.getValue()));
        }
        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null){
            Files.createDirectories(parent);
        }
        Files.write(path, lines);
    }
}
