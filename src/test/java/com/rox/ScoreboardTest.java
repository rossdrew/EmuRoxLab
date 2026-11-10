package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.rox.TestRomOutcome.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** {@link TestRomScoreboard}'s discovery and {@link ScoreboardSvg}'s drawing - not the ROM runs themselves. */
class ScoreboardTest {
    @TempDir
    Path roms;

    private void rom(final String path) throws IOException {
        final Path file = roms.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[0]);
    }

    @Test
    void eachFolderIsASuiteOfItsNesFiles() throws IOException {
        rom("oam_read/oam_read.nes");
        rom("oam_read/readme.txt");
        rom("instr_timing/rom_singles/2-branch.nes");
        rom("instr_timing/rom_singles/1-instr.nes");

        assertEquals(Map.of("oam_read", List.of("oam_read/oam_read.nes"),
                        "instr_timing", List.of("instr_timing/rom_singles/1-instr.nes", "instr_timing/rom_singles/2-branch.nes")),
                TestRomScoreboard.discover(roms));
    }

    @Test
    void romSinglesStandInForTheCombinedRom() throws IOException {
        rom("apu_test/apu_test.nes");
        rom("apu_test/rom_singles/1-len_ctr.nes");

        assertEquals(Map.of("apu_test", List.of("apu_test/rom_singles/1-len_ctr.nes")), TestRomScoreboard.discover(roms));
    }

    @Test
    void aFolderWithoutRomsIsNoSuite() throws IOException {
        rom("source/readme.txt");

        assertEquals(Map.of(), TestRomScoreboard.discover(roms));
    }

    @Test
    void suitesAreInNameOrder() throws IOException {
        rom("b/x.nes");
        rom("a/x.nes");

        assertEquals(List.of("a", "b"), List.copyOf(TestRomScoreboard.discover(roms).keySet()));
    }

    @Test
    void aFinishedRomReportingZeroPassedAndAnythingElseFailed(){
        assertEquals(PASSED, TestRomOutcome.of("s", "r", new BlarggRomRunner.Result(true, true, 0, "Passed")).kind());
        assertEquals(FAILED, TestRomOutcome.of("s", "r", new BlarggRomRunner.Result(true, true, 4, "Failed #4")).kind());
        assertEquals(NEVER_FINISHED, TestRomOutcome.of("s", "r", new BlarggRomRunner.Result(true, false, 0x80, "")).kind());
    }

    @Test
    void theLastLineIsTheVerdict(){
        assertEquals("Failed #11", new TestRomOutcome("s", "r", FAILED, 11, "\nImmediate occurence\n\n04-nmi_control\n\nFailed #11\n").lastLine());
        assertEquals("", new TestRomOutcome("s", "r", NEVER_FINISHED, 0x80, "").lastLine());
    }

    @Test
    void tilesSayHowEachRomEnded(){
        assertEquals("Passed", ScoreboardSvg.shortResult(new TestRomOutcome("s", "r", PASSED, 0, "")));
        assertEquals("Failed #4", ScoreboardSvg.shortResult(new TestRomOutcome("s", "r", FAILED, 4, "")));
        assertEquals("Failed", ScoreboardSvg.shortResult(new TestRomOutcome("s", "r", FAILED, 1, "")));
        assertEquals("Never finishes", ScoreboardSvg.shortResult(new TestRomOutcome("s", "r", NEVER_FINISHED, 0x80, "")));
        assertEquals("Crashed", ScoreboardSvg.shortResult(new TestRomOutcome("s", "r", CRASHED, -1, "")));
    }

    @Test
    void theChartCountsPassesAndColoursEachTile(){
        final String svg = ScoreboardSvg.render(List.of(
                new TestRomOutcome("suite_a", "1-good", PASSED, 0, "Passed"),
                new TestRomOutcome("suite_a", "2-bad", FAILED, 4, "Failed #4"),
                new TestRomOutcome("suite_b", "stuck", NEVER_FINISHED, 0x80, ""),
                new TestRomOutcome("suite_b", "boom", CRASHED, -1, "Unknown opcode")));

        assertTrue(svg.startsWith("<svg"), svg);
        assertTrue(svg.contains("1 / 4 passing"), svg);
        assertTrue(svg.contains(">25%<"), svg);
        assertTrue(svg.contains(">suite_a<") && svg.contains(">suite_b<"), svg);
        ScoreboardSvg.COLOURS.values().forEach(colour -> assertTrue(svg.contains(colour), colour));
        assertTrue(svg.contains("<title>suite_b/boom: Unknown opcode</title>"), svg);
    }

    @Test
    void anEmptyChartIsNoneOfNone(){
        assertTrue(ScoreboardSvg.render(List.of()).contains("0 / 0 passing"));
    }

    @Test
    void textIsEscapedForXml(){
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", ScoreboardSvg.escape("a <b> & \"c\""));
        assertEquals("ok\nfine", ScoreboardSvg.escape("o\u0001k\nfi\u001Fne"));
    }
}
