package com.rox;

import com.rox.apu.APU;
import com.rox.clock.Clock;
import com.rox.clock.FPSClock;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.mem.*;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

public class NES {
    private final MOS6502 cpu;
    private final APU apu;
    private final Clock clock;
    private final LatchedMemoryBus memoryBus;

    public NES(){
        this.apu = new APU();
        this.memoryBus = new Latched8BitMemoryBus(new NESMemoryBus(new MemoryBus8Bit(new RAM(0x10000)), apu));
        this.cpu = new MOS6502(memoryBus);
        this.clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());

        clock.addListener(cpu);
        clock.addListener(apu);
    }

    public void powerOn(){
        clock.run();
    }

    public void powerOff(){
        clock.stop();
    }
}
