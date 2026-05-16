package com.rox;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static com.rox.MOS6502.OpCode.ADC_Z;
import static org.mockito.Mockito.*;

public class MOS6502Test {
    @Test
    public void initialTick(){
        final MemoryBus bus = mock(MemoryBus.class);
        final MOS6502 cp = new MOS6502(bus);

        cp.tick();

        verify(bus, times(1)).read(0);
    }

    @Property
    public void multipleTicks(@ForAll @IntRange(min = 0, max = 1000) int ticks){
        final MemoryBus bus = mock(MemoryBus.class);
        final MOS6502 cp = new MOS6502(bus);

        for (int i=0; i<ticks; i++) {
            cp.tick();
        }

        verify(bus, times(ticks)).read(anyInt());
    }

    @Test
    public void opCode(){
        final MemoryBus bus = mock(MemoryBus.class);
        when (bus.read(0)).thenReturn(ADC_Z.id());//ADC Zero Page
        when (bus.read(1)).thenReturn(10);//Argument - Address in Zero Page

        final MOS6502 cp = new MOS6502(bus);

        cp.tick(); //Load OpCode
        cp.tick(); //Load Argument
        cp.tick(); //Read Zero Page

        verify(bus, times(1)).read(0);
    }
}

