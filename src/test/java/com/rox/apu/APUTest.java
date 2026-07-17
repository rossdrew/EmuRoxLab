package com.rox.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.apu.APU.FRAME_COUNTER_ADDRESS;
import static com.rox.apu.APU.STATUS_REGISTER_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class APUTest {
    private FrameSequencer frameSequencer;
    private APU apu;

    @BeforeEach
    public void setup(){
        frameSequencer = mock(FrameSequencer.class);
        apu = new APU(frameSequencer);
    }

    @Test
    public void tickClocksTheFrameSequencer(){
        apu.tick();

        verify(frameSequencer, times(1)).clock();
    }

    @Test
    public void writingFrameCounterRegisterDelegatesToFrameSequencer(){
        apu.write(FRAME_COUNTER_ADDRESS, 0x80);

        verify(frameSequencer, times(1)).writeControlRegister(0x80);
    }

    @Test
    public void writingOtherAddressesDoesNotTouchFrameSequencer(){
        apu.write(0x4000, 0x11);
        apu.write(STATUS_REGISTER_ADDRESS, 0x1F);

        verify(frameSequencer, never()).writeControlRegister(anyInt());
    }

    @Test
    public void readingStatusRegisterReturnsFrameIrqBitAndClearsItWhenPending(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(true);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0x40, result);
        verify(frameSequencer, times(1)).clearFrameIrq();
    }

    @Test
    public void readingStatusRegisterReturnsZeroAndDoesNotClearWhenNotPending(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(false);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0, result);
        verify(frameSequencer, never()).clearFrameIrq();
    }

    @Test
    public void readingOtherAddressesReturnsZeroWithoutTouchingFrameSequencer(){
        final int result = apu.read(0x4000);

        assertEquals(0, result);
        verify(frameSequencer, never()).isFrameIrqPending();
        verify(frameSequencer, never()).clearFrameIrq();
    }

    @Test
    public void defaultConstructorWiresARealFrameSequencer(){
        final APU realApu = new APU();

        realApu.tick();
        realApu.write(FRAME_COUNTER_ADDRESS, 0x80); //select 5-step mode, fires an immediate frame-IRQ-free clock

        assertEquals(0, realApu.read(STATUS_REGISTER_ADDRESS), "5-step mode never raises the frame IRQ");
    }
}
