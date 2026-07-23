package com.rox;

import com.rox.apu.APU;
import com.rox.audio.AudioOutput;
import com.rox.audio.Resampler;
import com.rox.audio.SpeakerAudioOutput;
import com.rox.clock.Clock;
import com.rox.clock.FPSClock;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.mem.*;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

import javax.sound.sampled.LineUnavailableException;

public class NES {
    private static final long CPU_HZ = 1_789_773;
    private static final long AUDIO_SAMPLE_RATE_HZ = 44_100;

    private final MOS6502 cpu;
    private final APU apu;
    private final Clock clock;
    private final LatchedMemoryBus memoryBus;
    private final AudioOutput audioOutput;

    public NES() throws LineUnavailableException {
        this(new SpeakerAudioOutput());
    }

    NES(final AudioOutput audioOutput){
        this.apu = new APU();
        this.memoryBus = new Latched8BitMemoryBus(new NESMemoryBus(new MemoryBus8Bit(new RAM(0x10000)), apu));
        this.cpu = new MOS6502(memoryBus);
        this.clock = new FPSClock(CPU_HZ, 60, new SystemTimeSource(), new ThreadSleeper());
        this.audioOutput = audioOutput;

        final Resampler resampler = new Resampler(CPU_HZ, AUDIO_SAMPLE_RATE_HZ);
        clock.addListener(cpu);
        clock.addListener(apu);
        clock.addListener(() -> resampler.accept(apu.outputSample()).ifPresent(audioOutput::write));
    }

    public void powerOn(){
        audioOutput.start();
        clock.run();
    }

    public void powerOff(){
        clock.stop();
        audioOutput.stop();
    }
}
