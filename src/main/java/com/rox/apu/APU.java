package com.rox.apu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * NES Audio Processing Unit, mapped into $4000-$4017. Owns the frame counter and all five channels
 * (both pulse, triangle, noise, DMC); only the full $4015 enable/status behaviour remains for a
 * later phase.
 */
public class APU implements ClockWatcher, MemoryBus {
    public static final int STATUS_REGISTER_ADDRESS = 0x4015;
    public static final int FRAME_COUNTER_ADDRESS = 0x4017;

    private static final int PULSE1_CONTROL_ADDRESS = 0x4000;
    private static final int PULSE1_SWEEP_ADDRESS = 0x4001;
    private static final int PULSE1_TIMER_LOW_ADDRESS = 0x4002;
    private static final int PULSE1_TIMER_HIGH_ADDRESS = 0x4003;

    private static final int PULSE2_CONTROL_ADDRESS = 0x4004;
    private static final int PULSE2_SWEEP_ADDRESS = 0x4005;
    private static final int PULSE2_TIMER_LOW_ADDRESS = 0x4006;
    private static final int PULSE2_TIMER_HIGH_ADDRESS = 0x4007;

    private static final int TRIANGLE_LINEAR_COUNTER_ADDRESS = 0x4008;
    private static final int TRIANGLE_TIMER_LOW_ADDRESS = 0x400A;
    private static final int TRIANGLE_TIMER_HIGH_ADDRESS = 0x400B;

    private static final int NOISE_CONTROL_ADDRESS = 0x400C;
    private static final int NOISE_MODE_AND_PERIOD_ADDRESS = 0x400E;
    private static final int NOISE_LENGTH_LOAD_ADDRESS = 0x400F;

    private static final int DMC_CONTROL_ADDRESS = 0x4010;
    private static final int DMC_DIRECT_LOAD_ADDRESS = 0x4011;
    private static final int DMC_SAMPLE_ADDRESS_ADDRESS = 0x4012;
    private static final int DMC_SAMPLE_LENGTH_ADDRESS = 0x4013;

    private static final int FRAME_IRQ_FLAG = 0x40;

    private final FrameSequencer frameSequencer;
    private final PulseChannel pulse1;
    private final PulseChannel pulse2;
    private final TriangleChannel triangle;
    private final NoiseChannel noise;
    private final DMCChannel dmc;

    public APU(final MemoryBus memoryBus){
        this(new FrameSequencer(), new PulseChannel(true), new PulseChannel(false), new TriangleChannel(),
                new NoiseChannel(), new DMCChannel(memoryBus));
    }

    APU(final FrameSequencer frameSequencer, final PulseChannel pulse1, final PulseChannel pulse2,
        final TriangleChannel triangle, final NoiseChannel noise, final DMCChannel dmc){
        this.frameSequencer = frameSequencer;
        this.pulse1 = pulse1;
        this.pulse2 = pulse2;
        this.triangle = triangle;
        this.noise = noise;
        this.dmc = dmc;
        frameSequencer.addQuarterFrameWatcher(pulse1::quarterFrameTick);
        frameSequencer.addQuarterFrameWatcher(pulse2::quarterFrameTick);
        frameSequencer.addQuarterFrameWatcher(triangle::quarterFrameTick);
        frameSequencer.addQuarterFrameWatcher(noise::quarterFrameTick);
        frameSequencer.addHalfFrameWatcher(pulse1::halfFrameTick);
        frameSequencer.addHalfFrameWatcher(pulse2::halfFrameTick);
        frameSequencer.addHalfFrameWatcher(triangle::halfFrameTick);
        frameSequencer.addHalfFrameWatcher(noise::halfFrameTick);
    }

    @Override
    public void tick() {
        frameSequencer.clock();
        pulse1.tick();
        pulse2.tick();
        triangle.tick();
        noise.tick();
        dmc.tick();
    }

    @Override
    public int read(final int address) {
        if (address == STATUS_REGISTER_ADDRESS){
            return readStatusRegister();
        }
        return 0;
    }

    @Override
    public void write(final int address, final int value) {
        switch (address){
            case FRAME_COUNTER_ADDRESS -> frameSequencer.writeControlRegister(value);

            case PULSE1_CONTROL_ADDRESS -> pulse1.writeControlRegister(value);
            case PULSE1_SWEEP_ADDRESS -> pulse1.writeSweepRegister(value);
            case PULSE1_TIMER_LOW_ADDRESS -> pulse1.writeTimerLow(value);
            case PULSE1_TIMER_HIGH_ADDRESS -> pulse1.writeTimerHighAndLengthLoad(value);

            case PULSE2_CONTROL_ADDRESS -> pulse2.writeControlRegister(value);
            case PULSE2_SWEEP_ADDRESS -> pulse2.writeSweepRegister(value);
            case PULSE2_TIMER_LOW_ADDRESS -> pulse2.writeTimerLow(value);
            case PULSE2_TIMER_HIGH_ADDRESS -> pulse2.writeTimerHighAndLengthLoad(value);

            case TRIANGLE_LINEAR_COUNTER_ADDRESS -> triangle.writeLinearCounterRegister(value);
            case TRIANGLE_TIMER_LOW_ADDRESS -> triangle.writeTimerLow(value);
            case TRIANGLE_TIMER_HIGH_ADDRESS -> triangle.writeTimerHighAndLengthLoad(value);

            case NOISE_CONTROL_ADDRESS -> noise.writeControlRegister(value);
            case NOISE_MODE_AND_PERIOD_ADDRESS -> noise.writeModeAndPeriod(value);
            case NOISE_LENGTH_LOAD_ADDRESS -> noise.writeLengthLoad(value);

            case DMC_CONTROL_ADDRESS -> dmc.writeControlRegister(value);
            case DMC_DIRECT_LOAD_ADDRESS -> dmc.writeDirectLoad(value);
            case DMC_SAMPLE_ADDRESS_ADDRESS -> dmc.writeSampleAddress(value);
            case DMC_SAMPLE_LENGTH_ADDRESS -> dmc.writeSampleLength(value);

            default -> { } //TODO $4015 enable byte
        }
    }

    /** The {@link Mixer} combined analog output of all five channels */
    public double outputSample(){
        return Mixer.mix(
                pulse1.outputSample(),
                pulse2.outputSample(),
                triangle.outputSample(),
                noise.outputSample(),
                dmc.outputSample()
        );
    }

    private int readStatusRegister(){
        if (frameSequencer.isFrameIrqPending()){
            frameSequencer.clearFrameIrq();
            return FRAME_IRQ_FLAG;
        }
        return 0; //bit7 (DMC-IRQ) stays 0 until $4015 is wired
    }
}
