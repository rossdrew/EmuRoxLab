package com.rox;

import com.rox.mem.LatchedMemoryBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class MOS6502Test {
    private LatchedMemoryBus bus;
    private MOS6502 cp;

    @BeforeEach
    public void setup(){
        bus = mock(LatchedMemoryBus.class);
        cp = new MOS6502(bus);
    }

    @Test
    public void initialTick(){
        cp.tick();

        verify(bus, times(1)).loadMemoryAddress(0);
        verify(bus, times(1)).fetch();
    }

// Doesn't make sense for the current iteration, since it's not determanistic without a program
//    @Property
//    public void multipleTicks(@ForAll @IntRange(min = 2, max = 1000) int ticks){
//        final MemoryBusLatch bus = mock(MemoryBusLatch.class);
//        final MOS6502 cp = new MOS6502(bus);
//
//        for (int i=0; i<ticks; i++) {
//            cp.tick();
//        }
//
//        verify(bus, times(ticks)).loadMemoryAddress(anyInt());
//        verify(bus, times(ticks)).fetch();
//    }

// Doesn't make sense until arguments are being loaded
//    @Test
//    public void opCode(){
//        when (bus.fetch())
//                .thenReturn(ADC_Z.id()) //ADC Zero Page
//                .thenReturn(10);  //Argument - Address in Zero Page
//
//        cp.tick(); //Load OpCode
//        cp.tick(); //Load Argument
//        cp.tick(); //Read Zero Page
//
//        verify(bus, times(1)).loadMemoryAddress(0);
//        verify(bus, times(1)).loadMemoryAddress(1);
//        verify(bus, times(2)).fetch();
//    }
}

