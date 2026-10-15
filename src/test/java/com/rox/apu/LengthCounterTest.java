package com.rox.apu;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LengthCounterTest {
    private LengthCounter lengthCounter;

    @BeforeEach
    public void setup(){
        lengthCounter = new LengthCounter();
        lengthCounter.setEnabled(true);
    }

    @ParameterizedTest
    @CsvSource({
            "0,10", "1,254", "2,20", "3,2", "4,40", "5,4", "6,80", "7,6",
            "8,160", "9,8", "10,60", "11,10", "12,14", "13,12", "14,26", "15,14",
            "16,12", "17,16", "18,24", "19,18", "20,48", "21,20", "22,96", "23,22",
            "24,192", "25,24", "26,72", "27,26", "28,16", "29,28", "30,32", "31,30"
    })
    public void loadSetsCounterFromTable(final int index, final int expectedValue){
        lengthCounter.load(index);

        assertEquals(expectedValue, lengthCounter.value());
    }

    @Test
    public void newCounterIsZero(){
        assertTrue(lengthCounter.isZero());
    }

    @Test
    public void loadedCounterIsNotZero(){
        lengthCounter.load(0);

        assertFalse(lengthCounter.isZero());
    }

    @Test
    public void clockHalfFrameDecrementsLoadedCounter(){
        lengthCounter.load(3); //value 2

        lengthCounter.tick();

        assertEquals(1, lengthCounter.value());
    }

    @Test
    public void clockHalfFrameStopsAtZero(){
        lengthCounter.load(3); //value 2

        lengthCounter.tick();
        lengthCounter.tick();
        lengthCounter.tick();

        assertEquals(0, lengthCounter.value());
        assertTrue(lengthCounter.isZero());
    }

    @Test
    public void haltFreezesCounterValue(){
        lengthCounter.load(3); //value 2
        lengthCounter.setHalt(true);

        lengthCounter.tick();
        lengthCounter.tick();

        assertEquals(2, lengthCounter.value());
    }

    @Test
    public void clearingHaltResumesDecrementing(){
        lengthCounter.load(3); //value 2
        lengthCounter.setHalt(true);
        lengthCounter.tick();
        lengthCounter.setHalt(false);

        lengthCounter.tick();

        assertEquals(1, lengthCounter.value());
    }

    @Test
    public void loadOverwritesRegardlessOfCurrentValue(){
        lengthCounter.load(0); //value 10
        lengthCounter.tick();
        lengthCounter.tick();

        lengthCounter.load(3); //value 2, overwrites the decremented 8

        assertEquals(2, lengthCounter.value());
    }

    @Test
    public void disablingImmediatelyZeroesALoadedCounter(){
        lengthCounter.load(0); //value 10

        lengthCounter.setEnabled(false);

        assertEquals(0, lengthCounter.value());
        assertTrue(lengthCounter.isZero());
        assertFalse(lengthCounter.isEnabled());
    }

    @Test
    public void disablingOverridesHalt(){
        lengthCounter.load(0); //value 10
        lengthCounter.setHalt(true);

        lengthCounter.setEnabled(false);

        assertTrue(lengthCounter.isZero());
    }

    @Test
    public void aLoadWhileDisabledIsIgnored(){
        lengthCounter.setEnabled(false);

        lengthCounter.load(0); //value 10, if it weren't ignored

        assertTrue(lengthCounter.isZero());
    }

    @Test
    public void enablingDoesNotChangeTheCounter(){
        lengthCounter.load(0); //value 10

        lengthCounter.setEnabled(true);

        assertEquals(10, lengthCounter.value());
        assertTrue(lengthCounter.isEnabled());
    }

    @Test
    public void startsDisabled(){
        final LengthCounter fresh = new LengthCounter();

        fresh.load(0);

        assertFalse(fresh.isEnabled());
        assertTrue(fresh.isZero(), "a channel is off at power-on until $4015 enables it");
    }
}
