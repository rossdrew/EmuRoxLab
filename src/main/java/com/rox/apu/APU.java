package com.rox.apu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * NES Audio Processing Unit, mapped into $4000-$4017. Owns the frame counter and both pulse
 * channels; the triangle/noise/DMC channels and the full $4015 enable/status behaviour are wired
 * in later phases.
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

    private static final int FRAME_IRQ_FLAG = 0x40;

    //TODO phase 4: replace with triangle.outputSample() once the triangle channel exists
    private static final int TRIANGLE_OUTPUT_NOT_YET_IMPLEMENTED = 0;
    //TODO phase 5: replace with noise.outputSample() once the noise channel exists
    private static final int NOISE_OUTPUT_NOT_YET_IMPLEMENTED = 0;
    //TODO phase 6: replace with dmc.outputSample() once the DMC channel exists
    private static final int DMC_OUTPUT_NOT_YET_IMPLEMENTED = 0;

    private final FrameSequencer frameSequencer;
    private final PulseChannel pulse1;
    private final PulseChannel pulse2;

    public APU(){
        this(new FrameSequencer(), new PulseChannel(true), new PulseChannel(false));
    }

    APU(final FrameSequencer frameSequencer, final PulseChannel pulse1, final PulseChannel pulse2){
        this.frameSequencer = frameSequencer;
        this.pulse1 = pulse1;
        this.pulse2 = pulse2;
        frameSequencer.addQuarterFrameWatcher(pulse1::quarterFrameTick);
        frameSequencer.addQuarterFrameWatcher(pulse2::quarterFrameTick);
        frameSequencer.addHalfFrameWatcher(pulse1::halfFrameTick);
        frameSequencer.addHalfFrameWatcher(pulse2::halfFrameTick);
    }

    @Override
    public void tick() {
        frameSequencer.clock();
        pulse1.tick();
        pulse2.tick();
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

            default -> { } //TODO $4015 enable byte and the triangle/noise/DMC registers
        }
    }

    /** The {@link Mixer} combined analog output of all five channels */
    public double outputSample(){
        return Mixer.mix(
                pulse1.outputSample(),
                pulse2.outputSample(),
                TRIANGLE_OUTPUT_NOT_YET_IMPLEMENTED,
                NOISE_OUTPUT_NOT_YET_IMPLEMENTED,
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
