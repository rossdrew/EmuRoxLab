package com.rox.mem;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;

import static org.mockito.Mockito.*;

public class Latched8BitMemoryBusTest {
    private MemoryBus subBus;
    private Latched8BitMemoryBus memBus;

    @Provide
    Arbitrary<Integer> nonByteValue() {
        return Arbitraries.integers().filter(i -> i < 0 || i > 255);
    }

    @BeforeTry
    public void setup(){
        subBus = mock(MemoryBus.class);
        memBus = new Latched8BitMemoryBus(subBus);
    }

    @Property
    public void fetchValidAddress(@ForAll @IntRange(min = 0, max = 255) int address){
        memBus.loadMemoryAddress(address);
        memBus.fetch();

        verify(subBus, times(1)).read(address);
    }

    @Property
    public void fetchInvalidAddress(@ForAll("nonByteValue") int address){
        memBus.loadMemoryAddress(address);
        memBus.fetch();

        verify(subBus, times(1)).read(0xFF & address);
    }

    @Property
    public void storeValidValueAtValidLocation(@ForAll @IntRange(min = 0, max = 255) int address,
                                               @ForAll @IntRange(min = 0, max = 255) int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        //Neither value should change/wrap
        verify(subBus, times(1)).write(address, value);
    }

    @Property
    public void invalidValueStorageWrapsIt(@ForAll @IntRange(min = 0, max = 255) int address,
                                           @ForAll("nonByteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(address, 0xFF & value);
    }

    @Property
    public void invalidLocationWraps(@ForAll("nonByteValue") int address,
                                     @ForAll @IntRange(min = 0, max = 255) int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(0xFF & address, value);
    }

    @Property
    public void invalidValueAndLocationBothWrap(@ForAll("nonByteValue") int address,
                                                @ForAll("nonByteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(0xFF & address, 0xFF & value);
    }
}
