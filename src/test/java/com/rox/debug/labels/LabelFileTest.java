package com.rox.debug.labels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelFileTest {
    @Test
    public void parsesSingleBytesAndRanges() throws LabelFileException {
        assertEquals(List.of(MemoryLabel.at("rupees", 0x066D), MemoryLabel.spanning("key_count", 0x0670, 0x0671)),
                LabelFile.parse(List.of("$066D rupees", "$0670-$0671 key_count")));
    }

    @Test
    public void addressesAreHexWithOrWithoutDollarInEitherCase() throws LabelFileException {
        assertEquals(List.of(MemoryLabel.at("a", 0x00AB), MemoryLabel.spanning("b", 0x0010, 0xFFFF), MemoryLabel.at("c", 0x0001)),
                LabelFile.parse(List.of("ab a", "$10-ffff b", "$1 c")));
    }

    @Test
    public void ignoresBlankLinesCommentsAndExtraSpacing() throws LabelFileException {
        assertEquals(List.of(MemoryLabel.at("rupees", 0x066D)),
                LabelFile.parse(List.of("", "# Zelda", "   ", "\t$066D \t rupees   # how many", "#$0670 not_a_label")));
    }

    @Test
    public void anEmptyFileHasNoLabels() throws LabelFileException {
        assertTrue(LabelFile.parse(List.of()).isEmpty());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "$066D                | expected '<address> <name>'",
            "$066D rupees extra   | expected '<address> <name>'",
            "$1-$2-$3 x           | isn't an address or a <start>-<end> range",
            "$GG x                | '$GG' isn't a hex address",
            "$ x                  | '$' isn't a hex address",
            "$10000 x             | '$10000' isn't a hex address",
            "+1F x                | '+1F' isn't a hex address",
            "$0010- x             | '' isn't a hex address",
            "$0671-$0670 x        | range ends ($0670) before it starts ($0671)",
    })
    public void rejectsBadLinesWithTheLineNumberAndReason(final String badLine, final String expectedProblem){
        final LabelFileException e = assertThrows(LabelFileException.class,
                () -> LabelFile.parse(List.of("$0000 fine", "# comment", badLine)));

        assertEquals(3, e.lineNumber());
        assertTrue(e.getMessage().startsWith("Line 3: "), e.getMessage());
        assertTrue(e.getMessage().contains(expectedProblem), e.getMessage());
    }

    @Test
    public void aRangeOfOneByteIsFine() throws LabelFileException {
        assertEquals(List.of(MemoryLabel.at("x", 0x0010)), LabelFile.parse(List.of("$0010-$0010 x")));
    }

    @Test
    public void rejectsADuplicateNameAtItsSecondUse(){
        final LabelFileException e = assertThrows(LabelFileException.class,
                () -> LabelFile.parse(List.of("$0010 hp", "$0011 other", "$0012 hp")));

        assertEquals(3, e.lineNumber());
        assertTrue(e.getMessage().contains("'hp' is already used"), e.getMessage());
    }

    @Test
    public void formatsSingleBytesAndRangesAsPaddedUppercaseHex(){
        assertEquals(List.of("$066D rupees", "$0670-$0671 key_count", "$000A x"),
                LabelFile.format(List.of(MemoryLabel.at("rupees", 0x066D), MemoryLabel.spanning("key_count", 0x0670, 0x0671),
                        MemoryLabel.at("x", 0x000A))));
    }

    @Test
    public void writeThenReadRoundTripsInOrder(@TempDir final Path tempDir) throws IOException, LabelFileException {
        final Path file = tempDir.resolve("loz").resolve("labels.txt"); //folder doesn't exist yet
        final List<MemoryLabel> labels = List.of(MemoryLabel.at("z_last_alphabetically", 0x0001),
                MemoryLabel.spanning("a_first", 0x6000, 0x6003));

        LabelFile.write(file, labels);

        assertEquals(labels, LabelFile.read(file));
        assertEquals(LabelFile.HEADER, Files.readAllLines(file).get(0));
        assertNoTemporaryFilesLeftIn(file.getParent());
    }

    @Test
    public void writeReplacesTheWholeFile(@TempDir final Path tempDir) throws IOException, LabelFileException {
        final Path file = tempDir.resolve("labels.txt");
        LabelFile.write(file, List.of(MemoryLabel.at("old", 0x0001), MemoryLabel.at("older", 0x0002)));

        LabelFile.write(file, List.of(MemoryLabel.at("new", 0x0003)));

        assertEquals(List.of(MemoryLabel.at("new", 0x0003)), LabelFile.read(file));
    }

    @Test
    public void readingAFileThatDoesNotExistYetGivesNoLabels(@TempDir final Path tempDir) throws IOException, LabelFileException {
        assertTrue(LabelFile.read(tempDir.resolve("labels.txt")).isEmpty());
    }

    @Test
    public void readReportsABadLine(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("labels.txt");
        Files.write(file, List.of("$0010 hp", "nonsense"));

        assertEquals(2, assertThrows(LabelFileException.class, () -> LabelFile.read(file)).lineNumber());
    }

    private static void assertNoTemporaryFilesLeftIn(final Path folder) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(folder)){
            assertEquals(List.of(folder.resolve("labels.txt")), files.toList(), "only the labels file itself should remain");
        }
    }

    /** CodeRabbit's PR #47 finding: a failed save must not leave its temporary file behind. */
    @Test
    public void aFailedWriteLeavesNoTemporaryFileBehind(@TempDir final Path tempDir) throws IOException {
        final Path target = tempDir.resolve("labels.txt");
        Files.createDirectory(target); //a non-empty directory where the file should go makes the final move fail
        Files.createFile(target.resolve("blocker"));

        assertThrows(IOException.class, () -> LabelFile.write(target, List.of(MemoryLabel.at("x", 1))));

        try (java.util.stream.Stream<Path> files = Files.list(tempDir)){
            assertEquals(List.of(target), files.toList(), "no .tmp file left alongside");
        }
    }

    /** CodeRabbit's PR #47 finding: overlapping saves must not move each other's temporary file away. */
    @Test
    public void concurrentWritesAllSucceed(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("labels.txt");
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            final List<java.util.concurrent.Future<?>> writes = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++){
                final int value = i;
                writes.add(pool.submit(() -> {
                    LabelFile.write(file, List.of(MemoryLabel.at("v" + value, value)));
                    return null;
                }));
            }
            for (final java.util.concurrent.Future<?> write : writes){
                write.get(); //rethrows any write's failure
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, LabelFile.read(file).size(), "one complete write won");
        assertNoTemporaryFilesLeftIn(tempDir);
    }

    /** CodeRabbit's PR #47 finding: Java's \s is ASCII-only, but MemoryLabel rejects any Unicode whitespace. */
    @Test
    public void aNameContainingUnicodeWhitespaceGetsANumberedError(){
        final LabelFileException e = assertThrows(LabelFileException.class,
                () -> LabelFile.parse(List.of("$0001 fine", "$0010 hp\u2003max"))); //em space

        assertEquals(2, e.lineNumber());
        assertTrue(e.getMessage().contains("expected '<address> <name>'"), e.getMessage());
    }

    @Test
    public void unicodeWhitespaceAroundAndBetweenPartsIsJustSpacing() throws LabelFileException {
        assertEquals(List.of(MemoryLabel.at("hp", 0x0010)),
                LabelFile.parse(List.of("\u2003\u3000$0010\u2002hp\u2003")));
    }

    /** CodeRabbit's PR #47 merge-risk note: '#' starts a comment, so it can't be part of a name that reads back. */
    @Test
    public void aNameNeverChangesOnTheWayThroughTheFile(@TempDir final Path tempDir) throws IOException, LabelFileException {
        final Path file = tempDir.resolve("labels.txt");
        final List<MemoryLabel> labels = List.of(MemoryLabel.at("A1_b.c-D", 0x0010));

        LabelFile.write(file, labels);

        assertEquals(labels, LabelFile.read(file));
        assertThrows(IllegalArgumentException.class, () -> MemoryLabel.at("hp#2", 0x0010), "such a name can't be made at all");
    }
}
