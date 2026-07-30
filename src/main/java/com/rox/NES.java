package com.rox;

import com.rox.apu.APU;
import com.rox.audio.AudioOutput;
import com.rox.audio.Resampler;
import com.rox.audio.SpeakerAudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.clock.Clock;
import com.rox.clock.FPSClock;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.mem.*;
import com.rox.ppu.PPU;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

import javax.sound.sampled.LineUnavailableException;
import java.util.concurrent.CountDownLatch;

public class NES {
    private static final long CPU_HZ = 1_789_773;
    private static final long AUDIO_SAMPLE_RATE_HZ = 44_100;
    //gives the JVM's JIT time to compile the emulation's hot per-cycle tick loop, and lets the
    //ring buffer build up a cushion, before audio playback (and thus underrun risk) begins - see
    //SpeakerAudioOutput's own class doc for why draining starts empty otherwise
    private static final long AUDIO_WARM_UP_MILLIS = 300;

    private final MOS6502 cpu;
    private final APU apu;
    private final PPU ppu;
    private final Clock clock;
    private final LatchedMemoryBus memoryBus;
    private final AudioOutput audioOutput;

    public NES(final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), cartridge);
    }

    NES(final AudioOutput audioOutput, final Cartridge cartridge){
        final MemoryBus ramBus = new MemoryBus8Bit(new RAM(0x10000));
        //DMC's own sample-address generator (see DMCChannel) only ever produces addresses in
        //$8000-$FFFF (base $C000+, wrapping no lower than $8000) - always within cartridge range,
        //so the cartridge alone is a complete, correct DMA source with no need to route through
        //NESMemoryBus (which would need apu itself to construct, a circular dependency)
        this.apu = new APU(cartridge);
        this.ppu = new PPU();
        this.memoryBus = new Latched8BitMemoryBus(new NESMemoryBus(ramBus, apu, cartridge, ppu));
        this.cpu = new MOS6502(memoryBus);
        this.clock = new FPSClock(CPU_HZ, 60, new SystemTimeSource(), new ThreadSleeper());
        this.audioOutput = audioOutput;

        final Resampler resampler = new Resampler(CPU_HZ, AUDIO_SAMPLE_RATE_HZ);
        clock.addListener(cpu);
        clock.addListener(apu);
        clock.addListener(ppu);
        clock.addListener(() -> cpu.setIRQLine(apu.isIrqAsserted()));
        clock.addListener(() -> {
            if (ppu.consumeNmiEdge()){
                cpu.signalNMI();
            }
        });
        clock.addListener(() -> resampler.accept(apu.outputSample()).ifPresent(audioOutput::write));
    }

    public void powerOn(){
        cpu.reset();
        //signalled once the clock thread is genuinely executing clock.run(), not just scheduled to -
        //without this, the warm-up sleep below could overlap with OS scheduling delay on a loaded
        //system and elapse before the clock has actually started producing samples
        final CountDownLatch clockStarted = new CountDownLatch(1);
        final Thread clockThread = new Thread(() -> {
            clockStarted.countDown();
            clock.run();
        });
        clockThread.start();

        boolean interrupted = false;
        //always wait for the clock to genuinely start (a near-instant wait in practice - counting
        //down the latch is the very first thing clockThread does) before anything below that might
        //call clock.stop(): stopping before clock.run() has actually begun has no effect, since
        //run() unconditionally sets its own running flag back to true the moment it starts, silently
        //undoing an earlier stop() - so this wait must not be abandoned early on interrupt.
        while (true){
            try {
                clockStarted.await();
                break;
            } catch (InterruptedException e){
                interrupted = true;
            }
        }

        if (!interrupted){
            try {
                Thread.sleep(AUDIO_WARM_UP_MILLIS);
            } catch (InterruptedException e){
                interrupted = true;
            }
        }

        //clock.isRunning() is false if powerOff() ran concurrently during warm-up - audio must not
        //start after a shutdown was already requested, interrupted or not
        if (!interrupted && clock.isRunning()){
            audioOutput.start();
        }

        if (interrupted){
            //an interrupted warm-up must not silently leave the clock thread running in the
            //background for some later powerOff() call the caller might never make
            clock.stop();
        }
        //retry join() until the thread has genuinely terminated - a single interrupted join() would
        //otherwise return early without actually waiting, silently leaving the clock thread running
        while (clockThread.isAlive()){
            try {
                clockThread.join();
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        //standard practice even though nothing in this method (or its callers) observes the flag
        //afterward - powerOn() returns right after this, so this is an accepted, unobservable pitest
        //survivor rather than something worth chasing a test for
        if (interrupted){
            Thread.currentThread().interrupt();
        }
    }

    public void powerOff(){
        clock.stop();
        audioOutput.stop();
    }

    MOS6502 cpu(){
        return cpu;
    }

    APU apu(){
        return apu;
    }

    PPU ppu(){
        return ppu;
    }

    Clock clock(){
        return clock;
    }
}
