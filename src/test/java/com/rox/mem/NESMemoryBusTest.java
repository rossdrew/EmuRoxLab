package com.rox.mem;

import com.rox.Arbitraries;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.mem.NESMemoryBus.IO_END_ADDRESS;
import static com.rox.mem.NESMemoryBus.IO_START_ADDRESS;
import static com.rox.mem.NESMemoryBus.STATUS_REGISTER_ADDRESS;
import static net.jqwik.api.Arbitraries.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class NESMemoryBusTest extends Arbitraries {
    private MemoryBus ramBus;
    private MemoryBus io;
    private NESMemoryBus memoryBus;

    @BeforeEach
    @BeforeTry
    public void setup(){
        ramBus = mock(MemoryBus.class);
        io = mock(MemoryBus.class);
        memoryBus = new NESMemoryBus(ramBus, io);
    }

    @Provide
    Arbitrary<Integer> belowIORange() {
        return integers().between(0x0000, IO_START_ADDRESS - 1);
    }

    @Provide
    Arbitrary<Integer> aboveIORange() {
        return integers().between(IO_END_ADDRESS + 1, 0xFFFF);
    }

    @Provide
    Arbitrary<Integer> inIORange() {
        return integers().between(IO_START_ADDRESS, IO_END_ADDRESS);
    }

    @Provide
    Arbitrary<Integer> inIORangeExcludingStatusRegister() {
        return integers().between(IO_START_ADDRESS, IO_END_ADDRESS)
                .filter(address -> address != STATUS_REGISTER_ADDRESS);
    }

    @Property
    public void writeBelowIORangeHitsRamUntouched(@ForAll("belowIORange") int address,
                                                  @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(ramBus, times(1)).write(address, value);
        verifyNoInteractions(io);
    }

    @Property
    public void writeAboveIORangeHitsRamUntouched(@ForAll("aboveIORange") int address,
                                                  @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(ramBus, times(1)).write(address, value);
        verifyNoInteractions(io);
    }

    @Property
    public void writeInIORangeRoutedToIO(@ForAll("inIORange") int address,
                                         @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(io, times(1)).write(address, value);
        verifyNoInteractions(ramBus);
    }

    @Property
    public void readBelowIORangeHitsRamUntouched(@ForAll("belowIORange") int address){
        when(ramBus.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(ramBus, times(1)).read(address);
        verifyNoInteractions(io);
    }

    @Property
    public void readAboveIORangeHitsRamUntouched(@ForAll("aboveIORange") int address){
        when(ramBus.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(ramBus, times(1)).read(address);
        verifyNoInteractions(io);
    }

    @Property
    public void readInIORangeExcludingStatusRegisterStubsZero(@ForAll("inIORangeExcludingStatusRegister") int address){
        assertEquals(0, memoryBus.read(address));
        verifyNoInteractions(io);
        verifyNoInteractions(ramBus);
    }

    @Test
    public void readStatusRegisterRoutedToIO(){
        when(io.read(STATUS_REGISTER_ADDRESS)).thenReturn(0x80);

        assertEquals(0x80, memoryBus.read(STATUS_REGISTER_ADDRESS));
        verify(io, times(1)).read(STATUS_REGISTER_ADDRESS);
        verifyNoInteractions(ramBus);
    }

    @Test
    public void writeJustBelowIORangeHitsRam(){
        memoryBus.write(0x3FFF, 0x11);

        verify(ramBus, times(1)).write(0x3FFF, 0x11);
        verifyNoInteractions(io);
    }

    @Test
    public void writeAtIORangeStartRoutedToIO(){
        memoryBus.write(IO_START_ADDRESS, 0x11);

        verify(io, times(1)).write(IO_START_ADDRESS, 0x11);
        verifyNoInteractions(ramBus);
    }

    @Test
    public void writeAtIORangeEndRoutedToIO(){
        memoryBus.write(IO_END_ADDRESS, 0x11);

        verify(io, times(1)).write(IO_END_ADDRESS, 0x11);
        verifyNoInteractions(ramBus);
    }

    @Test
    public void writeJustAboveIORangeHitsRam(){
        memoryBus.write(0x4018, 0x11);

        verify(ramBus, times(1)).write(0x4018, 0x11);
        verifyNoInteractions(io);
    }
}
