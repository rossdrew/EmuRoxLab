package com.rox.apu;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TriangleChannelTest {
    private LinearCounter mockLinearCounter;
    private LengthCounter mockLengthCounter;
    private TriangleChannel channel;

    @BeforeEach
    public void setup(){
        mockLinearCounter = mock(LinearCounter.class);
        mockLengthCounter = mock(LengthCounter.class);
        channel = new TriangleChannel(mockLinearCounter, mockLengthCounter);
    }

    @Test
    public void sequenceOutputsFullThirtyTwoStepTriangleWaveformInOrderThenWraps(){
        final TriangleChannel realChannel = new TriangleChannel(nonzeroLinearCounter(), nonzeroLengthCounter());
        realChannel.writeTimerLow(0); //period 0 -> advances every tick

        for (int step = 0; step < 32; step++){
            assertEquals(TriangleChannel.SEQUENCE[step], realChannel.outputSample(), "step=" + step);
            realChannel.tick();
        }
        assertEquals(TriangleChannel.SEQUENCE[0], realChannel.outputSample(), "should have wrapped back to the start");
    }

    private static LinearCounter nonzeroLinearCounter(){
        final LinearCounter linearCounter = new LinearCounter();
        linearCounter.writeControlRegister(0xFF); //control flag set (reload flag persists), reload=127
        linearCounter.requestReload();
        linearCounter.tick(); //loads 127, stays nonzero for the rest of the test since it's never ticked again
        return linearCounter;
    }

    private static LengthCounter nonzeroLengthCounter(){
        final LengthCounter lengthCounter = new LengthCounter();
        lengthCounter.load(0); //loads 10, stays nonzero for the rest of the test since it's never ticked
        return lengthCounter;
    }

    @Test
    public void timerPeriodAssemblesLowThenHighByteWithoutCrossContamination(){
        channel.writeTimerLow(0xAB);
        channel.writeTimerHighAndLengthLoad(0x02); //length index 0, timer high=2

        assertEquals(0x2AB, channel.currentTimerPeriod());
    }

    @Test
    public void timerHighWritePreservesPreviouslyWrittenLowByte(){
        channel.writeTimerHighAndLengthLoad(0x05); //timer high=5, length index 0
        channel.writeTimerLow(0x11);

        assertEquals(0x511, channel.currentTimerPeriod());
    }

    @Test
    public void writingLinearCounterRegisterSetsHaltAndDelegatesToLinearCounter(){
        channel.writeLinearCounterRegister(0x85); //control flag set, reload=5

        verify(mockLengthCounter).setHalt(true);
        verify(mockLinearCounter).writeControlRegister(0x85);
    }

    @Test
    public void writingLinearCounterRegisterWithControlBitClearSetsHaltFalse(){
        channel.writeLinearCounterRegister(0x05); //control flag clear

        verify(mockLengthCounter).setHalt(false);
    }

    @Test
    public void writingTimerHighAndLengthLoadLoadsLengthCounterAndRequestsLinearReload(){
        channel.writeTimerHighAndLengthLoad((5 << 3) | 3); //length index 5, timer high bits 3

        verify(mockLengthCounter).load(5);
        verify(mockLinearCounter).requestReload();
    }

    @Test
    public void quarterFrameTickClocksOnlyTheLinearCounter(){
        channel.quarterFrameTick();

        verify(mockLinearCounter).tick();
        verify(mockLengthCounter, never()).tick();
    }

    @Test
    public void halfFrameTickClocksOnlyTheLengthCounter(){
        channel.halfFrameTick();

        verify(mockLengthCounter).tick();
        verify(mockLinearCounter, never()).tick();
    }

    @Test
    public void sequencerFreezesWhenLengthCounterIsZero(){
        when(mockLengthCounter.isZero()).thenReturn(true);
        when(mockLinearCounter.isZero()).thenReturn(false);
        channel.writeTimerLow(0); //period 0 -> would advance every tick if unfrozen

        channel.tick();
        channel.tick();
        channel.tick();

        assertEquals(0, channel.currentSequencePosition());
    }

    @Test
    public void sequencerFreezesWhenLinearCounterIsZero(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockLinearCounter.isZero()).thenReturn(true);
        channel.writeTimerLow(0);

        channel.tick();
        channel.tick();
        channel.tick();

        assertEquals(0, channel.currentSequencePosition());
    }

    @Test
    public void sequencerAdvancesWhenBothCountersAreNonzero(){
        when(mockLengthCounter.isZero()).thenReturn(false);
        when(mockLinearCounter.isZero()).thenReturn(false);
        channel.writeTimerLow(0); //period 0 -> advances every tick

        channel.tick();

        assertEquals(1, channel.currentSequencePosition());
    }

    @Test
    public void outputSampleReturnsCurrentSequenceStepRegardlessOfCounterState(){
        when(mockLengthCounter.isZero()).thenReturn(true);
        when(mockLinearCounter.isZero()).thenReturn(true);

        assertEquals(TriangleChannel.SEQUENCE[0], channel.outputSample());
    }

    @Provide
    Arbitrary<Integer> periods(){
        return Arbitraries.integers().between(0, 63);
    }

    @Property
    public void sequencePositionAdvancesExactlyOncePerFullTimerPeriodWhenUnfrozen(@ForAll("periods") final int period){
        final TriangleChannel realChannel = new TriangleChannel(nonzeroLinearCounter(), nonzeroLengthCounter());
        realChannel.writeTimerLow(period & 0xFF);
        realChannel.writeTimerHighAndLengthLoad((period >> 8) & 0x07); //length index 0, unused here

        realChannel.tick(); //consume the immediate first advance from the initial timerCounter=0 state

        final int startPosition = realChannel.currentSequencePosition();
        for (int i = 0; i < period; i++){
            realChannel.tick();
        }
        assertEquals(startPosition, realChannel.currentSequencePosition(), "should not have advanced yet");

        realChannel.tick();
        assertEquals((startPosition + 1) % 32, realChannel.currentSequencePosition());
    }
}
