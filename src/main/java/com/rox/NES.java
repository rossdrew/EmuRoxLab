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
import com.rox.video.VideoOutput;

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
    //set by powerOff(), read by powerOn() - catches a powerOff() that races ahead of clockThread
    //even starting (see powerOn()'s comment on why clock.stop() alone can't catch that case)
    private volatile boolean stopRequested;

    public NES(final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), VideoOutput.NO_OP, cartridge);
    }

    /** Like {@link #NES(Cartridge)}, but presenting frames to {@code videoOutput} as they complete. */
    public NES(final VideoOutput videoOutput, final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), videoOutput, cartridge);
    }

    NES(final AudioOutput audioOutput, final Cartridge cartridge){
        this(audioOutput, VideoOutput.NO_OP, cartridge);
    }

    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final Cartridge cartridge){
        this(audioOutput, videoOutput, cartridge, new FPSClock(CPU_HZ, 60, new SystemTimeSource(), new ThreadSleeper()));
    }

    /** Test-only entry point for injecting a fake {@link Clock} - see {@code NESTest}'s race-reproducing double. */
    NES(final AudioOutput audioOutput, final Cartridge cartridge, final Clock clock){
        this(audioOutput, VideoOutput.NO_OP, cartridge, clock);
    }

    /** Test-only entry point for injecting both a fake {@link Clock} and a mocked {@link VideoOutput}. */
    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final Cartridge cartridge, final Clock clock){
        final MemoryBus ramBus = new MemoryBus8Bit(new RAM(0x10000));
        //DMC's own sample-address generator (see DMCChannel) only ever produces addresses in
        //$8000-$FFFF (base $C000+, wrapping no lower than $8000) - always within cartridge range,
        //so the cartridge alone is a complete, correct DMA source with no need to route through
        //NESMemoryBus (which would need apu itself to construct, a circular dependency)
        this.apu = new APU(cartridge);
        this.ppu = new PPU(cartridge);
        this.memoryBus = new Latched8BitMemoryBus(new NESMemoryBus(ramBus, apu, cartridge, ppu));
        this.cpu = new MOS6502(memoryBus);
        this.clock = clock;
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
        clock.addListener(() -> {
            final int stallCycles = ppu.consumeOamDmaStallCycles();
            if (stallCycles > 0){
                cpu.stall(stallCycles);
            }
        });
        clock.addListener(() -> resampler.accept(apu.outputSample()).ifPresent(audioOutput::write));
        clock.addListener(() -> {
            if (ppu.consumeFrameReady()){
                videoOutput.present(ppu.rgbFramebuffer());
            }
        });
    }

    public void powerOn(){
        cpu.reset();
        stopRequested = false;
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
        //undoing an earlier stop() - so this wait must not be abandoned early on interrupt. This also
        //means a powerOff() that races ahead of clockThread even starting can't be caught by
        //clock.stop() alone (its call lands before clock.run() does, so it's a no-op too) - that's
        //what stopRequested is for below, checked only once we're sure the clock is genuinely running.
        while (true){
            try {
                clockStarted.await();
                break;
            } catch (InterruptedException e){
                interrupted = true;
            }
        }
        //clockStarted firing only guarantees clockThread has begun executing, not that clock.run() has
        //reached its own "running = true" assignment yet - a real, observed race (not just a timing
        //bound) if this method proceeds to clock.stop() below in that tiny gap: run() unconditionally
        //sets running back to true the moment it starts (see FPSClock's own comment), silently undoing
        //a stop() that raced ahead of it, and clockThread then loops forever since nothing else will
        //ever call stop() again. Closing this gap is a handful of nanoseconds in practice, so a spin is
        //appropriate rather than a second latch/callback plumbed through the general Clock interface.
        while (!clock.isRunning()){
            Thread.onSpinWait();
        }

        if (!interrupted && !stopRequested){
            try {
                Thread.sleep(AUDIO_WARM_UP_MILLIS);
            } catch (InterruptedException e){
                interrupted = true;
            }
        }

        //clock.isRunning() is false if powerOff() ran concurrently during warm-up - audio must not
        //start after a shutdown was already requested, interrupted or not
        if (!interrupted && !stopRequested && clock.isRunning()){
            audioOutput.start();
        }

        if (interrupted || stopRequested){
            //an interrupted warm-up, or a powerOff() that arrived at any point up to here (even
            //before the clock genuinely started, when its own clock.stop() call would have been a
            //no-op), must not silently leave the clock thread running in the background - by this
            //point clockStarted has definitely fired, so this call is guaranteed effective
            clock.stop();
        }
        //retry join() until the thread has genuinely terminated - a single interrupted join() would
        //otherwise return early without actually waiting, silently leaving the clock thread running
        while (clockThread.isAlive()){
            try {
                clockThread.join();
            } catch (InterruptedException e){
                //an interrupt caught only here (audio already legitimately started, so the earlier
                //stop() above never ran) must still stop the clock - otherwise powerOn() would wait
                //forever for a thread nobody ever told to stop, since nothing else will
                interrupted = true;
                clock.stop();
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
        stopRequested = true;
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
