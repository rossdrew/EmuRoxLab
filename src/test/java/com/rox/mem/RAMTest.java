package com.rox.mem;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class RAMTest {
    @Provide
    final Arbitrary<Integer> powersOfTwo() {
        return Arbitraries.integers()
                .between(1, 16)
                .map(power -> 1 << power);
    }

    @Provide
    Arbitrary<Integer> nonPowersOfTwo() {
        return Arbitraries.integers()
                .between(1, 100_000)
                .filter(n -> (n & (n - 1)) != 0);
    }

    @Property(/*seed = x*/)
    void testValidAMSizes(@ForAll("powersOfTwo") int size) {
        assertDoesNotThrow(() -> new RAM(size));
    }

    @Property(/*seed = x*/)
    public void testInvalidRAMSizes(@ForAll("nonPowersOfTwo") int size) {
        assertThrows(RuntimeException.class, () -> new RAM(size));
    }

    @Test
    public void testWriteAndRead(){
        final RAM ram = new RAM(16);
        ram.write(0, 16);

        assertEquals(16, ram.read(0));
    }

    @Test
    public void testMaxValueStorage(){
        final RAM ram = new RAM(16);
        ram.write(0, 255);

        assertEquals(255, ram.read(0));
    }

    @Test
    public void testValueOverflowValueStorage(){
        final RAM ram = new RAM(16);
        ram.write(0, 256);

        assertEquals(0, ram.read(0));
    }

    @Test
    public void testReadFromMaxAddress(){
        final RAM ram = new RAM(16);
        ram.write(16, 42);

        assertEquals(42, ram.read(16));
    }

    @ParameterizedTest(name = "Write to {1}/{0}, wraps to {2}")
    @CsvSource({
            "16,16,0",
            "16,17,1",
            "16,18,2"
    })
    public void testReadFromBeyondMaxAddress(final int size,
                                             final int writeAddress,
                                             final int expectedAddress){
        final RAM ram = new RAM(size);
        ram.write(writeAddress, 42);
        assertEquals(42, ram.read(expectedAddress));
    }
}
