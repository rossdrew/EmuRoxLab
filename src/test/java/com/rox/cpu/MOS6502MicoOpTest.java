package com.rox.cpu;

import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.cpu.MOS6502MicroOp.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class MOS6502MicoOpTest {
    private MOS6502Environment env;
    private MemoryBus8Bit memoryBus8Bit;
    private RAM ram;
    private LatchedMemoryBus bus;
    private MOS6502ALU alu;

    @BeforeEach
    public void setup(){
        env = new MOS6502Environment();
        ram = new RAM(65536); //2 bytes of addressable memory
        memoryBus8Bit = new MemoryBus8Bit(ram);
        bus = new Latched8BitMemoryBus(memoryBus8Bit);
        alu = mock(MOS6502ALU.class);
    }

//    @Test
//    public void fetchInteractions(){
//        final MOS6502Environment env = mock(MOS6502Environment.class);
//        when(env.pc()).thenReturn(0);
//
//        final RAM ram = mock(RAM.class);//new RAM(1024);
//        when(ram.read(0)).thenReturn(42);
//
//        final MemoryBus8Bit memoryBus8Bit = new MemoryBus8Bit(ram);
//        final LatchedMemoryBus bus = new Latched8BitMemoryBus(memoryBus8Bit);
//        final MOS6502ALU alu = mock(MOS6502ALU.class);
//
//            FETCH.execute(env, bus, alu);
//
//        verify(env, times(1)).pc();
//        verify(ram, times(1)).read(0);
//        verify(env, times(1)).setIR(42);
//    }

    @Test
    public void fetchEndToEnd(){
        ram.write(0, 42);

        FETCH.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(42, env.getIR());
        assertEquals(1, env.getPC());
    }

    @Test
    public void adlFromPCEndToEnd(){
        ram.write(0, 12);

        ADL_FROM_PC.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(1, env.getPC());
        assertEquals(12, env.getADL());
    }

    @Test
    public void adlAddressEndToEnd(){
        ram.write(23, 99);
        env.setADL(23);

        LOAD_ADL_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0, env.getPC());
        assertEquals(23, env.getADL());
        assertEquals(99, bus.fetch());
    }

    @Test
    public void pcAddressEndToEnd(){
        ram.write(0, 33);

        LOAD_PC_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(1, env.getPC());
        assertEquals(33, bus.fetch());
    }

    @Test
    public void xOffSetAddressEndToEnd(){
        ram.write(0x22+5, 65);
        bus.loadMemoryAddress(0x22);

        env.setX(5);

        X_OFFSET_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x22+5, bus.getAddressedMemory());
        assertEquals(65, bus.fetch());
    }

    @Test
    public void pcAddressToADLAddressEndToEnd(){
        ram.write(0, 65);

        ADL_FROM_PC_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(1, env.getPC());
        assertEquals(65, env.getADL());
    }

    @Test
    public void pcAddressToADHAddressEndToEnd(){
        ram.write(0, 56);

        ADH_FROM_PC.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(1, env.getPC());
        assertEquals(56, env.getADH());
    }

    @Test
    public void absAddressAddressEndToEnd(){
        ram.write(0x0101, 43);
        env.setADL(1);
        env.setADH(1);

        ABS_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0, env.getPC());
        assertEquals(0x01, env.getADL());
        assertEquals(0x01, env.getADH());
        assertEquals(43, bus.fetch());
    }

    @Test
    public void adPlusXWithoutCarryyIntoHighByte(){
        memoryBus8Bit.write(0x0101 + 3, 72);
        env.setADL(0x01);
        env.setADH(0x01);
        env.setX(3);

        AD_PLUS_X.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x0101+3, env.getAD());
        assertEquals(72, bus.fetch());
        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusXWithCarryIntoHighByte() {
        memoryBus8Bit.write(0x0202, 72);

        env.setADL(0xFF);
        env.setADH(0x01);
        env.setX(0x03);

        AD_PLUS_X.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x02, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0202, env.getAD());
        assertEquals(72, bus.fetch());
        assertTrue(env.additionalTickPending());
    }

    @Test
    public void adPlusYAddressWithoutCarryIntoHighByte(){
        memoryBus8Bit.write(0x0105 + 5, 27);
        env.setADL(0x05);
        env.setADH(0x01);
        env.setY(5);

        AD_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x0105+5, env.getAD());
        assertEquals(27, bus.fetch());
        assertFalse(env.additionalTickPending());
    }


    @Test
    public void adPlusYAddressWithCarryIntoHighByte() {
        memoryBus8Bit.write(0x0202, 27);

        env.setADL(0xFF);
        env.setADH(0x01);
        env.setY(0x03);

        AD_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x02, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0202, env.getAD());
        assertEquals(27, bus.fetch());
        assertTrue(env.additionalTickPending());
    }

    @Test
    public void adlFetchEndToEnd(){
        memoryBus8Bit.write(10, 77);
        bus.loadMemoryAddress(10);

        ADL_FETCH.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(77, env.getADL());
    }

    @Test
    public void adhIncAndFetchEndToEnd(){
        memoryBus8Bit.write(10, 77);
        memoryBus8Bit.write(11, 88);
        bus.loadMemoryAddress(10);

        ADH_INC_FETCH.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(88, env.getADH());
    }
}
