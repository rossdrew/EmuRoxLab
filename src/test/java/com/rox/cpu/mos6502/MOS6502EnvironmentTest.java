package com.rox.cpu.mos6502;

import com.rox.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MOS6502EnvironmentTest extends Arbitraries {
    private MOS6502Environment env;

    @BeforeEach
    public void setup(){
        env = new MOS6502Environment();
    }

    @Test
    public void irqLineIsNotAssertedByDefault(){
        assertFalse(env.isIRQLineAsserted());
        assertFalse(env.hasPendingInterrupt());
    }

    @Test
    public void assertingIRQLineIsVisible(){
        env.setIRQLine(true);
        assertTrue(env.isIRQLineAsserted());
    }

    @Test
    public void deassertingIRQLineClearsIt(){
        env.setIRQLine(true);
        env.setIRQLine(false);
        assertFalse(env.isIRQLineAsserted());
    }

    @Test
    public void irqIsPendingWhenAssertedAndInterruptDisableFlagClear(){
        env.setIRQLine(true);
        env.setI(false);
        assertTrue(env.hasPendingInterrupt());
    }

    @Test
    public void irqIsMaskedByInterruptDisableFlag(){
        env.setIRQLine(true);
        env.setI(true);
        assertFalse(env.hasPendingInterrupt());
    }

    @Test
    public void nmiIsPendingRegardlessOfInterruptDisableFlag(){
        env.setI(true);
        env.signalNMI();
        assertTrue(env.hasPendingInterrupt());
    }

    @Test
    public void nmiIsConsumedOnlyOnce(){
        env.signalNMI();

        assertTrue(env.consumeNMI());
        assertFalse(env.consumeNMI(), "NMI should not refire once consumed");
        assertFalse(env.hasPendingInterrupt());
    }

    @Test
    public void consumingNMIWhenNoneWasSignalledReturnsFalse(){
        assertFalse(env.consumeNMI());
    }

    @Test
    public void nmiTakesPriorityOverIRQWhenBothPending(){
        env.setIRQLine(true);
        env.setI(false);
        env.signalNMI();

        assertTrue(env.consumeNMI(), "NMI should be reported ahead of the still-pending IRQ");
        assertTrue(env.hasPendingInterrupt(), "IRQ line is still asserted and I is clear, so an interrupt is still pending");
    }

    @Property
    public void hasPendingInterruptMatchesTruthTable(@ForAll boolean irqAsserted,
                                                     @ForAll boolean interruptDisable,
                                                     @ForAll boolean nmiSignalled){
        final MOS6502Environment localEnv = new MOS6502Environment();
        localEnv.setIRQLine(irqAsserted);
        localEnv.setI(interruptDisable);
        if (nmiSignalled) {
            localEnv.signalNMI();
        }

        final boolean expected = nmiSignalled || (irqAsserted && !interruptDisable);
        assertEquals(expected, localEnv.hasPendingInterrupt());
    }
}
