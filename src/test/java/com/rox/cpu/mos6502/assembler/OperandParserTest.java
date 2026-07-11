package com.rox.cpu.mos6502.assembler;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperandParserTest {

    @Nested
    class Empty {
        @Test
        public void emptyOperandTextIsTheEmptySentinel() {
            final Operand operand = OperandParser.parse(1, "");

            assertTrue(operand.isEmpty());
        }
    }

    @Nested
    class Accumulator {
        @ParameterizedTest
        @CsvSource({"A", "a"})
        public void bareARegisterTokenIsAccumulator(String text) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(AddressingMode.ACCUMULATOR, operand.mode());
        }
    }

    @Nested
    class Immediate {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "#$00, 0",
                "#$FF, 255",
                "#0,   0",
                "#255, 255"
        })
        public void hashPrefixedLiteralsAreImmediate(String text, int expectedValue) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(AddressingMode.IMMEDIATE, operand.mode());
            assertEquals(expectedValue, operand.value());
        }

        @Test
        public void valueAboveByteRangeIsRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "#$100"));
        }

        @Test
        public void malformedHexDigitsAreRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "#$ZZ"));
        }

        @Test
        public void negativeValueIsRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "#-5"));
        }
    }

    @Nested
    class ZeroPageAndAbsolute {
        @ParameterizedTest(name = "{0} -> {1}={2}")
        @CsvSource({
                "5,     ZERO_PAGE, 5", // single-digit literal: shorter than the ,X/,Y suffix itself
                "$05,   ZERO_PAGE, 5",
                "$FF,   ZERO_PAGE, 255",
                "$ff,   ZERO_PAGE, 255",
                "$0100, ABSOLUTE,  256",
                "$00FF, ABSOLUTE,  255", // 4 hex digits forces absolute even though the value fits in a byte
                "255,   ZERO_PAGE, 255",
                "256,   ABSOLUTE,  256",
                "1234,  ABSOLUTE,  1234"
        })
        public void digitCountOrMagnitudeDecidesZeroPageVsAbsolute(String text, AddressingMode expectedMode, int expectedValue) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(expectedMode, operand.mode());
            assertEquals(expectedValue, operand.value());
        }

        @ParameterizedTest(name = "{0} -> {1}={2}")
        @CsvSource({
                "'$05,X',   ZERO_PAGE_X, 5",
                "'$1234,X', ABSOLUTE_X,  4660",
                "'$05,Y',   ZERO_PAGE_Y, 5",
                "'$1234,Y', ABSOLUTE_Y,  4660",
                "'$05,x',   ZERO_PAGE_X, 5"
        })
        public void indexedSuffixIsAppliedToTheDisambiguatedMode(String text, AddressingMode expectedMode, int expectedValue) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(expectedMode, operand.mode());
            assertEquals(expectedValue, operand.value());
        }

        @Test
        public void valueAboveAddressRangeIsRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "$10000"));
        }
    }

    @Nested
    class Indirect {
        @Test
        public void parenthesizedAbsoluteIsIndirect() {
            final Operand operand = OperandParser.parse(1, "($1234)");

            assertEquals(AddressingMode.INDIRECT, operand.mode());
            assertEquals(0x1234, operand.value());
        }

        @ParameterizedTest
        @CsvSource({"'($05,X)'", "'($05,x)'"})
        public void commaXInsideParensIsIndexedIndirect(String text) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(AddressingMode.INDIRECT_X, operand.mode());
            assertEquals(5, operand.value());
        }

        @ParameterizedTest
        @CsvSource({"'($05),Y'", "'($05),y'"})
        public void commaYOutsideParensIsIndirectIndexed(String text) {
            final Operand operand = OperandParser.parse(1, text);

            assertEquals(AddressingMode.INDIRECT_Y, operand.mode());
            assertEquals(5, operand.value());
        }

        @Test
        public void unbalancedParenthesesAreRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "($05"));
        }
    }

    @Nested
    class LabelReferences {
        @ParameterizedTest
        @CsvSource({"LOOP", "SUB1", "_start", "MY_LABEL"})
        public void bareIdentifiersAreLabelReferences(String text) {
            final Operand operand = OperandParser.parse(1, text);

            assertTrue(operand.isLabelReference());
            assertEquals(text, operand.label());
        }

        @Test
        public void labelIsUnresolvedUntilInstructionResolverDecidesRelativeOrAbsolute() {
            final Operand operand = OperandParser.parse(1, "LOOP");

            assertEquals(null, operand.mode());
        }
    }

    @Nested
    class MalformedSyntax {
        @Test
        public void punctuationThatIsNeitherALiteralNorAnIdentifierIsRejected() {
            assertThrows(AssemblyException.class, () -> OperandParser.parse(1, "!!!"));
        }

        @Test
        public void exceptionMessageIsAttributedToTheSourceLineNumber() {
            final AssemblyException exception = assertThrows(AssemblyException.class,
                    () -> OperandParser.parse(42, "!!!"));

            assertTrue(exception.getMessage().startsWith("Line 42:"));
        }
    }

    @Property
    void hexLiteralsWithOneOrTwoDigitsAreAlwaysZeroPage(@ForAll("oneOrTwoDigitHex") int value) {
        final Operand operand = OperandParser.parse(1, "$" + Integer.toHexString(value));

        assertEquals(AddressingMode.ZERO_PAGE, operand.mode());
    }

    @Provide
    Arbitrary<Integer> oneOrTwoDigitHex() {
        return Arbitraries.integers().between(0x00, 0xFF);
    }

    @Property
    void hexLiteralsWithThreeOrFourDigitsAreAlwaysAbsolute(@ForAll("threeOrFourDigitHex") int value) {
        final Operand operand = OperandParser.parse(1, "$" + Integer.toHexString(value));

        assertEquals(AddressingMode.ABSOLUTE, operand.mode());
    }

    @Provide
    Arbitrary<Integer> threeOrFourDigitHex() {
        return Arbitraries.integers().between(0x100, 0xFFFF);
    }
}
