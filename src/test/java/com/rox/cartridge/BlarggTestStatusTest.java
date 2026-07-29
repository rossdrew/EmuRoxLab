package com.rox.cartridge;

import com.rox.mem.MemoryBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlarggTestStatusTest {
    private MemoryBus bus;

    @BeforeEach
    public void setup(){
        bus = mock(MemoryBus.class);
    }

    private void withSignature(){
        when(bus.read(0x6001)).thenReturn(0xDE);
        when(bus.read(0x6002)).thenReturn(0xB0);
        when(bus.read(0x6003)).thenReturn(0x61);
    }

    @Test
    public void signaturePresentWhenAllThreeBytesMatch(){
        withSignature();

        assertTrue(BlarggTestStatus.isSignaturePresent(bus));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x6001, 0x6002, 0x6003})
    public void signatureAbsentIfAnySingleByteIsWrong(final int wrongAddress){
        withSignature();
        when(bus.read(wrongAddress)).thenReturn(0x00);

        assertFalse(BlarggTestStatus.isSignaturePresent(bus));
    }

    @Test
    public void statusByteReadsFrom6000(){
        when(bus.read(0x6000)).thenReturn(0x02);

        assertEquals(0x02, BlarggTestStatus.statusByte(bus));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x81})
    public void isRunningTrueWhileRunningOrAwaitingReset(final int status){
        when(bus.read(0x6000)).thenReturn(status);

        assertTrue(BlarggTestStatus.isRunning(bus));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x01, 0x02, 0x7F})
    public void isRunningFalseOnceAResultCodeIsPresent(final int status){
        when(bus.read(0x6000)).thenReturn(status);

        assertFalse(BlarggTestStatus.isRunning(bus));
    }

    @Test
    public void needsResetTrueOnlyFor0x81(){
        when(bus.read(0x6000)).thenReturn(0x81);

        assertTrue(BlarggTestStatus.needsReset(bus));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x00, 0x02})
    public void needsResetFalseForOtherStatuses(final int status){
        when(bus.read(0x6000)).thenReturn(status);

        assertFalse(BlarggTestStatus.needsReset(bus));
    }

    @Test
    public void textStopsAtTheNullTerminator(){
        when(bus.read(0x6004)).thenReturn((int) 'O');
        when(bus.read(0x6005)).thenReturn((int) 'K');
        when(bus.read(0x6006)).thenReturn(0);
        when(bus.read(0x6007)).thenReturn((int) 'X'); //should never be read

        assertEquals("OK", BlarggTestStatus.text(bus));
    }

    @Test
    public void emptyTextIsAnImmediateNullTerminator(){
        when(bus.read(0x6004)).thenReturn(0);

        assertEquals("", BlarggTestStatus.text(bus));
    }

    @Test
    public void textIsCappedEvenWithoutATerminator(){
        when(bus.read(anyInt())).thenReturn((int) 'A');

        assertEquals(4096, BlarggTestStatus.text(bus).length());
    }
}
