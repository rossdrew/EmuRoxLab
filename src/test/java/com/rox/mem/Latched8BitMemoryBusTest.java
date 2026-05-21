package com.rox.mem;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.*;

public class Latched8BitMemoryBusTest {
    private MemoryBus subBus;
    private Latched8BitMemoryBus memBus;

    @Provide
    private Arbitrary<Integer> nonByteValue() {
        return Arbitraries.integers()
                .filter(i -> i < 0 || i > 255);
    }

    @BeforeEach
    public void setup(){
        subBus = mock(MemoryBus.class);
        memBus = new Latched8BitMemoryBus(subBus);
    }

    @Property
    public void fetchValidAddress(@ForAll @IntRange(min = 0, max = 255) int address){
        final MemoryBus subBus = mock(MemoryBus.class);
        final Latched8BitMemoryBus memBus = new Latched8BitMemoryBus(subBus);

        memBus.loadMemoryAddress(address);

        memBus.fetch();

        verify(subBus, times(1)).read(address);
    }

    @Property
    public void fetchInvalidAddress(@ForAll("nonByteValue") int address){
        final MemoryBus subBus = mock(MemoryBus.class);
        final Latched8BitMemoryBus memBus = new Latched8BitMemoryBus(subBus);

        memBus.loadMemoryAddress(address);

        memBus.fetch();

        verify(subBus, times(1)).read(0xFF & address);
    }

    @Property
    public void storeValidValueAtValidLocation(@ForAll @IntRange(min = 0, max = 255) int address,
                                               @ForAll @IntRange(min = 0, max = 255) int value){
        final MemoryBus subBus = mock(MemoryBus.class);
        final Latched8BitMemoryBus memBus = new Latched8BitMemoryBus(subBus);

        memBus.loadMemoryAddress(address);

        memBus.store(value);

        verify(subBus, times(1)).write(address, value);
    }

    //TODO invalid storage of values
}
