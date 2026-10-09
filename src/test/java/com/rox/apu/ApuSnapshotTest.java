package com.rox.apu;

import com.rox.mem.MemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two kinds of check: per-unit properties (restoring any values then snapshotting gives them back, so
 * every field is wired both ways), and whole-APU lockstep runs (a restored APU behaves identically,
 * so no unit's real state was left out of its snapshot in the first place).
 */
public class ApuSnapshotTest {
    // --- per-unit round trips ---

    @Property
    public void envelopeRoundTrips(@ForAll @IntRange(max = 15) int reloadValue, @ForAll boolean volumeIsConstant,
                                   @ForAll boolean loopEnabled, @ForAll boolean restartRequested,
                                   @ForAll @IntRange(max = 15) int divider, @ForAll @IntRange(max = 15) int decayLevel){
        final Envelope.Snapshot snapshot = new Envelope.Snapshot(reloadValue, volumeIsConstant, loopEnabled, restartRequested, divider, decayLevel);
        final Envelope envelope = new Envelope();

        envelope.restore(snapshot);

        assertEquals(snapshot, envelope.snapshot());
    }

    @Property
    public void lengthCounterRoundTrips(@ForAll @IntRange(max = 254) int counter, @ForAll boolean haltEnabled){
        final LengthCounter.Snapshot snapshot = new LengthCounter.Snapshot(counter, haltEnabled);
        final LengthCounter lengthCounter = new LengthCounter();

        lengthCounter.restore(snapshot);

        assertEquals(snapshot, lengthCounter.snapshot());
    }

    @Property
    public void linearCounterRoundTrips(@ForAll @IntRange(max = 127) int reloadValue, @ForAll boolean controlFlagSet,
                                        @ForAll @IntRange(max = 127) int counter, @ForAll boolean reloadFlagSet){
        final LinearCounter.Snapshot snapshot = new LinearCounter.Snapshot(reloadValue, controlFlagSet, counter, reloadFlagSet);
        final LinearCounter linearCounter = new LinearCounter();

        linearCounter.restore(snapshot);

        assertEquals(snapshot, linearCounter.snapshot());
    }

    @Property
    public void sweepRoundTrips(@ForAll boolean enabled, @ForAll @IntRange(max = 7) int periodReload, @ForAll boolean negate,
                                @ForAll @IntRange(max = 7) int shiftCount, @ForAll boolean reloadRequested,
                                @ForAll @IntRange(max = 7) int divider){
        final Sweep.Snapshot snapshot = new Sweep.Snapshot(enabled, periodReload, negate, shiftCount, reloadRequested, divider);
        final Sweep sweep = new Sweep(true);

        sweep.restore(snapshot);

        assertEquals(snapshot, sweep.snapshot());
    }

    @Property
    public void frameSequencerRoundTrips(@ForAll boolean fiveStepMode, @ForAll boolean irqInhibit,
                                         @ForAll @IntRange(max = 37281) int cycle, @ForAll boolean frameIrqPending){
        final FrameSequencer.Snapshot snapshot = new FrameSequencer.Snapshot(fiveStepMode, irqInhibit, cycle, frameIrqPending);
        final FrameSequencer frameSequencer = new FrameSequencer();

        frameSequencer.restore(snapshot);

        assertEquals(snapshot, frameSequencer.snapshot());
    }

    @Property
    public void frequencyDividerRoundTrips(@ForAll boolean parityGate, @ForAll @IntRange(max = 0x7FF) int countdown,
                                           @ForAll @IntRange(max = 0x7FF) int counterPeriod){
        final ParityCountdownFrequencyDivider.Snapshot snapshot = new ParityCountdownFrequencyDivider.Snapshot(parityGate, countdown, counterPeriod);
        final ParityCountdownFrequencyDivider divider = new ParityCountdownFrequencyDivider(() -> { }, false, 0);

        divider.restore(snapshot);

        assertEquals(snapshot, divider.snapshot());
    }

    // --- whole-APU lockstep ---

    private static final int SAMPLE_ADDRESS = 0xC000;

    /** A real APU whose DMC reads a short, non-silent sample from its own RAM-backed bus. */
    private static APU apuWithSample(){
        final RAM ram = new RAM(0x10000);
        for (int i = 0; i < 17; i++){
            ram.write(SAMPLE_ADDRESS + i, 0xA5 ^ i);
        }
        final MemoryBus bus = new MemoryBus8Bit(ram);
        return new APU(bus);
    }

    /** Every channel playing, with envelopes decaying, a sweep running, and a 17-byte IRQ-raising DMC sample. */
    private static APU busyApu(){
        final APU apu = apuWithSample();
        apu.write(0x4017, 0x00); //4-step, frame IRQ allowed
        apu.write(0x4010, 0x8F); apu.write(0x4011, 0x40); apu.write(0x4012, 0x00); apu.write(0x4013, 0x01);
        //enable before the length writes below - a disabled channel ignores length counter loads
        apu.write(0x4015, 0x1F);
        apu.write(0x4000, 0x86); apu.write(0x4001, 0xA9); apu.write(0x4002, 0x40); apu.write(0x4003, 0x09);
        apu.write(0x4004, 0x4F); apu.write(0x4005, 0x93); apu.write(0x4006, 0x80); apu.write(0x4007, 0x18);
        apu.write(0x4008, 0x45); apu.write(0x400A, 0x30); apu.write(0x400B, 0x22);
        apu.write(0x400C, 0x23); apu.write(0x400E, 0x84); apu.write(0x400F, 0x30);
        return apu;
    }

    private static void tick(final APU apu, final int ticks){
        for (int i = 0; i < ticks; i++){
            apu.tick();
        }
    }

    private static void assertRestoredApuRunsInLockstep(final APU original){
        final APU restored = apuWithSample();
        restored.restore(original.snapshot());
        assertEquals(original.snapshot(), restored.snapshot());

        for (int i = 0; i < 40_000; i++){
            original.tick();
            restored.tick();
            assertEquals(original.outputSample(), restored.outputSample(), "output at tick " + i);
            assertEquals(original.isIrqAsserted(), restored.isIrqAsserted(), "IRQ at tick " + i);
        }
        assertEquals(original.snapshot(), restored.snapshot());
    }

    @Test
    public void restoredMidSampleRunsInLockstep(){
        final APU apu = busyApu();
        tick(apu, 3_000); //DMC part-way through its sample, envelopes and sweep mid-count
        final ApuSnapshot snapshot = apu.snapshot();
        assertTrue(snapshot.pulse1().lengthCounter().counter() > 0 && snapshot.triangle().lengthCounter().counter() > 0
                        && snapshot.dmc().bytesRemaining() > 0,
                "test setup: expected channels still sounding and the DMC sample part-played");

        assertRestoredApuRunsInLockstep(apu);
    }

    @Test
    public void restoredWithIrqsPendingRunsInLockstep(){
        final APU apu = busyApu();
        tick(apu, 31_000); //past a full 4-step frame (frame IRQ) and the end of the DMC sample (DMC IRQ)
        final ApuSnapshot snapshot = apu.snapshot();
        assertTrue(snapshot.frameSequencer().frameIrqPending() && snapshot.dmc().irqPending(),
                "test setup: expected both the frame and DMC IRQs to be pending");

        assertRestoredApuRunsInLockstep(apu);
    }
}
