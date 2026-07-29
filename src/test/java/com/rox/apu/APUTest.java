package com.rox.apu;

import com.rox.mem.MemoryBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.rox.apu.APU.FRAME_COUNTER_ADDRESS;
import static com.rox.apu.APU.STATUS_REGISTER_ADDRESS;
import static com.rox.apu.FrameSequencer.QUARTER_FRAME_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class APUTest {
    private FrameSequencer frameSequencer;
    private PulseChannel pulse1;
    private PulseChannel pulse2;
    private TriangleChannel triangle;
    private NoiseChannel noise;
    private DMCChannel dmc;
    private APU apu;

    @BeforeEach
    public void setup(){
        frameSequencer = mock(FrameSequencer.class);
        pulse1 = mock(PulseChannel.class);
        pulse2 = mock(PulseChannel.class);
        triangle = mock(TriangleChannel.class);
        noise = mock(NoiseChannel.class);
        dmc = mock(DMCChannel.class);
        apu = new APU(frameSequencer, pulse1, pulse2, triangle, noise, dmc);
    }

    @Test
    public void tickClocksTheFrameSequencer(){
        apu.tick();

        verify(frameSequencer, times(1)).tick();
    }

    @Test
    public void tickClocksBothPulseChannelsTriangleNoiseAndDmc(){
        apu.tick();

        verify(pulse1, times(1)).tick();
        verify(pulse2, times(1)).tick();
        verify(triangle, times(1)).tick();
        verify(noise, times(1)).tick();
        verify(dmc, times(1)).tick();
    }

    @Test
    public void constructorRegistersAllFourChannelsAsQuarterAndHalfFrameWatchers(){
        verify(frameSequencer, times(4)).addQuarterFrameWatcher(any());
        verify(frameSequencer, times(4)).addHalfFrameWatcher(any());
    }

    @Test
    public void frameSequencerBoundariesReachAllFourChannels(){
        final APU realFrameSequencerApu = new APU(new FrameSequencer(), pulse1, pulse2, triangle, noise, dmc);

        for (int i = 0; i < QUARTER_FRAME_1; i++){
            realFrameSequencerApu.tick();
        }

        verify(pulse1, times(1)).quarterFrameTick();
        verify(pulse2, times(1)).quarterFrameTick();
        verify(triangle, times(1)).quarterFrameTick();
        verify(noise, times(1)).quarterFrameTick();
        verify(pulse1, never()).halfFrameTick();
        verify(pulse2, never()).halfFrameTick();
        verify(triangle, never()).halfFrameTick();
        verify(noise, never()).halfFrameTick();
    }

    @Test
    public void writingFrameCounterRegisterDelegatesToFrameSequencer(){
        apu.write(FRAME_COUNTER_ADDRESS, 0x80);

        verify(frameSequencer, times(1)).writeControlRegister(0x80);
    }

    @Test
    public void writingPulse1RegistersDelegatesToPulse1Only(){
        apu.write(0x4000, 0x11);
        apu.write(0x4001, 0x22);
        apu.write(0x4002, 0x33);
        apu.write(0x4003, 0x44);

        verify(pulse1, times(1)).writeControlRegister(0x11);
        verify(pulse1, times(1)).writeSweepRegister(0x22);
        verify(pulse1, times(1)).writeTimerLow(0x33);
        verify(pulse1, times(1)).writeTimerHighAndLengthLoad(0x44);
        verify(pulse2, never()).writeControlRegister(anyInt());
        verify(pulse2, never()).writeSweepRegister(anyInt());
        verify(pulse2, never()).writeTimerLow(anyInt());
        verify(pulse2, never()).writeTimerHighAndLengthLoad(anyInt());
    }

    @Test
    public void writingPulse2RegistersDelegatesToPulse2Only(){
        apu.write(0x4004, 0x11);
        apu.write(0x4005, 0x22);
        apu.write(0x4006, 0x33);
        apu.write(0x4007, 0x44);

        verify(pulse2, times(1)).writeControlRegister(0x11);
        verify(pulse2, times(1)).writeSweepRegister(0x22);
        verify(pulse2, times(1)).writeTimerLow(0x33);
        verify(pulse2, times(1)).writeTimerHighAndLengthLoad(0x44);
        verify(pulse1, never()).writeControlRegister(anyInt());
        verify(pulse1, never()).writeSweepRegister(anyInt());
        verify(pulse1, never()).writeTimerLow(anyInt());
        verify(pulse1, never()).writeTimerHighAndLengthLoad(anyInt());
    }

    @Test
    public void writingTriangleRegistersDelegatesToTriangleOnly(){
        apu.write(0x4008, 0x11);
        apu.write(0x400A, 0x22);
        apu.write(0x400B, 0x33);

        verify(triangle, times(1)).writeLinearCounterRegister(0x11);
        verify(triangle, times(1)).writeTimerLow(0x22);
        verify(triangle, times(1)).writeTimerHighAndLengthLoad(0x33);
        verify(pulse1, never()).writeControlRegister(anyInt());
        verify(pulse2, never()).writeControlRegister(anyInt());
    }

    @Test
    public void writingNoiseRegistersDelegatesToNoiseOnly(){
        apu.write(0x400C, 0x11);
        apu.write(0x400E, 0x22);
        apu.write(0x400F, 0x33);

        verify(noise, times(1)).writeControlRegister(0x11);
        verify(noise, times(1)).writeModeAndPeriod(0x22);
        verify(noise, times(1)).writeLengthLoad(0x33);
        verify(pulse1, never()).writeControlRegister(anyInt());
        verify(triangle, never()).writeLinearCounterRegister(anyInt());
    }

    @Test
    public void writingDmcRegistersDelegatesToDmcOnly(){
        apu.write(0x4010, 0x11);
        apu.write(0x4011, 0x22);
        apu.write(0x4012, 0x33);
        apu.write(0x4013, 0x44);

        verify(dmc, times(1)).writeControlRegister(0x11);
        verify(dmc, times(1)).writeDirectLoad(0x22);
        verify(dmc, times(1)).writeSampleAddress(0x33);
        verify(dmc, times(1)).writeSampleLength(0x44);
        verify(pulse1, never()).writeControlRegister(anyInt());
        verify(noise, never()).writeControlRegister(anyInt());
    }

    @Test
    public void writingUnwiredAddressesTouchesNothing(){
        apu.write(0x4009, 0x11); //unused triangle register slot - not a real register
        apu.write(0x400D, 0x11); //unused noise register slot - not a real register

        verify(frameSequencer, never()).writeControlRegister(anyInt());
        verify(pulse1, never()).writeControlRegister(anyInt());
        verify(pulse2, never()).writeControlRegister(anyInt());
        verify(triangle, never()).writeLinearCounterRegister(anyInt());
        verify(noise, never()).writeControlRegister(anyInt());
        verify(dmc, never()).writeControlRegister(anyInt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,  false, false, false, false, false",
            "1,  true,  false, false, false, false", //bit0: pulse1
            "2,  false, true,  false, false, false", //bit1: pulse2
            "4,  false, false, true,  false, false", //bit2: triangle
            "8,  false, false, false, true,  false", //bit3: noise
            "16, false, false, false, false, true",  //bit4: dmc
            "31, true,  true,  true,  true,  true"    //all five bits set
    })
    public void writingStatusRegisterSetsEachChannelsEnabledBitFromTheWrittenValue(
            final int value, final boolean pulse1Enabled, final boolean pulse2Enabled,
            final boolean triangleEnabled, final boolean noiseEnabled, final boolean dmcEnabled){
        apu.write(STATUS_REGISTER_ADDRESS, value);

        verify(pulse1).setEnabled(pulse1Enabled);
        verify(pulse2).setEnabled(pulse2Enabled);
        verify(triangle).setEnabled(triangleEnabled);
        verify(noise).setEnabled(noiseEnabled);
        verify(dmc).setEnabled(dmcEnabled);
    }

    @Test
    public void writingStatusRegisterAlwaysClearsDmcIrqRegardlessOfValue(){
        apu.write(STATUS_REGISTER_ADDRESS, 0x00);

        verify(dmc, times(1)).clearIrq();
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
    public void readingStatusRegisterReportsEachChannelsLengthCounterAndDmcActivity(){
        when(pulse1.isLengthCounterActive()).thenReturn(true);
        when(pulse2.isLengthCounterActive()).thenReturn(false);
        when(triangle.isLengthCounterActive()).thenReturn(true);
        when(noise.isLengthCounterActive()).thenReturn(false);
        when(dmc.isActive()).thenReturn(true);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0x01 | 0x04 | 0x10, result); //pulse1 + triangle + dmc-active
    }

    @Test
    public void readingStatusRegisterReportsTheComplementaryChannelsToo(){
        when(pulse1.isLengthCounterActive()).thenReturn(false);
        when(pulse2.isLengthCounterActive()).thenReturn(true);
        when(triangle.isLengthCounterActive()).thenReturn(false);
        when(noise.isLengthCounterActive()).thenReturn(true);
        when(dmc.isActive()).thenReturn(false);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0x02 | 0x08, result); //pulse2 + noise
    }

    @Test
    public void readingStatusRegisterReportsDmcIrqPendingWithoutClearingIt(){
        when(dmc.isIrqPending()).thenReturn(true);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0x80, result);
        verify(dmc, never()).clearIrq();
    }

    @Test
    public void readingStatusRegisterCombinesFrameIrqAndDmcIrqBits(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(true);
        when(dmc.isIrqPending()).thenReturn(true);

        final int result = apu.read(STATUS_REGISTER_ADDRESS);

        assertEquals(0x40 | 0x80, result);
        verify(frameSequencer, times(1)).clearFrameIrq();
        verify(dmc, never()).clearIrq();
    }

    @Test
    public void isIrqAssertedTrueWhenOnlyFrameIrqPending(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(true);
        when(dmc.isIrqPending()).thenReturn(false);

        assertTrue(apu.isIrqAsserted());
    }

    @Test
    public void isIrqAssertedTrueWhenOnlyDmcIrqPending(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(false);
        when(dmc.isIrqPending()).thenReturn(true);

        assertTrue(apu.isIrqAsserted());
    }

    @Test
    public void isIrqAssertedFalseWhenNeitherSourceIsPending(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(false);
        when(dmc.isIrqPending()).thenReturn(false);

        assertFalse(apu.isIrqAsserted());
    }

    @Test
    public void isIrqAssertedHasNoSideEffectsUnlikeAnActualStatusRead(){
        when(frameSequencer.isFrameIrqPending()).thenReturn(true);

        apu.isIrqAsserted();

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
    public void outputSampleMixesAllFiveChannelOutputs(){
        when(pulse1.outputSample()).thenReturn(15);
        when(pulse2.outputSample()).thenReturn(7);
        when(triangle.outputSample()).thenReturn(11);
        when(noise.outputSample()).thenReturn(4);
        when(dmc.outputSample()).thenReturn(64);

        assertEquals(Mixer.mix(15, 7, 11, 4, 64), apu.outputSample(), 1e-9);
    }

    @Test
    public void defaultConstructorWiresRealComponents(){
        final MemoryBus memoryBus = mock(MemoryBus.class);
        final APU realApu = new APU(memoryBus);

        realApu.tick();
        realApu.write(FRAME_COUNTER_ADDRESS, 0x80); //select 5-step mode, fires an immediate frame-IRQ-free clock

        assertEquals(0, realApu.read(STATUS_REGISTER_ADDRESS), "5-step mode never raises the frame IRQ");
    }
}
