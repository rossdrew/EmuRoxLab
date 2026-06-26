package com.rox.cpu;

import com.rox.Arbitraries;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import net.jqwik.api.ForAll;
import net.jqwik.api.Group;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Nested;

import static com.rox.cpu.MOS6502MicroOp.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

public class MOS6502MicoOpTest extends Arbitraries {
    private MOS6502Environment env;
    private MemoryBus8Bit memoryBus8Bit;
    private RAM ram;
    private LatchedMemoryBus bus;
    private MOS6502ALU alu;

    @BeforeTry  //Property tests
    @BeforeEach //Unit tests
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
    public void adlAddressEndToEnd(){
        ram.write(23, 99);
        env.setADL(23);

        ADDRESS_ADL.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0, env.getPC());
        assertEquals(23, env.getADL());
        assertEquals(99, bus.fetch());
    }

    @Test
    public void pcAddressEndToEnd(){
        ram.write(0, 33);

        ADDRESS_PC.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(1, env.getPC());
        assertEquals(33, bus.fetch());
    }

    @Test
    public void xOffSetAddressEndToEnd(){
        ram.write(0x22, 0x50); //pointer stored at x22
        ram.write(0x55, 65);   //value stored at pointer + x
        bus.loadMemoryAddress(0x22);

        env.setX(5);

        X_OFFSET_ADDRESS.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x50+5, bus.getAddressedMemory());
        assertEquals(65, bus.fetch());
    }

    @Test
    public void absAddressAddressEndToEnd(){
        ram.write(0x0101, 43);
        env.setADL(1);
        env.setADH(1);

        ADDRESS_AD.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0, env.getPC());
        assertEquals(0x01, env.getADL());
        assertEquals(0x01, env.getADH());
        assertEquals(43, bus.fetch());
    }


    @Test
    public void adPlusXWithoutCarryIntoHighByte() {
        memoryBus8Bit.write(0x0101 + 3, 72);

        env.setADL(0x01);
        env.setADH(0x01);
        env.setX(3);

        ADDRESS_AD_PLUS_X.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x01, env.getADH());
        assertEquals(0x04, env.getADL());
        assertEquals(0x0104, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusXWithCarryIntoHighByteDoesNotRequestAdditionalTick() {
        memoryBus8Bit.write(0x0202, 72);

        env.setADL(0xFF);
        env.setADH(0x01);
        env.setX(0x03);

        ADDRESS_AD_PLUS_X.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x02, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0202, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusXWithCarryWrapsHighByte() {
        memoryBus8Bit.write(0x0002, 72);

        env.setADL(0xFF);
        env.setADH(0xFF);
        env.setX(0x03);

        ADDRESS_AD_PLUS_X.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x00, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0002, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusYWithoutCarryIntoHighByte() {
        memoryBus8Bit.write(0x0101 + 3, 72);

        env.setADL(0x01);
        env.setADH(0x01);
        env.setY(3);

        ADDRESS_AD_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x01, env.getADH());
        assertEquals(0x04, env.getADL());
        assertEquals(0x0104, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusYWithCarryIntoHighByteDoesNotRequestAdditionalTick() {
        memoryBus8Bit.write(0x0202, 72);

        env.setADL(0xFF);
        env.setADH(0x01);
        env.setY(0x03);

        ADDRESS_AD_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x02, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0202, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusYWithCarryWrapsHighByte() {
        memoryBus8Bit.write(0x0002, 72);

        env.setADL(0xFF);
        env.setADH(0xFF);
        env.setY(0x03);

        ADDRESS_AD_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);

        assertEquals(0x00, env.getADH());
        assertEquals(0x02, env.getADL());
        assertEquals(0x0002, env.getAD());
        assertEquals(72, bus.fetch());

        assertFalse(env.additionalTickPending());
    }

    @Test
    public void adPlusXWithoutCarryyIntoHighByte(){
        memoryBus8Bit.write(0x0101 + 3, 72);
        env.setADL(0x01);
        env.setADH(0x01);
        env.setX(3);

        ADDRESS_AD_PLUS_X_AND_PAGE_CROSS.execute(env, bus, alu);

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

        ADDRESS_AD_PLUS_X_AND_PAGE_CROSS.execute(env, bus, alu);

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

        ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS.execute(env, bus, alu);

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

        ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS.execute(env, bus, alu);

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

        ADL_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(77, env.getADL());
    }

    @Test
    public void adhIncAndFetchEndToEnd(){
        memoryBus8Bit.write(10, 77);
        memoryBus8Bit.write(11, 88);
        bus.loadMemoryAddress(10);

        NEXT_MEM_TO_ADH.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(88, env.getADH());
    }

    @ParameterizedTest(name = "AD[{0}:{1}] + X({3} = {4}")
    @CsvSource({
          // ADH    ADL    X   Expected
            "0x0,   0x1,   1,  0x02",
            "0x1,   0x1,   1,  0x02",
            "0x0,   0xFF,  2,  0x01",
    })
    public void adlPlusX(final int adh, final int adl, final int x, final int expected){
        env.setADH(adh);
        env.setADL(adl);
        env.setX(x);

        ADL_PLUS_X.execute(env, bus, alu);

        assertEquals(expected, env.getADL());
        assertEquals(expected, env.getAD());
    }

    @ParameterizedTest(name = "PC={0}: Z={1}, N={2}")
    @CsvSource({
            //Value  Z      N
            "0x20, false, false",
            "0x00, true,  false",
            "0x80, false, true",
            "0x7F, false, false"
    })
    public void setFlagsOnA(int value,
                            boolean expectedZero,
                            boolean expectedNegative){
        alu = new MOS6502ALU(env);
        env.setA(value);

        SET_FLAGS_ON_A.execute(env, bus, alu);

        assertEquals(value, env.getA());
        assertEquals(expectedZero, env.getZ());
        assertEquals(expectedNegative, env.getN());
    }

    @Test
    public void nopDoesNothing() {
        NOP.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0, env.getPC());
        assertEquals(0, env.getA());
        assertEquals(0, env.getX());
        assertEquals(0, env.getAD());
    }

    @Test
    public void addressMemPointerEndToEnd() {
        ram.write(0x10, 0x44);
        ram.write(0x44, 0x99);

        bus.loadMemoryAddress(0x10);

        ADDRESS_MEM_POINTER.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x44, bus.getAddressedMemory());
        assertEquals(0x99, bus.fetch());
    }

    @Test
    public void memToAdhEndToEnd() {
        ram.write(0x10, 0x88);
        bus.loadMemoryAddress(0x10);

        ADH_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x88, env.getADH());
    }

    @Test
    public void incPcEndToEnd() {
        env.setPC(0x8000);

        INC_PC.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x8001, env.getPC());
    }

    @Test
    public void incAdlEndToEnd() {
        env.setADH(0x12);
        env.setADL(0x34);

        INC_ADL.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x12, env.getADH());
        assertEquals(0x35, env.getADL());
        assertEquals(0x1235, env.getAD());
    }

    @Test
    public void incAdlWrapsWithoutCarryingToAdh() {
        env.setADH(0x12);
        env.setADL(0xFF);

        INC_ADL.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x12, env.getADH());
        assertEquals(0x00, env.getADL());
        assertEquals(0x1200, env.getAD());
    }

    @Test
    public void memToPclEndToEnd() {
        ram.write(0x10, 0xCD);
        bus.loadMemoryAddress(0x10);
        env.setPC(0xAB00);

        PCL_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0xABCD, env.getPC());
    }

    @Test
    public void memToPchEndToEnd() {
        ram.write(0x10, 0xAB);
        bus.loadMemoryAddress(0x10);
        env.setPC(0x00CD);

        PCH_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0xABCD, env.getPC());
    }

    @Test
    public void adToPcEndToEnd() {
        env.setADH(0x12);
        env.setADL(0x34);

        AD_TO_PC.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x1234, env.getPC());
    }

    @ParameterizedTest(name = "AD[{0}:{1}] + Y({2}) = {3}")
    @CsvSource({
          // ADH,  ADL,  Y,    Expected ADL
            "0x00, 0x01, 0x01, 0x02",
            "0x01, 0x01, 0x01, 0x02",
            "0x00, 0xFF, 0x02, 0x01"
    })
    public void adlPlusY(final int adh, final int adl, final int y, final int expected) {
        env.setADH(adh);
        env.setADL(adl);
        env.setY(y);

        ADL_PLUS_Y.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(expected, env.getADL());
        assertEquals(expected, env.getAD());
    }

    @ParameterizedTest(name = "X={0}: Z={1}, N={2}")
    @CsvSource({
           // Value, Z,     N
            "0x20,   false, false",
            "0x00,   true,  false",
            "0x80,   false, true",
            "0x7F,   false, false"
    })
    public void setFlagsOnX(int value, boolean expectedZero, boolean expectedNegative) {
        alu = new MOS6502ALU(env);
        env.setX(value);

        SET_FLAGS_ON_X.execute(env, bus, alu);

        assertEquals(value, env.getX());
        assertEquals(expectedZero, env.getZ());
        assertEquals(expectedNegative, env.getN());
    }

    @ParameterizedTest(name = "X={0}: Z={1}, N={2}")
    @CsvSource({
            // Value, Z,     N
            "0x20,   false, false",
            "0x00,   true,  false",
            "0x80,   false, true",
            "0x7F,   false, false"
    })
    public void setFlagsOnY(int value, boolean expectedZero, boolean expectedNegative) {
        alu = new MOS6502ALU(env);
        env.setY(value);

        SET_FLAGS_ON_Y.execute(env, bus, alu);

        assertEquals(value, env.getY());
        assertEquals(expectedZero, env.getZ());
        assertEquals(expectedNegative, env.getN());
    }

    @Test
    public void aFromAdEndToEnd() {
        ram.write(0x1234, 0x42);
        bus.loadMemoryAddress(0x1234);

        A_FROM_AD.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x42, env.getA());
    }

    @Test
    public void xFromAdEndToEnd() {
        ram.write(0x1234, 0x42);
        bus.loadMemoryAddress(0x1234);

        X_FROM_AD.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x42, env.getX());
    }

    @Test
    public void yFromAdEndToEnd() {
        ram.write(0x1234, 0x42);
        bus.loadMemoryAddress(0x1234);

        Y_FROM_AD.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x42, env.getY());
    }

    @Test
    public void aFromMemEndToEnd() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        A_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x77, env.getA());
    }

    @Test
    public void xFromMemEndToEnd() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        X_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x77, env.getX());
    }

    @Test
    public void yFromMemEndToEnd() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        Y_FROM_MEM.execute(env, bus, alu);

        verifyNoInteractions(alu);
        assertEquals(0x77, env.getY());
    }

    @Test
    public void adcFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        ADC.execute(env, bus, alu);

        verify(alu).adc(0x77);
    }

    @Test
    public void andFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        AND.execute(env, bus, alu);

        verify(alu).and(0x77);
    }

    @Test
    public void oraFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        ORA.execute(env, bus, alu);

        verify(alu).ora(0x77);
    }

    @Test
    public void eorFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        EOR.execute(env, bus, alu);

        verify(alu).eor(0x77);
    }

    @Test
    public void sbcFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        SBC.execute(env, bus, alu);

        verify(alu).sbc(0x77);
    }

    @Test
    public void cmpFetchesOperandAndDelegatesToAlu() {
        ram.write(0x20, 0x77);
        bus.loadMemoryAddress(0x20);

        CMP.execute(env, bus, alu);

        verify(alu).cmp(0x77);
    }

    @Property
    public void memFromA(@ForAll("byteValue") int address,
                         @ForAll("byteValue") int value){
        env.setA(value);
        bus.loadMemoryAddress(address);

        MEM_FROM_A.execute(env, bus, alu);

        assertEquals(value, bus.fetch());
    }

    @Property
    public void memFromX(@ForAll("byteValue") int address,
                         @ForAll("byteValue") int value){
        env.setX(value);
        bus.loadMemoryAddress(address);

        MEM_FROM_X.execute(env, bus, alu);

        assertEquals(value, bus.fetch());
    }

    @Property
    public void memFromY(@ForAll("byteValue") int address,
                         @ForAll("byteValue") int value){
        env.setY(value);
        bus.loadMemoryAddress(address);

        MEM_FROM_Y.execute(env, bus, alu);

        assertEquals(value, bus.fetch());
    }

    @Nested
    class INX{
        @Test
        public void inxIncrementsX() {
            env.setX(0x20);

            INX.execute(env, bus, alu);

            verifyNoInteractions(alu);

            assertEquals(0x21, env.getX());
        }

        @Test
        public void inxWrapsFromFfToZero() {
            env.setX(0xFF);

            INX.execute(env, bus, alu);

            verifyNoInteractions(alu);

            assertEquals(0x00, env.getX());
        }

        @Test
        public void inxDoesNotChangeAccumulatorYOrPc() {
            env.setA(0x11);
            env.setX(0x20);
            env.setY(0x33);
            env.setPC(0x8000);

            INX.execute(env, bus, alu);

            verifyNoInteractions(alu);

            assertEquals(0x11, env.getA());
            assertEquals(0x21, env.getX());
            assertEquals(0x33, env.getY());
            assertEquals(0x8000, env.getPC());
        }
    }

    @Nested //Junit (unit)
    @Group  //JQwik (properties)
    class PUSH_A {
        @Property //XXX This might be overkill and we should test this extensively elsehwere
        public void pushAStoresAccumulatorAtCurrentStackPointerAddress(@ForAll("validNonWrappingStackPointers") int stackPointer) {
            memoryBus8Bit.write(0x01FF, 0x99);

            env.setA(0x20);
            env.setStackPointer(stackPointer);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x0100 | stackPointer);
            assertEquals(0x20, bus.fetch());

            assertEquals(stackPointer-1, env.getStackPointer(), "Expected SP=" + stackPointer + " to decrement");
        }

        @Test
        public void pushAStoresAccumulatorAtCurrentStackPointerAddress() {
            memoryBus8Bit.write(0x01FF, 0x99);

            env.setA(0x20);
            env.setStackPointer(0xFF);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x01FF);
            assertEquals(0x20, bus.fetch());

            assertEquals(0xFE, env.getStackPointer());
        }

        @Test
        public void pushAUsesStackPageNotZeroPage() {
            memoryBus8Bit.write(0x00FF, 0x11);
            memoryBus8Bit.write(0x01FF, 0x99);

            env.setA(0x20);
            env.setStackPointer(0xFF);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x01FF);
            assertEquals(0x20, bus.fetch());

            bus.loadMemoryAddress(0x00FF);
            assertEquals(0x11, bus.fetch());

            assertEquals(0xFE, env.getStackPointer());
        }

        @Test
        public void pushAWritesBeforeDecrementingStackPointer() {
            memoryBus8Bit.write(0x01FE, 0x11);
            memoryBus8Bit.write(0x01FF, 0x99);

            env.setA(0x20);
            env.setStackPointer(0xFF);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x01FF);
            assertEquals(0x20, bus.fetch());

            bus.loadMemoryAddress(0x01FE);
            assertEquals(0x11, bus.fetch());

            assertEquals(0xFE, env.getStackPointer());
        }

        @Test
        public void pushAWrapsStackPointerFromZeroToFf() {
            memoryBus8Bit.write(0x0100, 0x99);

            env.setA(0x20);
            env.setStackPointer(0x00);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x0100);
            assertEquals(0x20, bus.fetch());

            assertEquals(0xFF, env.getStackPointer());
        }

        @Test
        public void pushAStoresAccumulatorAsEightBitValue() {
            memoryBus8Bit.write(0x01FF, 0x99);

            env.setA(0x1FF);
            env.setStackPointer(0xFF);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            bus.loadMemoryAddress(0x01FF);
            assertEquals(0xFF, bus.fetch());

            assertEquals(0xFE, env.getStackPointer());
        }

        @Test
        public void pushADoesNotChangeAccumulatorIndexRegistersProgramCounterOrFlags() {
            env.setA(0x44);
            env.setX(0x55);
            env.setY(0x66);
            env.setPC(0x8000);
            env.setStackPointer(0xFF);

            env.setZ(true);
            env.setN(false);
            env.setCarry(true);
            env.setV(false);

            PUSH_A.execute(env, bus, alu);

            verifyNoInteractions(alu);

            assertEquals(0x44, env.getA());
            assertEquals(0x55, env.getX());
            assertEquals(0x66, env.getY());
            assertEquals(0x8000, env.getPC());

            assertTrue(env.getZ());
            assertFalse(env.getN());
            assertTrue(env.getCarry());
            assertFalse(env.getV());

            assertEquals(0xFE, env.getStackPointer());
        }
    }
}
