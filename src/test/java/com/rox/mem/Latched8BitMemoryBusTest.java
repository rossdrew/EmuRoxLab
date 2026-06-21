package com.rox.mem;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import com.rox.Arbitraries;

import static com.rox.mem.Latched8BitMemoryBus.ADDRESS_MASK;
import static com.rox.mem.Latched8BitMemoryBus.DATA_MASK;
import static org.mockito.Mockito.*;

public class Latched8BitMemoryBusTest extends Arbitraries {
    private MemoryBus subBus;
    private Latched8BitMemoryBus memBus;

    @BeforeTry
    public void setup(){
        subBus = mock(MemoryBus.class);
        memBus = new Latched8BitMemoryBus(subBus);
    }

    @Property
    public void fetchValidAddress(@ForAll("byteValue") int address){
        memBus.loadMemoryAddress(address);
        memBus.fetch();

        verify(subBus, times(1)).read(address);
    }

    @Property
    public void fetchInvalidAddress(@ForAll("nonByteValue") int address){
        memBus.loadMemoryAddress(address);
        memBus.fetch();

        verify(subBus, times(1)).read(address & ADDRESS_MASK);
    }

    @Property
    public void storeValidValueAtValidLocation(@ForAll("byteValue") int address,
                                               @ForAll("byteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        //Neither value should change/wrap
        verify(subBus, times(1)).write(address, value);
    }

    @Property
    public void invalidValueStorageWrapsIt(@ForAll("byteValue") int address,
                                           @ForAll("nonByteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(address, value & DATA_MASK);
    }

    @Property
    public void invalidLocationWraps(@ForAll("nonByteValue") int address,
                                     @ForAll("byteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(address & ADDRESS_MASK, value);
    }

    @Property
    public void invalidValueAndLocationBothWrap(@ForAll("nonByteValue") int address,
                                                @ForAll("nonByteValue") int value){
        memBus.loadMemoryAddress(address);
        memBus.store(value);

        verify(subBus, times(1)).write(address & ADDRESS_MASK, value & DATA_MASK);
    }
}
