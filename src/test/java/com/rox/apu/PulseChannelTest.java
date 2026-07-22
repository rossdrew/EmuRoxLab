package com.rox.apu;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PulseChannelTest {
    private Envelope mockEnvelope;
    private LengthCounter mockLengthCounter;
    private Sweep mockSweep;
    private PulseChannel channel;

    @BeforeEach
    public void setup(){
        mockEnvelope = mock(Envelope.class);
        mockLengthCounter = mock(LengthCounter.class);
        mockSweep = mock(Sweep.class);
        channel = new PulseChannel(mockEnvelope, mockLengthCounter, mockSweep);
    }

    @Test
    public void dutyWaveformMatchesSelectedDutyCycleAcrossFullEightStepSequence(){
        for (int duty = 0; duty < 4; duty++){
            final PulseChannel realChannel = new PulseChannel(true);
            realChannel.writeControlRegister((duty << 6) | 0x10 | 0x0F); //duty select, constant volume, volume=15
            realChannel.writeTimerLow(100);
            realChannel.writeTimerHighAndLengthLoad(0); //timer high=0 (period 100, well clear of the sweep's mute-below-8 floor), length index 0 (loads 10)

            for (int step = 0; step < 8; step++){
                final int expected = PulseChannel.DUTY_TABLES[duty][step] == 0 ? 0 : 15;
                assertEquals(expected, realChannel.outputSample(), "duty=" + duty + " step=" + step);
                advanceOneSequenceStep(realChannel);
            }
        }
    }

    private static void advanceOneSequenceStep(final PulseChannel channel){
        final int start = channel.currentSequencePosition();
        while (channel.currentSequencePosition() == start){
            channel.tick();
        }
    }

    @Test
    public void writingTimerHighAndLengthLoadResetsSequencePositionAndRestartsEnvelope(){
        channel.writeTimerHighAndLengthLoad((5 << 3) | 3); //length index 5, timer high bits 3

        verify(mockLengthCounter).load(5);
        verify(mockEnvelope).restart();
        assertEquals(0, channel.currentSequencePosition());
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

    @Test
    public void writingSweepRegisterDelegatesToSweep(){
        channel.writeSweepRegister(0x91);

        verify(mockSweep).writeControlRegister(0x91);
    }
    
    @Test
    public void timerPeriodAssemblesLowThenHighByteWithoutCrossContamination(){
        channel.writeTimerLow(0xAB);
        channel.writeTimerHighAndLengthLoad(0x02); //length index 0, timer high=2

        channel.outputSample();

        verify(mockSweep).isMuted(0x2AB);
    }

    @Test
    public void timerHighWritePreservesPreviouslyWrittenLowByte(){
        channel.writeTimerHighAndLengthLoad(0x05); //timer high=5, length index 0
        channel.writeTimerLow(0x11);

        channel.outputSample();

        verify(mockSweep).isMuted(0x511);
    }

    @Test
    public void lengthCounterZeroSilencesOutputRegardlessOfSweepOrDuty(){
        when(mockLengthCounter.isZero()).thenReturn(true);

        assertEquals(0, channel.outputSample());
        verify(mockSweep, never()).isMuted(anyInt());
        verify(mockEnvelope, never()).volume();
    }

    @Test
    public void sweepMutedSilencesOutputRegardlessOfLengthOrDuty(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockSweep.isMuted(anyInt())).thenReturn(true);

        assertEquals(0, channel.outputSample());
        verify(mockEnvelope, never()).volume();
    }

    @Test
    public void dutyBitZeroSilencesOutputWhenLengthAndSweepAllowSound(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockSweep.isMuted(anyInt())).thenReturn(false);
        channel.writeControlRegister(0x00); //duty=0, sequence position 0 is a 0 bit in that table

        assertEquals(0, channel.outputSample());
        verify(mockEnvelope, never()).volume();
    }

    @Test
    public void audibleOutputReturnsEnvelopeVolumeWhenNothingSilences(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockSweep.isMuted(anyInt())).thenReturn(false);
        when(mockEnvelope.volume()).thenReturn(9);
        channel.writeControlRegister(3 << 6); //duty=3, sequence position 0 is a 1 bit in that table

        assertEquals(9, channel.outputSample());
    }

    @Test
    public void quarterFrameTickClocksOnlyTheEnvelope(){
        channel.quarterFrameTick();

        verify(mockEnvelope).tick();
        verify(mockLengthCounter, never()).tick();
    }

    @Test
    public void halfFrameTickClocksLengthCounterAndAppliesSweep(){
        channel.writeTimerLow(0x00);
        channel.writeTimerHighAndLengthLoad(0x00); //period 0
        when(mockSweep.clockHalfFrame(0)).thenReturn(0x123);

        channel.halfFrameTick();

        verify(mockLengthCounter).tick();
        channel.outputSample();
        verify(mockSweep).isMuted(0x123); //confirms the sweep's returned period was adopted
    }

    @Provide
    Arbitrary<Integer> periods(){
        return Arbitraries.integers().between(0, 63);
    }

    @Property
    public void sequencePositionAdvancesExactlyOncePerFullTimerPeriod(@ForAll("periods") final int period){
        final PulseChannel realChannel = new PulseChannel(true);
        realChannel.writeTimerLow(period & 0xFF);
        realChannel.writeTimerHighAndLengthLoad((period >> 8) & 0x07); //length index 0, unused here

        realChannel.tick(); //consume the immediate first advance from the initial timerCounter=0 state

        final int startPosition = realChannel.currentSequencePosition();
        for (int i = 0; i < 2 * (period + 1) - 1; i++){
            realChannel.tick();
        }
        assertEquals(startPosition, realChannel.currentSequencePosition(), "should not have advanced yet");

        realChannel.tick();
        assertEquals((startPosition + 1) % 8, realChannel.currentSequencePosition());
    }
}
