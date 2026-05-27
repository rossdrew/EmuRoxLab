package com.rox.cpu;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class MOS6502Test {
    private LatchedMemoryBus bus;
    private MOS6502 cpu;

    @BeforeEach
    public void setup(){
        bus = mock(LatchedMemoryBus.class);
        cpu = new MOS6502(bus);
    }

    @Test
    public void initialTick(){
        when(bus.fetch()).thenReturn(MOS6502.OpCode.ADC_Z.getId());
        
        cpu.tick();

        verify(bus, times(1)).loadMemoryAddress(0);
        verify(bus, times(1)).fetch();
    }

    @Test
    public void realisticRun(){
        final Memory testRAM = new RAM(16);
        testRAM.write(0x00, MOS6502.OpCode.ADC_Z.getId());
        testRAM.write(0x01, 4);
        testRAM.write(0x02, MOS6502.OpCode.ADC_I.getId());
        testRAM.write(0x03, 8);
        final MemoryBus subBus = new MemoryBus8Bit(testRAM);
        final LatchedMemoryBus memoryBus = new Latched8BitMemoryBus(subBus);
        cpu = new MOS6502(memoryBus);

//        cpu.tick();
//        //assert state
//        final MOS6502Environment env = cpu.getEnvironmentSnapshot();
//        assertEquals(101, env.getIR());
//        assertEquals(1, env.getPC());
//        cpu.tick();
//        //assert state
//        assertEquals(2, env.getPC());
//        cpu.tick();
//        //assert state
//        assertEquals(2, env.getPC(), "PC should not move on while doing ALU cycle");
//        cpu.tick();
//        //assert state
//        assertEquals(3, env.getPC());
//        cpu.tick();
//        //assert state
//        assertEquals(4, env.getPC());
//        try {
//            cpu.tick();
//            fail("Expected an exception, 0x0 is not a valid opcode");
//        } catch (Exception e) {
//            //implicit pass?
//        }
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

