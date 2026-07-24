package com.rox.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NoiseChannelTest {
    private Envelope mockEnvelope;
    private LengthCounter mockLengthCounter;
    private NoiseChannel channel;

    @BeforeEach
    public void setup(){
        mockEnvelope = mock(Envelope.class);
        mockLengthCounter = mock(LengthCounter.class);
        channel = new NoiseChannel(mockEnvelope, mockLengthCounter);
    }

    @Test
    public void writingControlRegisterSetsLengthHaltAndDelegatesToEnvelope(){
        channel.writeControlRegister(0x2A); //bit5 set (halt), rest is envelope's business

        verify(mockLengthCounter).setHalt(true);
        verify(mockEnvelope).writeControlRegister(0x2A);
    }

    @Test
    public void writingControlRegisterWithHaltBitClearSetsHaltFalse(){
        channel.writeControlRegister(0x00); //bit5 clear

        verify(mockLengthCounter).setHalt(false);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    public void writingModeAndPeriodSelectsPeriodFromNtscTable(final int periodIndex){
        channel.writeModeAndPeriod(periodIndex); //mode clear, period index under test

        assertEquals(NoiseChannel.NTSC_NOISE_PERIODS[periodIndex], channel.currentTimerPeriod());
    }

    @Test
    public void writingModeAndPeriodSetsAndClearsModeFlag(){
        channel.writeModeAndPeriod(0x80); //mode bit set, period index 0

        assertTrue(channel.currentMode());

        channel.writeModeAndPeriod(0x00); //mode bit clear, period index 0

        assertFalse(channel.currentMode());
    }

    @Test
    public void writingLengthLoadLoadsLengthCounterAndRestartsEnvelope(){
        channel.writeLengthLoad(5 << 3); //length index 5

        verify(mockLengthCounter).load(5);
        verify(mockEnvelope).restart();
    }

    @Test
    public void quarterFrameTickClocksOnlyTheEnvelope(){
        channel.quarterFrameTick();

        verify(mockEnvelope).tick();
        verify(mockLengthCounter, never()).tick();
    }

    @Test
    public void halfFrameTickClocksOnlyTheLengthCounter(){
        channel.halfFrameTick();

        verify(mockLengthCounter).tick();
        verify(mockEnvelope, never()).tick();
    }

    @Test
    public void lengthCounterZeroSilencesOutputRegardlessOfShiftRegister(){
        when(mockLengthCounter.isZero()).thenReturn(true);

        assertEquals(0, channel.outputSample());
        verify(mockEnvelope, never()).volume();
    }

    @Test
    public void outputIsSilentImmediatelyAfterConstructionBecauseTheShiftRegisterResetsWithBitZeroSet(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockEnvelope.volume()).thenReturn(9);

        assertEquals(1, channel.currentShiftRegister(), "hardware resets the 15-bit register to 1");
        assertEquals(0, channel.outputSample());
    }

    @Test
    public void outputSampleReturnsEnvelopeVolumeOnceTheShiftRegisterClocksBitZeroClear(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockEnvelope.volume()).thenReturn(9);
        channel.writeModeAndPeriod(0); //period=4, mode clear - irrelevant here, just needs a reload to happen

        channel.tick(); //a fresh channel's very first tick() already does real work (see below) - shift
                         //register 0x0001 -> 0x4000, bit0 now clear

        assertEquals(9, channel.outputSample());
    }

    @Test
    public void tickIsParityGatedLikePulseSoOnlyEveryOtherCallClocksTheShiftRegister(){
        final NoiseChannel realChannel = new NoiseChannel();
        realChannel.writeModeAndPeriod(0); //period=4

        //apuCycleParity starts false; the first tick() flips it true and (per the "if (!apuCycleParity)
        //return" guard) that's the call that does NOT skip - so, same as PulseChannel, the very first
        //tick() on a fresh channel already performs real work.
        realChannel.tick();
        assertEquals(0x4000, realChannel.currentShiftRegister());

        realChannel.tick(); //flips parity back - this call is the one that's skipped
        assertEquals(0x4000, realChannel.currentShiftRegister(), "should not have clocked again on the even call");
    }

    @Test
    public void longModeFeedbackTapDivergesFromShortModeAfterTenClocks(){
        //golden values precomputed independently: both modes agree for the first 9 clocks (the
        //single seed bit hasn't reached either tap yet), then diverge at the 10th once it reaches
        //bit 6 (short mode's tap) before bit 1 (long mode's tap).
        final NoiseChannel longModeChannel = new NoiseChannel();
        longModeChannel.writeModeAndPeriod(0); //mode clear (long), period=4
        final NoiseChannel shortModeChannel = new NoiseChannel();
        shortModeChannel.writeModeAndPeriod(0x80); //mode set (short), period=4

        clockRealTimes(longModeChannel, 10);
        clockRealTimes(shortModeChannel, 10);

        assertEquals(0x0020, longModeChannel.currentShiftRegister());
        assertEquals(0x4020, shortModeChannel.currentShiftRegister());
    }

    /**
     * Drives exactly {@code clocks} real shift-register clocks, regardless of the timer period or
     * tick()'s parity gating - mirrors PulseChannelTest's advanceOneSequenceStep in spirit: just keep
     * calling tick() until the observable state changes, that many times.
     */
    private static void clockRealTimes(final NoiseChannel channel, final int clocks){
        for (int i = 0; i < clocks; i++){
            final int before = channel.currentShiftRegister();
            while (channel.currentShiftRegister() == before){
                channel.tick();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    public void shiftRegisterClocksExactlyOncePerFullTimerPeriod(final int periodIndex){
        final NoiseChannel realChannel = new NoiseChannel();
        realChannel.writeModeAndPeriod(periodIndex); //mode clear, period = table[periodIndex]
        final int period = NoiseChannel.NTSC_NOISE_PERIODS[periodIndex];

        realChannel.tick(); //consume the immediate first clock from the initial timerCounter=0 state

        final int stateAfterFirstClock = realChannel.currentShiftRegister();
        for (int i = 0; i < 2 * (period + 1) - 1; i++){
            realChannel.tick();
        }
        assertEquals(stateAfterFirstClock, realChannel.currentShiftRegister(), "should not have clocked again yet");

        realChannel.tick();
        assertFalse(stateAfterFirstClock == realChannel.currentShiftRegister(), "should have clocked by now");
    }
}
