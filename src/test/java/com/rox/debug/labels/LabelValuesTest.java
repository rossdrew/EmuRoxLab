package com.rox.debug.labels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelValuesTest {
    private static final MemoryLabel ONE = MemoryLabel.at("rupees", 0x066D);
    private static final MemoryLabel TWO = MemoryLabel.spanning("key_count", 0x0670, 0x0671);

    @Test
    public void formatsEachByteAsHexAndAsDecimal(){
        final int[] memory = new int[0x10000];
        memory[0x0670] = 0x01;
        memory[0x0671] = 0xFF;

        assertEquals("$01 $FF", LabelValues.hex(TWO, address -> memory[address]));
        assertEquals("1 255", LabelValues.decimal(TWO, address -> memory[address]));
        assertEquals("$00", LabelValues.hex(ONE, address -> memory[address]));
        assertEquals("0", LabelValues.decimal(ONE, address -> memory[address]));
    }

    @ParameterizedTest
    @CsvSource({"31, 31", "$1F, 31", "0x1F, 31", "0X1f, 31", "$ff, 255", "255, 255", "0, 0", "$0, 0", "'  7  ', 7", "000255, 255"})
    public void parsesDecimalAndHexBytes(final String text, final int expected){
        assertArrayEquals(new int[]{expected}, LabelValues.parse(ONE, text));
    }

    @Test
    public void parsesOneValuePerByteInOrder(){
        assertArrayEquals(new int[]{1, 0xFF}, LabelValues.parse(TWO, "1 \t$FF"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"        | covers 1 byte - enter 1 value, got 0",
            "1 2       | covers 1 byte - enter 1 value, got 2",
            "-1        | isn't a byte value",
            "+1        | isn't a byte value",
            "$         | isn't a byte value",
            "0x        | isn't a byte value",
            "$GG       | isn't a byte value",
            "twelve    | isn't a byte value",
            "256       | more than a byte holds",
            "$100      | more than a byte holds",
            "1000      | more than a byte holds",
            "99999999999 | more than a byte holds",
    })
    public void rejectsWithAReason(final String text, final String expected){
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LabelValues.parse(ONE, text));

        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    @Test
    public void multiByteCountMismatchIsPluralised(){
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LabelValues.parse(TWO, "1"));

        assertEquals("'key_count' covers 2 bytes - enter 2 values, got 1", e.getMessage());
    }
}
