package com.rox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every test ROM under {@code src/test/resources/roms/}, run and drawn as one SVG chart - green passed,
 * red failed, amber never finished, grey crashed. A suite is a folder; a {@code rom_singles} folder
 * stands in for its suite's combined ROM. New ROMs show up without code changes.
 */
final class TestRomScoreboard {
    static final long TICK_BUDGET = 20_000_000;
    private static final String SINGLES = "rom_singles";

    private TestRomScoreboard(){
    }

    /** Suite name -> its ROMs' resource paths (relative to {@code roms/}), both sorted. */
    static Map<String, List<String>> discover(final Path romsRoot){
        final Map<String, List<String>> suites = new TreeMap<>();
        try (Stream<Path> dirs = Files.list(romsRoot)){
            for (final Path suite : dirs.filter(Files::isDirectory).sorted().toList()){
                final Path singles = suite.resolve(SINGLES);
                final Path romDir = Files.isDirectory(singles) ? singles : suite;
                try (Stream<Path> files = Files.list(romDir)){
                    final List<String> roms = files.filter(file -> file.getFileName().toString().endsWith(".nes"))
                            .map(file -> romsRoot.relativize(file).toString().replace('\\', '/'))
                            .sorted().toList();
                    if (!roms.isEmpty()){
                        suites.put(suite.getFileName().toString(), roms);
                    }
                }
            }
        } catch (IOException e){
            throw new UncheckedIOException(e);
        }
        return suites;
    }

    /** Runs every discovered ROM. */
    static List<TestRomOutcome> runAll(final Path romsRoot){
        final List<TestRomOutcome> outcomes = new ArrayList<>();
        discover(romsRoot).forEach((suite, roms) -> roms.forEach(rom -> outcomes.add(run(suite, rom))));
        return outcomes;
    }

    private static TestRomOutcome run(final String suite, final String rom){
        final String name = Path.of(rom).getFileName().toString().replace(".nes", "");
        try {
            return TestRomOutcome.of(suite, name, BlarggRomRunner.run(rom, TICK_BUDGET));
        } catch (IOException | RuntimeException e){
            return new TestRomOutcome(suite, name, TestRomOutcome.Kind.CRASHED, -1, e.toString());
        }
    }

    /** {@code args[0]}: where to write the SVG. */
    static void main(final String[] args) throws IOException {
        final List<TestRomOutcome> outcomes = runAll(Path.of("src/test/resources/roms"));
        final Path out = Path.of(args[0]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, ScoreboardSvg.render(outcomes));
        outcomes.forEach(o -> System.out.printf("%-20s %-28s %-15s %s%n", o.suite(), o.rom(), o.kind(), o.lastLine()));
        System.out.println("Wrote " + out);
    }
}
