package com.rox.debug.breakpoints;

import com.rox.debug.labels.MemoryLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddressTextTest {
    private static final List<MemoryLabel> LABELS = List.of(
            MemoryLabel.at("PLAYER_HP", 0x0042),
            MemoryLabel.spanning("SCORE", 0x0300, 0x0302),
            MemoryLabel.at("BEEF", 0x0010));

    @ParameterizedTest
    @ValueSource(strings = {"$C123", "0xC123", "0XC123", "C123", "c123", "  $c123  "})
    void parsesHexWithOrWithoutAPrefix(final String text){
        assertEquals(OptionalInt.of(0xC123), AddressText.parse(text, List.of()));
    }

    @Test
    void parsesShortHex(){
        assertEquals(OptionalInt.of(0x42), AddressText.parse("$42", List.of()));
    }

    @Test
    void parsesALabelAsItsFirstByte(){
        assertEquals(OptionalInt.of(0x0042), AddressText.parse("PLAYER_HP", LABELS));
        assertEquals(OptionalInt.of(0x0300), AddressText.parse(" SCORE ", LABELS));
    }

    @Test
    void aLabelThatReadsAsHexIsTheLabel(){
        assertEquals(OptionalInt.of(0x0010), AddressText.parse("BEEF", LABELS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "$", "$12345", "G000", "player_hp", "$-1", "NOPE"})
    void rejectsAnythingElse(final String text){
        assertEquals(OptionalInt.empty(), AddressText.parse(text, LABELS));
    }

    @Test
    void describesAnAddressAsHex(){
        assertEquals("$C123", AddressText.describe(0xC123, LABELS));
    }

    @Test
    void describesALabelledAddressWithItsName(){
        assertEquals("$0042 PLAYER_HP", AddressText.describe(0x0042, LABELS));
        assertEquals("$0302 SCORE", AddressText.describe(0x0302, LABELS));
        assertEquals("$0303", AddressText.describe(0x0303, LABELS));
        assertEquals("$02FF", AddressText.describe(0x02FF, LABELS));
    }
}
