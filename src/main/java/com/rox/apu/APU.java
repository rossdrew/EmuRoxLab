package com.rox.apu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * NES Audio Processing Unit, mapped into $4000-$4017. Owns the frame counter, both pulse channels,
 * the triangle channel and the noise channel; the DMC channel and the full $4015 enable/status
 * behaviour are wired in later phases.
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

    private static final int FRAME_IRQ_FLAG = 0x40;

    //TODO phase 6: replace with dmc.outputSample() once the DMC channel exists
    private static final int DMC_OUTPUT_NOT_YET_IMPLEMENTED = 0;

    private final FrameSequencer frameSequencer;
    private final PulseChannel pulse1;
    private final PulseChannel pulse2;
    private final TriangleChannel triangle;
    private final NoiseChannel noise;

    public APU(){
        this(new FrameSequencer(), new PulseChannel(true), new PulseChannel(false), new TriangleChannel(),
                new NoiseChannel());
    }

    APU(final FrameSequencer frameSequencer, final PulseChannel pulse1, final PulseChannel pulse2,
        final TriangleChannel triangle, final NoiseChannel noise){
        this.frameSequencer = frameSequencer;
        this.pulse1 = pulse1;
        this.pulse2 = pulse2;
        this.triangle = triangle;
        this.noise = noise;
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

            default -> { } //TODO $4015 enable byte and the DMC registers
        }
    }

    /** The {@link Mixer} combined analog output of all five channels */
    public double outputSample(){
        return Mixer.mix(
                pulse1.outputSample(),
                pulse2.outputSample(),
                triangle.outputSample(),
                noise.outputSample(),
                DMC_OUTPUT_NOT_YET_IMPLEMENTED
        );
    }

    private int readStatusRegister(){
        if (frameSequencer.isFrameIrqPending()){
            frameSequencer.clearFrameIrq();
            return FRAME_IRQ_FLAG;
        }
        return 0; //bit7 (DMC-IRQ) stays 0 until the DMC channel exists
    }
}
