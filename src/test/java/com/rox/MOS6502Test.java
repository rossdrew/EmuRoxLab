package com.rox;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

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
}
