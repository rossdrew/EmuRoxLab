package com.rox.apu;

import com.rox.mem.MemoryBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DMCChannelTest {
    private MemoryBus memoryBus;
    private DMCChannel channel;

    @BeforeEach
    public void setup(){
        memoryBus = mock(MemoryBus.class);
        channel = new DMCChannel(memoryBus);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    public void writingControlRegisterSelectsRateFromNtscTable(final int rateIndex){
        channel.writeControlRegister(rateIndex); //IRQ/loop clear, rate index under test

        assertEquals(DMCChannel.NTSC_DMC_RATES[rateIndex], channel.currentTimerPeriod());
    }

    @Test
    public void writingControlRegisterSetsLoopFlag(){
        channel.writeControlRegister(0x40);

        assertTrue(channel.isLoopFlagSet());

        channel.writeControlRegister(0x00);

        assertFalse(channel.isLoopFlagSet());
    }

    @Test
    public void disablingIrqViaControlRegisterClearsAPendingIrq(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0x80); //IRQ enabled, rate index 0
        channel.writeSampleAddress(0);
        channel.writeSampleLength(0); //length 1 byte
        channel.start(); //fetches the only byte synchronously, no loop -> IRQ becomes pending immediately

        assertTrue(channel.isIrqPending());

        channel.writeControlRegister(0x00); //IRQ disabled

        assertFalse(channel.isIrqPending());
    }

    @Test
    public void writingDirectLoadSetsDeltaCounterDirectlyMaskedTo7Bits(){
        channel.writeDirectLoad(0x00);
        assertEquals(0, channel.currentDeltaCounter());

        channel.writeDirectLoad(0x7F);
        assertEquals(0x7F, channel.currentDeltaCounter());

        channel.writeDirectLoad(0xFF); //bit 7 ignored
        assertEquals(0x7F, channel.currentDeltaCounter());
    }

    @Test
    public void directLoadDoesNotTouchPlaybackState(){
        channel.writeSampleAddress(0x10);
        channel.writeSampleLength(0x02);
        channel.start();
        final int addressBefore = channel.currentAddress();
        final int remainingBefore = channel.bytesRemaining();

        channel.writeDirectLoad(0x40);

        assertEquals(addressBefore, channel.currentAddress());
        assertEquals(remainingBefore, channel.bytesRemaining());
    }

    @Test
    public void writingSampleAddressComputesStartAddressFromRegisterValue(){
        channel.writeSampleAddress(0x00);
        assertEquals(0xC000, channel.sampleStartAddress());

        channel.writeSampleAddress(0x01);
        assertEquals(0xC040, channel.sampleStartAddress());

        channel.writeSampleAddress(0xFF);
        assertEquals(0xFFC0, channel.sampleStartAddress());
    }

    @Test
    public void writingSampleLengthComputesLengthFromRegisterValue(){
        channel.writeSampleLength(0x00);
        assertEquals(1, channel.sampleLength());

        channel.writeSampleLength(0x01);
        assertEquals(17, channel.sampleLength());

        channel.writeSampleLength(0xFF);
        assertEquals(4081, channel.sampleLength());
    }

    @Test
    public void startPrimesTheShiftRegisterByFetchingTheFirstByteWhenBytesAreAvailable(){
        when(memoryBus.read(0xC000)).thenReturn(0x55);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00); //1 byte

        channel.start();

        verify(memoryBus, times(1)).read(0xC000);
        assertEquals(0x55, channel.currentShiftRegister());
        assertFalse(channel.isOutputSilenced());
        assertEquals(0, channel.bytesRemaining());
    }

    @Test
    public void startAddressWrapsFrom0xFFFFTo0x8000(){
        when(memoryBus.read(anyInt())).thenReturn(0);
        channel.writeControlRegister(0); //fastest available real setting isn't needed - rate irrelevant here
        channel.writeSampleAddress(0xFF); //start address 0xFFC0
        channel.writeSampleLength(0x04); //65 bytes: 0xFFC0..0xFFFF (64 bytes) then wraps for the 65th
        channel.start(); //fetch #1 (of 64 needed to reach $FFFF) happens synchronously here

        driveFetches(channel, 63); //fetches #2-64, the last landing on $FFFF and wrapping

        assertEquals(0x8000, channel.currentAddress());
    }

    @Test
    public void deltaCounterClampsAt127WhenShiftingOutAllOneBits(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF); //all 8 bits set
        channel.writeControlRegister(0x40); //loop, so the buffer keeps supplying 1-bits across the test
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00); //1 byte, reloaded every exhaustion via loop
        channel.writeDirectLoad(0x7E); //start 2 below the ceiling
        channel.start();

        clockRealShifts(channel, 1); //one 1-bit shifted: 0x7E+2=128, clamps to 127
        assertEquals(127, channel.currentDeltaCounter());

        clockRealShifts(channel, 1); //further 1-bits must not exceed the ceiling
        assertEquals(127, channel.currentDeltaCounter());
    }

    @Test
    public void deltaCounterClampsAt0WhenShiftingOutAllZeroBits(){
        when(memoryBus.read(anyInt())).thenReturn(0x00); //all 8 bits clear
        channel.writeControlRegister(0x40); //loop, so the buffer keeps supplying 0-bits across the test
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00);
        channel.writeDirectLoad(0x01); //start 1 above the floor
        channel.start();

        clockRealShifts(channel, 1); //one 0-bit shifted: 0x01-2 clamps to 0
        assertEquals(0, channel.currentDeltaCounter());

        clockRealShifts(channel, 1); //further 0-bits must not go below the floor
        assertEquals(0, channel.currentDeltaCounter());
    }

    @Test
    public void outputFreezesOnceSampleIsExhaustedWithoutLoopOrIrq(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0); //no loop, no IRQ
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00); //1 byte
        channel.start();
        clockRealShifts(channel, 8); //exhausts the single byte, next refill has nothing to fetch

        final int deltaAfterExhaustion = channel.currentDeltaCounter();
        assertTrue(channel.isOutputSilenced());
        assertFalse(channel.isIrqPending());

        clockRealShifts(channel, 8); //further clocks must not change the frozen output

        assertEquals(deltaAfterExhaustion, channel.currentDeltaCounter());
    }

    @Test
    public void loopFlagRestartsPlaybackFromOriginalAddressAndLengthOnExhaustion(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0x40); //loop set, IRQ clear
        channel.writeSampleAddress(0x00); //0xC000
        channel.writeSampleLength(0x00); //1 byte
        channel.start();

        clockRealShifts(channel, 8); //exhausts + reloads via loop

        assertEquals(1, channel.bytesRemaining());
        assertFalse(channel.isOutputSilenced());
        assertFalse(channel.isIrqPending());
        verify(memoryBus, times(2)).read(0xC000); //initial fetch + the loop-reloaded refetch
    }

    @Test
    public void noLoopAndIrqEnabledSetsIrqPendingOnExhaustion(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0x80); //loop clear, IRQ enabled
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00); //1 byte
        channel.start();

        clockRealShifts(channel, 8);

        assertTrue(channel.isIrqPending());
        assertTrue(channel.isOutputSilenced());
    }

    @Test
    public void noLoopAndIrqDisabledDoesNothingOnExhaustion(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0x00); //loop clear, IRQ disabled
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00);
        channel.start();

        clockRealShifts(channel, 8);

        assertFalse(channel.isIrqPending());
        assertTrue(channel.isOutputSilenced());
    }

    @Test
    public void clearIrqClearsAPendingFlag(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(0x80);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00);
        channel.start();
        clockRealShifts(channel, 8);
        assertTrue(channel.isIrqPending());

        channel.clearIrq();

        assertFalse(channel.isIrqPending());
    }

    @Test
    public void shiftRegisterRefillsFromSuccessiveAddressesAcrossMultipleBytes(){
        when(memoryBus.read(anyInt())).thenReturn(0x00);
        channel.writeControlRegister(0x40); //loop, so it never silences mid-test
        channel.writeSampleAddress(0x00); //0xC000
        channel.writeSampleLength(0x01); //17 bytes
        channel.start(); //fetch #1 at 0xC000

        clockRealShifts(channel, 8); //exhausts first byte, refetch #2 at 0xC001

        verify(memoryBus, times(1)).read(0xC000);
        verify(memoryBus, times(1)).read(0xC001);
        assertEquals(0xC002, channel.currentAddress());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 15})
    public void tickIsParityGatedLikePulseAndNoise(final int rateIndex){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeControlRegister(rateIndex);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x01); //17 bytes - enough that a shift never runs out mid-check
        channel.start(); //note: doesn't touch the runner itself, only reloadShiftRegister() directly
        final int period = DMCChannel.NTSC_DMC_RATES[rateIndex];

        channel.tick(); //consumes the runner's immediate first clock from its initial countdown=0 state
        final int bitsRemainingAfterFirstClock = channel.bitsRemainingInShiftRegister();

        //one full timer period takes 2*(period+1) real tick() calls (parity gate halves the rate);
        //one call short of that must not have clocked again yet
        for (int i = 0; i < 2 * (period + 1) - 1; i++){
            channel.tick();
        }
        assertEquals(bitsRemainingAfterFirstClock, channel.bitsRemainingInShiftRegister(), "should not have clocked again yet");

        channel.tick();

        assertNotEquals(bitsRemainingAfterFirstClock, channel.bitsRemainingInShiftRegister(), "should have clocked by now");
    }

    @Test
    public void outputSampleReturnsTheDeltaCounter(){
        channel.writeDirectLoad(0x33);

        assertEquals(0x33, channel.outputSample());
    }

    @Test
    public void isActiveReflectsWhetherBytesRemain(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x00); //1 byte

        assertFalse(channel.isActive(), "idle before start()");

        channel.start();

        assertFalse(channel.isActive(), "the single byte was already consumed synchronously by start()");
    }

    @Test
    public void enablingWhileIdleRestartsPlayback(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x01); //17 bytes - stays active after the first fetch

        assertFalse(channel.isActive());

        channel.setEnabled(true);

        assertTrue(channel.isActive());
        verify(memoryBus, times(1)).read(0xC000);
    }

    @Test
    public void enablingWhileAlreadyActiveDoesNotRestart(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x01); //17 bytes
        channel.start(); //fetch #1, now active with 16 bytes remaining
        final int bytesRemainingBefore = channel.bytesRemaining();
        final int addressBefore = channel.currentAddress();

        channel.setEnabled(true); //should be a no-op - already playing

        assertEquals(bytesRemainingBefore, channel.bytesRemaining());
        assertEquals(addressBefore, channel.currentAddress());
        verify(memoryBus, times(1)).read(0xC000); //no extra fetch from the redundant enable
    }

    @Test
    public void disablingStopsFutureFetchesWithoutSilencingImmediately(){
        when(memoryBus.read(anyInt())).thenReturn(0xFF);
        channel.writeSampleAddress(0x00);
        channel.writeSampleLength(0x01); //17 bytes, so bytesRemaining is 16 (nonzero) after start()
        channel.start();

        channel.setEnabled(false);

        assertFalse(channel.isActive());
        assertEquals(0, channel.bytesRemaining());
        assertFalse(channel.isOutputSilenced(), "the byte already latched into the shift register keeps playing");
    }

    //generous upper bound on ticks needed for one real shift-register clock (2*(period+1), parity
    //gated) at the slowest configured rate - guards the driver loops below against ever spinning
    //forever if a regression stops the state from changing
    private static final int MAX_TICKS_PER_SHIFT = 2 * (DMCChannel.NTSC_DMC_RATES[0] + 1) + 2;
    private static final int BITS_PER_BYTE = 8; //mirrors DMCChannel's own private SHIFT_REGISTER_BITS

    /**
     * Drives exactly {@code fetches} real memory fetches (each one every 8 shift-register clocks),
     * regardless of the configured timer period - keeps tests independent of the exact rate chosen.
     */
    private static void driveFetches(final DMCChannel channel, final int fetches){
        for (int i = 0; i < fetches; i++){
            final int before = channel.bytesRemaining();
            int ticks = 0;
            while (channel.bytesRemaining() == before){
                if (++ticks > BITS_PER_BYTE * MAX_TICKS_PER_SHIFT){
                    fail("no fetch occurred within one byte's worth of timer periods");
                }
                channel.tick();
            }
        }
    }

    /**
     * Drives exactly {@code shifts} real shift-register clocks (i.e. real bits shifted out),
     * regardless of the timer period or tick()'s parity gating. Detects each real clock via
     * {@code bitsRemainingInShiftRegister()} rather than the shift register's value, since an
     * all-zero (or, after a reload, coincidentally repeated) sample byte would otherwise look
     * unchanged from one clock to the next.
     */
    private static void clockRealShifts(final DMCChannel channel, final int shifts){
        for (int i = 0; i < shifts; i++){
            final int before = channel.bitsRemainingInShiftRegister();
            int ticks = 0;
            while (channel.bitsRemainingInShiftRegister() == before){
                if (++ticks > MAX_TICKS_PER_SHIFT){
                    fail("no shift occurred within one timer period");
                }
                channel.tick();
            }
        }
    }
}
