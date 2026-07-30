package com.rox.mem;

import com.rox.Arbitraries;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.rox.mem.NESMemoryBus.CARTRIDGE_START_ADDRESS;
import static com.rox.mem.NESMemoryBus.CONTROLLER_1_ADDRESS;
import static com.rox.mem.NESMemoryBus.CONTROLLER_2_ADDRESS;
import static com.rox.mem.NESMemoryBus.IO_END_ADDRESS;
import static com.rox.mem.NESMemoryBus.IO_START_ADDRESS;
import static com.rox.mem.NESMemoryBus.OAM_DMA_ADDRESS;
import static com.rox.mem.NESMemoryBus.PPU_END_ADDRESS;
import static com.rox.mem.NESMemoryBus.PPU_START_ADDRESS;
import static com.rox.mem.NESMemoryBus.STATUS_REGISTER_ADDRESS;
import static net.jqwik.api.Arbitraries.integers;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class NESMemoryBusTest extends Arbitraries {
    private MemoryBus ramBus;
    private MemoryBus io;
    private MemoryBus cartridge;
    private OamDmaBus ppu;
    private NESMemoryBus memoryBus;

    @BeforeEach
    @BeforeTry
    public void setup(){
        ramBus = mock(MemoryBus.class);
        io = mock(MemoryBus.class);
        cartridge = mock(MemoryBus.class);
        ppu = mock(OamDmaBus.class);
        memoryBus = new NESMemoryBus(ramBus, io, cartridge, ppu);
    }

    @Provide
    Arbitrary<Integer> belowPpuRange() {
        return integers().between(0x0000, PPU_START_ADDRESS - 1);
    }

    @Provide
    Arbitrary<Integer> inPpuRange() {
        return integers().between(PPU_START_ADDRESS, PPU_END_ADDRESS);
    }

    @Provide
    Arbitrary<Integer> aboveIORangeBelowCartridgeRange() {
        return integers().between(IO_END_ADDRESS + 1, CARTRIDGE_START_ADDRESS - 1);
    }

    @Provide
    Arbitrary<Integer> inCartridgeRange() {
        return integers().between(CARTRIDGE_START_ADDRESS, 0xFFFF);
    }

    @Provide
    Arbitrary<Integer> inIORange() {
        //excludes $4016: unlike the rest of the I/O range (including $4017, the APU's frame counter
        //on write), the joypad strobe never reaches the device bus - see writeController1StrobeIsANoOp
        return integers().between(IO_START_ADDRESS, IO_END_ADDRESS)
                .filter(address -> address != CONTROLLER_1_ADDRESS);
    }

    @Provide
    Arbitrary<Integer> inIORangeExcludingStatusRegister() {
        return integers().between(IO_START_ADDRESS, IO_END_ADDRESS)
                .filter(address -> address != STATUS_REGISTER_ADDRESS);
    }

    @Provide
    Arbitrary<Integer> inIORangeExcludingOamDma() {
        //also excludes $4016 for the same reason as inIORange() above - the joypad strobe is a no-op,
        //not a write that reaches the device bus, so this test's "always routed to io" claim can't cover it
        return integers().between(IO_START_ADDRESS, IO_END_ADDRESS)
                .filter(address -> address != OAM_DMA_ADDRESS && address != CONTROLLER_1_ADDRESS);
    }

    @Property
    public void writeBelowPpuRangeHitsRamUntouched(@ForAll("belowPpuRange") int address,
                                                  @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(ramBus, times(1)).write(address, value);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Property
    public void writeInPpuRangeRoutedToPpu(@ForAll("inPpuRange") int address,
                                           @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(ppu, times(1)).write(address, value);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
    }

    @Property
    public void writeAboveIORangeBelowCartridgeRangeHitsRamUntouched(
            @ForAll("aboveIORangeBelowCartridgeRange") int address, @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(ramBus, times(1)).write(address, value);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Property
    public void writeInIORangeRoutedToIO(@ForAll("inIORangeExcludingOamDma") int address,
                                         @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(io, times(1)).write(address, value);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Property
    public void writeInCartridgeRangeRoutedToCartridge(@ForAll("inCartridgeRange") int address,
                                                        @ForAll("byteValue") int value){
        memoryBus.write(address, value);

        verify(cartridge, times(1)).write(address, value);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(ppu);
    }

    @Property
    public void readBelowPpuRangeHitsRamUntouched(@ForAll("belowPpuRange") int address){
        when(ramBus.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(ramBus, times(1)).read(address);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Property
    public void readInPpuRangeRoutedToPpu(@ForAll("inPpuRange") int address){
        when(ppu.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(ppu, times(1)).read(address);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
    }

    @Property
    public void readAboveIORangeBelowCartridgeRangeHitsRamUntouched(
            @ForAll("aboveIORangeBelowCartridgeRange") int address){
        when(ramBus.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(ramBus, times(1)).read(address);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Property
    public void readInCartridgeRangeRoutedToCartridge(@ForAll("inCartridgeRange") int address){
        when(cartridge.read(address)).thenReturn(0x42);

        assertEquals(0x42, memoryBus.read(address));
        verify(cartridge, times(1)).read(address);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(ppu);
    }

    @Property
    public void readInIORangeExcludingStatusRegisterStubsZero(@ForAll("inIORangeExcludingStatusRegister") int address){
        assertEquals(0, memoryBus.read(address));
        verifyNoInteractions(io);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void readStatusRegisterRoutedToIO(){
        when(io.read(STATUS_REGISTER_ADDRESS)).thenReturn(0x80);

        assertEquals(0x80, memoryBus.read(STATUS_REGISTER_ADDRESS));
        verify(io, times(1)).read(STATUS_REGISTER_ADDRESS);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void readController1ReportsNoButtonsPressedWithoutTouchingAnyBus(){
        assertEquals(0, memoryBus.read(CONTROLLER_1_ADDRESS));
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void readController2ReportsNoButtonsPressedWithoutTouchingAnyBus(){
        assertEquals(0, memoryBus.read(CONTROLLER_2_ADDRESS));
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    /** Unlike every other I/O-range write, the joypad strobe has no meaning to the APU (or anything else) - it's a pure no-op. */
    @Test
    public void writeController1StrobeIsANoOp(){
        memoryBus.write(CONTROLLER_1_ADDRESS, 0x01);

        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    /** $4017 is genuinely the APU's frame counter register on write (unlike $4016) - must keep reaching it. */
    @Test
    public void writeController2AddressStillReachesIOAsTheApuFrameCounter(){
        memoryBus.write(CONTROLLER_2_ADDRESS, 0x40);

        verify(io, times(1)).write(CONTROLLER_2_ADDRESS, 0x40);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeJustBelowPpuRangeHitsRam(){
        memoryBus.write(PPU_START_ADDRESS - 1, 0x11);

        verify(ramBus, times(1)).write(PPU_START_ADDRESS - 1, 0x11);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeAtPpuRangeStartRoutedToPpu(){
        memoryBus.write(PPU_START_ADDRESS, 0x11);

        verify(ppu, times(1)).write(PPU_START_ADDRESS, 0x11);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
    }

    @Test
    public void writeAtPpuRangeEndRoutedToPpu(){
        memoryBus.write(PPU_END_ADDRESS, 0x11);

        verify(ppu, times(1)).write(PPU_END_ADDRESS, 0x11);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
    }

    @Test
    public void writeJustAbovePpuRangeRoutedToIO(){
        memoryBus.write(PPU_END_ADDRESS + 1, 0x11);

        verify(io, times(1)).write(PPU_END_ADDRESS + 1, 0x11);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeJustAboveIORangeHitsRam(){
        memoryBus.write(0x4018, 0x11);

        verify(ramBus, times(1)).write(0x4018, 0x11);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeJustBelowCartridgeRangeHitsRam(){
        memoryBus.write(CARTRIDGE_START_ADDRESS - 1, 0x11);

        verify(ramBus, times(1)).write(CARTRIDGE_START_ADDRESS - 1, 0x11);
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeAtCartridgeRangeStartRoutedToCartridge(){
        memoryBus.write(CARTRIDGE_START_ADDRESS, 0x11);

        verify(cartridge, times(1)).write(CARTRIDGE_START_ADDRESS, 0x11);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writeAtTopOfAddressSpaceRoutedToCartridge(){
        memoryBus.write(0xFFFF, 0x11);

        verify(cartridge, times(1)).write(0xFFFF, 0x11);
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
        verifyNoInteractions(ppu);
    }

    @Test
    public void writingOamDmaRegisterReadsThePageFromRamAndHandsItToThePpuBus(){
        for (int i = 0; i < 0x100; i++){
            when(ramBus.read(0x0200 + i)).thenReturn(i);
        }

        memoryBus.write(OAM_DMA_ADDRESS, 0x02);

        final int[] expectedPage = new int[0x100];
        for (int i = 0; i < 0x100; i++){
            expectedPage[i] = i;
        }
        final ArgumentCaptor<int[]> captor = ArgumentCaptor.forClass(int[].class);
        verify(ppu, times(1)).writeOamDma(captor.capture());
        assertArrayEquals(expectedPage, captor.getValue());
        verifyNoInteractions(io);
        verifyNoInteractions(cartridge);
    }

    @Test
    public void writingOamDmaRegisterDoesNotAlsoRouteThroughTheNormalPpuRegisterWrite(){
        memoryBus.write(OAM_DMA_ADDRESS, 0x02);

        verify(ppu, never()).write(anyInt(), anyInt());
    }

    @Test
    public void writingOamDmaRegisterSourcesThePageFromTheCartridgeWhenThatIsWhereItMaps(){
        memoryBus.write(OAM_DMA_ADDRESS, 0x80); //$8000 falls in cartridge range

        final ArgumentCaptor<int[]> captor = ArgumentCaptor.forClass(int[].class);
        verify(ppu, times(1)).writeOamDma(captor.capture());
        verify(cartridge, times(0x100)).read(anyInt());
        verifyNoInteractions(ramBus);
        verifyNoInteractions(io);
    }
}
