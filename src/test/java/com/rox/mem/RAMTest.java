package com.rox.mem;

import com.rox.Arbitraries;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class RAMTest extends Arbitraries {
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

    @Test
    public void snapshotCapturesEveryByte(){
        final RAM ram = new RAM(4);
        ram.write(0, 0x11);
        ram.write(3, 0xFF);

        assertArrayEquals(new int[]{0x11, 0, 0, 0xFF}, ram.snapshot());
    }

    @Test
    public void snapshotIsACopyNotALiveView(){
        final RAM ram = new RAM(4);
        final int[] snapshot = ram.snapshot();

        ram.write(0, 0x42);

        assertEquals(0, snapshot[0]);
    }

    @Test
    public void restorePutsBackASnapshot(){
        final RAM ram = new RAM(4);
        ram.write(1, 0x22);
        final int[] snapshot = ram.snapshot();
        ram.write(1, 0x99);
        ram.write(2, 0x33);

        ram.restore(snapshot);

        assertArrayEquals(new int[]{0, 0x22, 0, 0}, ram.snapshot());
    }

    @Test
    public void restoreCopiesRatherThanKeepingTheCallersArray(){
        final RAM ram = new RAM(4);
        final int[] contents = {1, 2, 3, 4};

        ram.restore(contents);
        contents[0] = 0x77;

        assertEquals(1, ram.read(0));
    }

    @Test
    public void restoreMasksValuesToBytes(){
        final RAM ram = new RAM(2);

        ram.restore(new int[]{0x1AB, 0});

        assertEquals(0xAB, ram.read(0));
    }

    @Test
    public void restoreRejectsTheWrongSize(){
        final RAM ram = new RAM(4);

        assertThrows(IllegalArgumentException.class, () -> ram.restore(new int[8]));
    }

    @Test
    public void checkRestorableRejectsTheWrongSizeWithoutChangingAnything(){
        final RAM ram = new RAM(4);
        ram.write(0, 0x12);

        ram.checkRestorable(new int[4]);
        assertThrows(IllegalArgumentException.class, () -> ram.checkRestorable(new int[8]));

        assertEquals(0x12, ram.read(0));
    }
}
