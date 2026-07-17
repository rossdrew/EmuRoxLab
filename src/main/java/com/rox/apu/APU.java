package com.rox.apu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * NES Audio Processing Unit, mapped into $4000-$4017. Currently owns just the frame counter;
 * channel registers ($4000-$4013) and the full $4015 enable/status behaviour are wired in
 * later phases.
 */
public class APU implements ClockWatcher, MemoryBus {
    public static final int STATUS_REGISTER_ADDRESS = 0x4015;
    public static final int FRAME_COUNTER_ADDRESS = 0x4017;

    private static final int FRAME_IRQ_FLAG = 0x40;

    private final FrameSequencer frameSequencer;

    public APU(){
        this(new FrameSequencer());
    }

    APU(final FrameSequencer frameSequencer){
        this.frameSequencer = frameSequencer;
    }

    @Override
    public void tick() {
        frameSequencer.clock();
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
        if (address == FRAME_COUNTER_ADDRESS){
            frameSequencer.writeControlRegister(value);
        }
        //$4015 enable byte and channel registers ($4000-$4013) are wired in later phases
    }

    private int readStatusRegister(){
        if (frameSequencer.isFrameIrqPending()){
            frameSequencer.clearFrameIrq();
            return FRAME_IRQ_FLAG;
        }
        return 0; //bit7 (DMC-IRQ) stays 0 until the DMC channel exists
    }
}
