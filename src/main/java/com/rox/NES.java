package com.rox;

import com.rox.clock.Clock;
import com.rox.clock.FPSClock;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.mem.*;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

public class NES {
    private final MOS6502 cpu;
    private final Clock clock;
    private final LatchedMemoryBus memoryBus;

    public NES(){
        this.memoryBus = new Latched8BitMemoryBus(new MemoryBus8Bit(new RAM(0x10000)));
        this.cpu = new MOS6502(memoryBus);
        this.clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());

        clock.addListener(cpu);
    }

    public void powerOn(){
        clock.run();
    }

    public void powerOff(){
        clock.stop();
    }
}
