package com.rox.mem;

import com.rox.Arbitraries;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.*;

public class MemoryBus8BitTest extends Arbitraries {
    private Memory underlyingMemory;
    private MemoryBus8Bit memoryBus;

    @BeforeEach
    @BeforeTry
    public void setup(){
        underlyingMemory = mock(Memory.class);
        memoryBus = new MemoryBus8Bit(underlyingMemory);
    }

    @Property
    public void writeValidValueToValidAddress(@ForAll("byteValues") int address,
                                              @ForAll("byteValues") int value){
        memoryBus.write(address, value);

        verify(underlyingMemory, times(1)).write(address, value);
    }

    @Property
    public void invalidValuesWrap(@ForAll("byteValues") int address,
                                  @ForAll("nonByteValue") int value){
        memoryBus.write(address, value);

        verify(underlyingMemory, times(1)).write(address, 0xFF & value);
    }

    @Property
    public void invalidAddressesWrap(@ForAll("nonByteValue") int address,
                                     @ForAll("byteValues") int value){
        memoryBus.write(address, value);

        verify(underlyingMemory, times(1)).write(address & 0xFF, value);
    }

    @Property
    public void invalidAddressAndValueWrap(@ForAll("nonByteValue") int address,
                                           @ForAll("nonByteValue") int value){
        memoryBus.write(address, value);

        verify(underlyingMemory, times(1)).write(address & 0xFF, value & 0xFF);
    }
}
