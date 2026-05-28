package com.rox.cpu;

import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import org.junit.jupiter.api.Test;

import static com.rox.cpu.MOS6502MicroOp.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

public class MOS6502MicoOpTest {
    @Test
    public void mockTestFetch(){
        final MOS6502Environment env = mock(MOS6502Environment.class);
        when(env.pc()).thenReturn(0);

        final RAM ram = mock(RAM.class);//new RAM(1024);
        when(ram.read(0)).thenReturn(42);

        final MemoryBus8Bit memoryBus8Bit = new MemoryBus8Bit(ram);
        final LatchedMemoryBus bus = new Latched8BitMemoryBus(memoryBus8Bit);
        final MOS6502ALU alu = mock(MOS6502ALU.class);

        FETCH.execute(env, bus, alu);

        verify(env, times(1)).pc();
        verify(ram, times(1)).read(0);
        verify(env, times(1)).setIR(42);
    }

    @Test
    public void endToEndTestFetch(){
        final MOS6502Environment env = new MOS6502Environment();

        final RAM ram = new RAM(64);
        ram.write(0, 42);
        ram.write(1, 2);

        final MemoryBus8Bit memoryBus8Bit = new MemoryBus8Bit(ram);
        final LatchedMemoryBus bus = new Latched8BitMemoryBus(memoryBus8Bit);
        final MOS6502ALU alu = mock(MOS6502ALU.class);

        FETCH.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(42, env.getIR());
        assertEquals(1, env.getPC());
    }
}
