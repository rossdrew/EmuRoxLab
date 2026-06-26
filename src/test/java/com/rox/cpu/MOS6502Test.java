package com.rox.cpu;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
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
        when(bus.fetch()).thenReturn(MOS6502OpCode.ADC_Z.getId());
        
        cpu.tick();

        verify(bus, times(1)).loadMemoryAddress(0);
        verify(bus, times(1)).fetch();
    }

    @Test
    public void temporaryFullRun(){
        final Memory testRAM = new RAM(16);
        testRAM.write(0x00, MOS6502OpCode.ADC_Z.getId());
        testRAM.write(0x01, 4);
        testRAM.write(0x02, MOS6502OpCode.ADC_I.getId());
        testRAM.write(0x03, 8);
        final MemoryBus subBus = new MemoryBus8Bit(testRAM);
        final LatchedMemoryBus memoryBus = new Latched8BitMemoryBus(subBus);
        cpu = new MOS6502(memoryBus);

        cpu.tick(); //Fetch opcode (ADC Z)
        MOS6502Environment env = cpu.getEnvironmentSnapshot();
        assertEquals(MOS6502OpCode.ADC_Z.getId(), env.getIR());
        assertEquals(1, env.getPC());

        cpu.tick(); //Fetch operand (ZP address)
        env = cpu.getEnvironmentSnapshot();
        assertEquals(2, env.getPC());
        assertEquals(4, env.getADL());

        cpu.tick(); //Execute ADC
        env = cpu.getEnvironmentSnapshot();
        assertEquals(2, env.getPC(), "PC should not move on while doing ALU cycle");
        assertTrue(env.z, "0+0 was expected to be 0");

        cpu.tick(); //Fetch opcode (ADC I)
        env = cpu.getEnvironmentSnapshot();
        assertEquals(3, env.getPC());
        assertEquals(MOS6502OpCode.ADC_I.getId(), env.getIR());

        cpu.tick(); //Execute ADC
        env = cpu.getEnvironmentSnapshot();
        assertEquals(4, env.getPC());

        cpu.tick(); //BRK (0x0) TODO: Validate when BRK ready
    }
}

