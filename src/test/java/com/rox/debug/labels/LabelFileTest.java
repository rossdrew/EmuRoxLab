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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(Files.exists(file.resolveSibling("labels.txt.tmp")), "the temporary file is moved into place");
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
}
