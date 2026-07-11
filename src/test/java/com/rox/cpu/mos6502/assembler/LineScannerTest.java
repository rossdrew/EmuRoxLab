package com.rox.cpu.mos6502.assembler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LineScannerTest {

    @Nested
    class BlankAndCommentLines {
        @Test
        public void blankLinesProduceNoSourceLines() {
            final List<SourceLine> lines = LineScanner.scan("\n\n   \n\t\n");

            assertTrue(lines.isEmpty());
        }

        @Test
        public void commentOnlyLinesProduceNoSourceLines() {
            final List<SourceLine> lines = LineScanner.scan("; just a comment\n   ; indented comment\n");

            assertTrue(lines.isEmpty());
        }

        @Test
        public void trailingCommentIsStrippedFromAnInstructionLine() {
            final List<SourceLine> lines = LineScanner.scan("LDX #$00 ; X = 0");

            assertEquals(1, lines.size());
            assertEquals("LDX", lines.get(0).mnemonic());
            assertEquals("#$00", lines.get(0).operandText());
        }
    }

    @Nested
    class InstructionLines {
        @Test
        public void instructionWithNoOperandHasEmptyOperandText() {
            final List<SourceLine> lines = LineScanner.scan("BRK ; Stop");

            assertEquals(1, lines.size());
            assertEquals(null, lines.get(0).label());
            assertEquals("BRK", lines.get(0).mnemonic());
            assertEquals("", lines.get(0).operandText());
        }

        @Test
        public void instructionWithOperandAndNoLabel() {
            final List<SourceLine> lines = LineScanner.scan("STA $0200,X");

            assertEquals(1, lines.size());
            assertEquals(null, lines.get(0).label());
            assertEquals("STA", lines.get(0).mnemonic());
            assertEquals("$0200,X", lines.get(0).operandText());
        }

        @Test
        public void mnemonicIsNormalizedToUpperCase() {
            final List<SourceLine> lines = LineScanner.scan("lda #$09");

            assertEquals("LDA", lines.get(0).mnemonic());
        }

        @Test
        public void tabIndentationIsTolerated() {
            final List<SourceLine> lines = LineScanner.scan("\t\tLDA\t#$09");

            assertEquals("LDA", lines.get(0).mnemonic());
            assertEquals("#$09", lines.get(0).operandText());
        }
    }

    @Nested
    class LabelLines {
        @Test
        public void labelOnlyLineHasNoMnemonic() {
            final List<SourceLine> lines = LineScanner.scan("LOOP:");

            assertEquals(1, lines.size());
            assertEquals("LOOP", lines.get(0).label());
            assertEquals(null, lines.get(0).mnemonic());
            assertEquals("", lines.get(0).operandText());
        }

        @Test
        public void labelAndInstructionOnTheSameLine() {
            final List<SourceLine> lines = LineScanner.scan("LOOP:   STA $0200,X   ; Store A at $0200 + X");

            assertEquals(1, lines.size());
            assertEquals("LOOP", lines.get(0).label());
            assertEquals("STA", lines.get(0).mnemonic());
            assertEquals("$0200,X", lines.get(0).operandText());
        }

        @Test
        public void labelOnlyLineFollowedByLabelAndInstructionLine() {
            final List<SourceLine> lines = LineScanner.scan("""
                    FOO:
                    BAR:    LDA #$00
                    """);

            assertEquals(2, lines.size());
            assertEquals(new SourceLine(1, "FOO", null, ""), lines.get(0));
            assertEquals(new SourceLine(2, "BAR", "LDA", "#$00"), lines.get(1));
        }

        @Test
        public void emptyTextBeforeAColonIsNotTreatedAsALabel() {
            final List<SourceLine> lines = LineScanner.scan(": BRK");

            assertEquals(1, lines.size());
            assertEquals(null, lines.get(0).label());
        }

        @Test
        public void anInvalidCharacterAfterTheStartOfACandidateLabelPreventsItBeingRecognisedAsALabel() {
            final List<SourceLine> lines = LineScanner.scan("LOOP!: BRK");

            assertEquals(1, lines.size());
            assertEquals(null, lines.get(0).label());
        }
    }

    @Nested
    class LineNumbers {
        @Test
        public void lineNumbersTrackOriginalSourcePositionSkippingBlankAndCommentLines() {
            final List<SourceLine> lines = LineScanner.scan("""
                    LDX #$00

                    ; a comment
                    LDA #$09
                    """);

            assertEquals(2, lines.size());
            assertEquals(1, lines.get(0).lineNumber());
            assertEquals(4, lines.get(1).lineNumber());
        }
    }

    @Test
    public void scansTheFullSampleProgramIntoTheExpectedSequenceOfLines() {
        final String simpleProgram = """
                        LDX #$00      ; X = 0
                        LDA #$09      ; A = value to store

                LOOP:   STA $0200,X   ; Store A at $0200 + X
                        INX           ; X = X + 1
                        CPX #$FF      ; Have we reached the end?
                        BNE LOOP      ; No, continue

                        BRK           ; Stop
                """;

        final List<SourceLine> lines = LineScanner.scan(simpleProgram);

        assertEquals(7, lines.size());

        assertEquals(new SourceLine(1, null, "LDX", "#$00"), lines.get(0));
        assertEquals(new SourceLine(2, null, "LDA", "#$09"), lines.get(1));
        assertEquals(new SourceLine(4, "LOOP", "STA", "$0200,X"), lines.get(2));
        assertEquals(new SourceLine(5, null, "INX", ""), lines.get(3));
        assertEquals(new SourceLine(6, null, "CPX", "#$FF"), lines.get(4));
        assertEquals(new SourceLine(7, null, "BNE", "LOOP"), lines.get(5));
        assertEquals(new SourceLine(9, null, "BRK", ""), lines.get(6));
    }
}
