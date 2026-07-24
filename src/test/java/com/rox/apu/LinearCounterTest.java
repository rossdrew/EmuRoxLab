package com.rox.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LinearCounterTest {
    private LinearCounter linearCounter;

    @BeforeEach
    public void setup(){
        linearCounter = new LinearCounter();
    }

    @Test
    public void startsAtZero(){
        assertTrue(linearCounter.isZero());
    }

    @Test
    public void requestReloadThenTickLoadsTheReloadValue(){
        linearCounter.writeControlRegister(0x05); //no control flag, reload=5
        linearCounter.requestReload();

        linearCounter.tick();

        assertFalse(linearCounter.isZero());
    }

    @Test
    public void withoutAPendingReloadTickDecrementsTowardsZero(){
        linearCounter.writeControlRegister(0x02); //no control flag, reload=2
        linearCounter.requestReload();
        linearCounter.tick(); //loads 2, clears the (non-control) reload flag

        linearCounter.tick();
        assertFalse(linearCounter.isZero());

        linearCounter.tick();
        assertTrue(linearCounter.isZero());
    }

    @Test
    public void decrementingHoldsAtZeroOnceReached(){
        linearCounter.writeControlRegister(0x00); //no control flag, reload=0
        linearCounter.requestReload();
        linearCounter.tick(); //loads 0

        linearCounter.tick();
        linearCounter.tick();

        assertTrue(linearCounter.isZero());
    }

    @Test
    public void withoutControlFlagReloadFlagIsClearedAfterOneTick(){
        linearCounter.writeControlRegister(0x03); //no control flag, reload=3
        linearCounter.requestReload();
        linearCounter.tick(); //loads 3, clears the pending reload flag since control flag is clear

        linearCounter.tick(); //no pending reload now, so this decrements (3 -> 2) instead of reloading

        assertFalse(linearCounter.isZero());
    }

    @Test
    public void withControlFlagSetReloadFlagPersistsAcrossTicks(){
        linearCounter.writeControlRegister(0x83); //control flag set, reload=3
        linearCounter.requestReload();
        linearCounter.tick(); //loads 3, reload flag survives (control flag set)

        linearCounter.tick(); //reload flag still set -> reloads to 3 again rather than decrementing

        assertFalse(linearCounter.isZero());
    }

    @Test
    public void writingControlRegisterUpdatesReloadValueUsedByNextReload(){
        linearCounter.writeControlRegister(0x00); //reload=0
        linearCounter.writeControlRegister(0x07); //reload=7 (overwrites)
        linearCounter.requestReload();

        linearCounter.tick();

        assertFalse(linearCounter.isZero());
    }

    @Test
    public void tickWithoutAnyReloadRequestedAndCounterAlreadyZeroStaysZero(){
        linearCounter.writeControlRegister(0x09); //reload=9, never requested

        linearCounter.tick();

        assertTrue(linearCounter.isZero());
    }
}
