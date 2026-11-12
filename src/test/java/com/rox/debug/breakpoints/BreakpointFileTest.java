package com.rox.debug.breakpoints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BreakpointFileTest {
    @TempDir
    Path directory;

    private Path file(){
        return directory.resolve("game").resolve("breakpoints.txt");
    }

    @Test
    void whatsSavedLoadsBack() throws IOException {
        final Breakpoints saved = new Breakpoints();
        saved.addBreakpoint(0xC123);
        saved.addBreakpoint(0x8000);
        saved.addWatch(0x2000, WatchKind.WRITE);
        saved.addWatch(0x0042, WatchKind.ACCESS);
        BreakpointFile.save(file(), saved);
        final Breakpoints loaded = new Breakpoints();

        assertEquals(List.of(), BreakpointFile.load(file(), loaded));

        assertEquals(List.of(0x8000, 0xC123), List.copyOf(loaded.breakpoints()));
        assertEquals(Map.of(0x2000, WatchKind.WRITE, 0x0042, WatchKind.ACCESS), loaded.watches());
    }

    @Test
    void theFileIsReadable() throws IOException {
        final Breakpoints breakpoints = new Breakpoints();
        breakpoints.addBreakpoint(0xC123);
        breakpoints.addWatch(0x2000, WatchKind.WRITE);

        BreakpointFile.save(file(), breakpoints);

        assertEquals(List.of("break $C123", "watch $2000 WRITE"), Files.readAllLines(file()).subList(1, 3));
        assertTrue(Files.readAllLines(file()).get(0).startsWith("#"));
    }

    @Test
    void noFileIsNothingSet() throws IOException {
        final Breakpoints breakpoints = new Breakpoints();

        assertEquals(List.of(), BreakpointFile.load(file(), breakpoints));

        assertTrue(breakpoints.breakpoints().isEmpty());
        assertTrue(breakpoints.watches().isEmpty());
    }

    @Test
    void blankLinesCommentsCaseAndSpacingAreForgiven() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "# mine\n\n  break   $c123  \nwatch $42 read\n");
        final Breakpoints breakpoints = new Breakpoints();

        assertEquals(List.of(), BreakpointFile.load(file(), breakpoints));

        assertEquals(List.of(0xC123), List.copyOf(breakpoints.breakpoints()));
        assertEquals(Map.of(0x0042, WatchKind.READ), breakpoints.watches());
    }

    @Test
    void unreadableLinesAreReportedAndTheRestStillLoad() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), String.join("\n", "break $C123", "break C123", "break $GG", "break $10000",
                "watch $2000 SIDEWAYS", "watch $2000", "jump $C000", "break $8000") + "\n");
        final Breakpoints breakpoints = new Breakpoints();

        assertEquals(List.of("break C123", "break $GG", "break $10000", "watch $2000 SIDEWAYS", "watch $2000", "jump $C000"),
                BreakpointFile.load(file(), breakpoints));

        assertEquals(List.of(0x8000, 0xC123), List.copyOf(breakpoints.breakpoints()));
    }

    @Test
    void savingNothingLeavesJustTheHeader() throws IOException {
        BreakpointFile.save(file(), new Breakpoints());

        assertEquals(1, Files.readAllLines(file()).size());
    }

    @Test
    void savesToAFileWithNoFolder() throws IOException {
        final Path here = Path.of("breakpoints-test-" + ProcessHandle.current().pid() + ".txt");
        try {
            BreakpointFile.save(here, new Breakpoints());
            assertTrue(Files.exists(here));
        } finally {
            Files.deleteIfExists(here);
        }
    }
}
