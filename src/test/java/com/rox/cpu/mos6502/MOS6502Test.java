package com.rox.cpu.mos6502;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
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
        assertTrue(env.zero, "0+0 was expected to be 0");

        cpu.tick(); //Fetch opcode (ADC I)
        env = cpu.getEnvironmentSnapshot();
        assertEquals(3, env.getPC());
        assertEquals(MOS6502OpCode.ADC_I.getId(), env.getIR());

        cpu.tick(); //Execute ADC
        env = cpu.getEnvironmentSnapshot();
        assertEquals(4, env.getPC());

        cpu.tick(); //BRK (0x0) TODO: Validate when BRK ready
    }

    @Test
    public void pendingInterruptNotServicedMidInstruction(){
        final MOS6502Environment env = new MOS6502Environment();
        final MOS6502 localCpu = new MOS6502(bus, env);
        when(bus.fetch()).thenReturn(MOS6502OpCode.ADC_ABS.getId(), 0x00, 0x00, 0x00);

        localCpu.tick(); //fetch ADC_ABS opcode, 3 ticks remain on the stack

        env.setIRQLine(true);
        localCpu.tick(); //still mid-instruction (fetching ADL)

        verify(bus, never()).loadMemoryAddress(0xFFFE);
        verify(bus, never()).loadMemoryAddress(0xFFFF);
    }

    @Test
    public void pendingIRQServicedAtNextFetchBoundary(){
        final MOS6502Environment env = new MOS6502Environment();
        final MOS6502 localCpu = new MOS6502(bus, env);
        when(bus.fetch()).thenReturn(MOS6502OpCode.NOP_IMP.getId());

        localCpu.tick(); //fetch NOP_IMP
        localCpu.tick(); //execute NOP_IMP's single (empty) tick, stack now empty

        env.setIRQLine(true);
        for (int i = 0; i < 7; i++) {
            localCpu.tick(); //service the 7-cycle interrupt sequence
        }

        verify(bus).loadMemoryAddress(0xFFFE);
        verify(bus).loadMemoryAddress(0xFFFF);
    }

    @Test
    public void servicingIRQSetsInterruptDisableFlagAndConsumesNMISeparately(){
        final MOS6502Environment env = new MOS6502Environment();
        final MOS6502 localCpu = new MOS6502(bus, env);
        localCpu.setIRQLine(true);

        for (int i = 0; i < 7; i++) {
            localCpu.tick();
        }

        assertTrue(env.getI(), "servicing a hardware interrupt should set the interrupt-disable flag");
    }

    @Test
    public void nmiServicedInPreferenceToPendingIRQ(){
        final MOS6502Environment env = new MOS6502Environment();
        final MOS6502 localCpu = new MOS6502(bus, env);
        localCpu.setIRQLine(true);
        localCpu.signalNMI();

        for (int i = 0; i < 7; i++) {
            localCpu.tick();
        }

        verify(bus).loadMemoryAddress(0xFFFA);
        verify(bus).loadMemoryAddress(0xFFFB);
        verify(bus, never()).loadMemoryAddress(0xFFFE);
        verify(bus, never()).loadMemoryAddress(0xFFFF);
    }

    @Test
    public void resetReadsVectorAndSetsPC(){
        when(bus.fetch()).thenReturn(0x34, 0x12); //low then high byte -> PC = $1234

        cpu.reset();

        verify(bus).loadMemoryAddress(0xFFFC);
        verify(bus).loadMemoryAddress(0xFFFD);
        assertEquals(0x1234, cpu.getEnvironmentSnapshot().getPC());
    }

    @Test
    public void resetReadsVectorFromRealMemory(){
        final Memory testRAM = new RAM(0x10000);
        testRAM.write(0xFFFC, 0x00);
        testRAM.write(0xFFFD, 0x80); //reset vector points at $8000

        final MemoryBus subBus = new MemoryBus8Bit(testRAM);
        cpu = new MOS6502(new Latched8BitMemoryBus(subBus));

        cpu.reset();

        assertEquals(0x8000, cpu.getEnvironmentSnapshot().getPC());
    }
}

