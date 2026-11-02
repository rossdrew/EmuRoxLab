package com.rox.mem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WatchedMemoryBusTest {
    private final MemoryBus memory = mock(MemoryBus.class);
    private final WatchedMemoryBus.AccessListener listener = mock(WatchedMemoryBus.AccessListener.class);
    private final WatchedMemoryBus bus = new WatchedMemoryBus(memory, listener);

    @Test
    void aReadReturnsTheWrappedBusesValueAndReportsIt(){
        when(memory.read(0x0042)).thenReturn(0x07);

        assertEquals(0x07, bus.read(0x0042));

        verify(listener).accessed(0x0042, false, 0x07);
    }

    @Test
    void aWriteReachesTheWrappedBusThenIsReported(){
        bus.write(0x2006, 0x20);

        final var order = inOrder(memory, listener);
        order.verify(memory).write(0x2006, 0x20);
        order.verify(listener).accessed(0x2006, true, 0x20);
    }
}
