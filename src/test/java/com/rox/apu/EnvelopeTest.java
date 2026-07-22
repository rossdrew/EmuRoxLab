package com.rox.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvelopeTest {
    private Envelope envelope;

    @BeforeEach
    public void setup(){
        envelope = new Envelope();
    }

    @Test
    public void restartThenClockSetsDecayToFifteen(){
        envelope.restart();
        envelope.tick();

        assertEquals(15, envelope.volume());
    }

    @Test
    public void decayCountsDownFromFifteenToZeroWhenReloadValueIsZero(){
        envelope.writeControlRegister(0x00); //reload=0, not const-volume, no loop
        envelope.restart();
        envelope.tick();

        for (int expected = 14; expected >= 0; expected--){
            envelope.tick();
            assertEquals(expected, envelope.volume());
        }
    }

    @Test
    public void decayHoldsAtZeroWithoutLoop(){
        envelope.writeControlRegister(0x00); //reload=0, not const-volume, no loop
        envelope.restart();
        envelope.tick();
        for (int i = 0; i < 15; i++){
            envelope.tick();
        }
        assertEquals(0, envelope.volume());

        envelope.tick();
        envelope.tick();

        assertEquals(0, envelope.volume());
    }

    @Test
    public void decayWrapsToFifteenWhenLoopSetAfterReachingZero(){
        envelope.writeControlRegister(0x20); //reload=0, not const-volume, loop set
        envelope.restart();
        envelope.tick();
        for (int i = 0; i < 15; i++){
            envelope.tick();
        }
        assertEquals(0, envelope.volume());

        envelope.tick();

        assertEquals(15, envelope.volume());
    }

    @Test
    public void nonZeroReloadValueGatesDecayStepsByDividerPeriod(){
        envelope.writeControlRegister(0x02); //reload=2, not const-volume, no loop
        envelope.restart();
        envelope.tick(); //decay=15, divider=2 (start-flag branch)

        envelope.tick(); //divider 2->1
        assertEquals(15, envelope.volume());
        envelope.tick(); //divider 1->0
        assertEquals(15, envelope.volume());
        envelope.tick(); //divider==0: reload divider=2, decay 15->14
        assertEquals(14, envelope.volume());
    }

    @Test
    public void constantVolumeReturnsReloadValueRegardlessOfDecay(){
        envelope.writeControlRegister(0x15); //bit4 set (const-volume), reload=5, no loop

        assertEquals(5, envelope.volume());

        envelope.restart();
        envelope.tick();
        envelope.tick();
        envelope.tick();

        assertEquals(5, envelope.volume());
    }

    @Test
    public void restartClearsPendingDividerCountdown(){
        envelope.writeControlRegister(0x00); //reload=0, not const-volume, no loop
        envelope.restart();
        envelope.tick(); //decay=15
        envelope.tick(); //decay=14
        assertEquals(14, envelope.volume());

        envelope.restart();
        envelope.tick();

        assertEquals(15, envelope.volume());
    }
}
