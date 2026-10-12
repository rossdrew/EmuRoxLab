package com.rox.debug.labels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MemoryLabelTest {
    @Test
    public void singleByteLabel(){
        final MemoryLabel rupees = MemoryLabel.at("rupees", 0x066D);

        assertEquals(new MemoryLabel("rupees", 0x066D, 1), rupees);
        assertEquals(0x066D, rupees.end());
    }

    @Test
    public void spanningIsInclusiveAtBothEnds(){
        final MemoryLabel keys = MemoryLabel.spanning("key_count", 0x0670, 0x0671);

        assertEquals(2, keys.length());
        assertEquals(0x0671, keys.end());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "two words", "tab\there", "new\nline"})
    public void namesMustBeOneWord(final String name){
        assertThrows(IllegalArgumentException.class, () -> MemoryLabel.at(name, 0));
    }

    @Test
    public void nameIsRequired(){
        assertThrows(IllegalArgumentException.class, () -> MemoryLabel.at(null, 0));
    }

    @Test
    public void startMustBeAnAddress(){
        assertThrows(IllegalArgumentException.class, () -> MemoryLabel.at("x", -1));
        assertThrows(IllegalArgumentException.class, () -> MemoryLabel.at("x", 0x10000));
        assertDoesNotThrow(() -> MemoryLabel.at("x", 0x0000));
        assertDoesNotThrow(() -> MemoryLabel.at("x", 0xFFFF));
    }

    @Test
    public void lengthMustBePositive(){
        assertThrows(IllegalArgumentException.class, () -> new MemoryLabel("x", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryLabel("x", 0, -1));
    }

    @Test
    public void mustNotRunPastTheTopOfMemory(){
        assertDoesNotThrow(() -> MemoryLabel.spanning("x", 0xFFFE, 0xFFFF));
        assertThrows(IllegalArgumentException.class, () -> new MemoryLabel("x", 0xFFFF, 2));
    }
}
