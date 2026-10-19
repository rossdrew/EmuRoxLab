package com.rox;

import com.rox.apu.APU;
import com.rox.audio.AudioOutput;
import com.rox.audio.Resampler;
import com.rox.audio.SpeakerAudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.clock.Clock;
import com.rox.clock.FPSClock;
import com.rox.clock.TickRateMonitor;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.debug.cpu.InstructionTrace;
import com.rox.debug.memory.MemoryEditor;
import com.rox.input.ControllerConfiguration;
import com.rox.mem.*;
import com.rox.ppu.PPU;
import com.rox.save.SystemSnapshot;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;
import com.rox.video.VideoOutput;

import javax.sound.sampled.LineUnavailableException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.zip.CRC32;

public class NES {
    private static final long CPU_HZ = 1_789_773;
    /**
     * Far beyond any real instruction (7 cycles max, plus a 514-cycle OAM DMA stall and a 7-cycle
     * interrupt entry) - only reached if the CPU somehow never finishes, which would otherwise hang the
     * UI thread that asked for the step.
     */
    static final int MAX_CYCLES_PER_STEPPED_INSTRUCTION = 10_000;
    /** Over three NTSC frames (29,780.5 CPU cycles each) - only reached if the PPU somehow stops finishing frames. */
    static final int MAX_CYCLES_PER_STEPPED_FRAME = 100_000;
    /** The NES's 2KB of CPU RAM - NESMemoryBus mirrors it across $0000-$1FFF. */
    private static final int CPU_RAM_SIZE = NESMemoryBus.CPU_RAM_MIRROR_MASK + 1;
    private static final long AUDIO_SAMPLE_RATE_HZ = 44_100;
    /**
     * Gives the JVM's JIT time to compile the emulation's hot per-cycle tick loop, and lets the ring buffer build
     * up a cushion, before audio playback (and thus underrun risk) begins. See {@link SpeakerAudioOutput} class doc
     * for why draining starts empty otherwise
     */
    private static final long AUDIO_WARM_UP_MILLIS = 300;

    private final MOS6502 cpu;
    private final APU apu;
    private final PPU ppu;
    private final Clock clock;
    private final LatchedMemoryBus memoryBus;
    private final AudioOutput audioOutput;
    private final RAM ram;
    private final Cartridge cartridge;
    private final TickRateMonitor tickRateMonitor;
    private final MemoryEditor memoryEditor;
    private final InstructionTrace instructionTrace;
    private final Resampler resampler;
    private final VideoOutput videoOutput;
    private final ControllerConfiguration controllers;
    /** set only once pause() has genuinely parked the clock - the precondition for capturing/restoring state while powered on (see captureSnapshot()) */
    private volatile boolean paused;
    /** every frame handed to videoOutput - what stepFrame() watches for */
    private volatile long framesPresented;
    /** set by {@link #powerOff()}, so {@link #powerOn()} skips warm-up and audio once a shutdown is asked for */
    private volatile boolean stopRequested;
    /** guards {@link #powerOn()}'s setup and audio start against {@link #powerOff()} */
    private final Object lifecycleLock = new Object();

    public NES(final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), VideoOutput.NO_OP, ControllerConfiguration.NONE, cartridge);
    }

    /** Like {@link #NES(Cartridge)}, but presenting frames to {@code videoOutput} as they complete. */
    public NES(final VideoOutput videoOutput, final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), videoOutput, ControllerConfiguration.NONE, cartridge);
    }

    /** Like {@link #NES(VideoOutput, Cartridge)}, but driven by {@code controllers} rather than {@link ControllerConfiguration#NONE}. */
    public NES(final VideoOutput videoOutput, final ControllerConfiguration controllers, final Cartridge cartridge) throws LineUnavailableException {
        this(new SpeakerAudioOutput(), videoOutput, controllers, cartridge);
    }

    NES(final AudioOutput audioOutput, final Cartridge cartridge){
        this(audioOutput, VideoOutput.NO_OP, ControllerConfiguration.NONE, cartridge);
    }

    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final Cartridge cartridge){
        this(audioOutput, videoOutput, ControllerConfiguration.NONE, cartridge);
    }

    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final ControllerConfiguration controllers, final Cartridge cartridge){
        this(audioOutput, videoOutput, controllers, cartridge, new FPSClock(CPU_HZ, 60, new SystemTimeSource(), new ThreadSleeper()));
    }

    /** Test-only entry point for injecting a fake {@link Clock} - see {@code NESTest}'s race-reproducing double. */
    NES(final AudioOutput audioOutput, final Cartridge cartridge, final Clock clock){
        this(audioOutput, VideoOutput.NO_OP, cartridge, clock);
    }

    /** Test-only entry point for injecting both a fake {@link Clock} and a mocked {@link VideoOutput}. */
    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final Cartridge cartridge, final Clock clock){
        this(audioOutput, videoOutput, ControllerConfiguration.NONE, cartridge, clock);
    }

    /** Test-only entry point for injecting a fake {@link Clock} alongside a real {@link ControllerConfiguration}. */
    NES(final AudioOutput audioOutput, final VideoOutput videoOutput, final ControllerConfiguration controllers,
        final Cartridge cartridge, final Clock clock){
        this.ram = new RAM(CPU_RAM_SIZE);
        this.cartridge = cartridge;
        final MemoryBus ramBus = new MemoryBus8Bit(ram);
        //DMC's own sample-address generator (see DMCChannel) only ever produces addresses in
        //$8000-$FFFF (base $C000+, wrapping no lower than $8000) - always within cartridge range,
        //so the cartridge alone is a complete, correct DMA source with no need to route through
        //NESMemoryBus (which would need apu itself to construct, a circular dependency)
        this.apu = new APU(cartridge);
        this.ppu = new PPU(cartridge);
        this.memoryBus = new Latched8BitMemoryBus(new NESMemoryBus(ramBus, apu, cartridge, ppu, controllers));
        this.cpu = new MOS6502(memoryBus);
        this.tickRateMonitor = new TickRateMonitor(new SystemTimeSource());
        this.memoryEditor = new MemoryEditor(ram::write, cartridge::writePrgRamWithoutSaving);
        this.instructionTrace = new InstructionTrace(cpu::isAtInstructionBoundary, cpu::programCounter,
                DebugCapture.sideEffectFreePeek(ram::read, cartridge), cpu::packedRegisters, cpu::packedFlags,
                InstructionTrace.DEFAULT_CAPACITY);
        this.clock = clock;
        this.audioOutput = audioOutput;

        this.resampler = new Resampler(CPU_HZ, AUDIO_SAMPLE_RATE_HZ);
        this.videoOutput = videoOutput;
        this.controllers = controllers;
        //one listener doing every component's work in a fixed order (see tick()), rather than one listener
        //per component: the clock's dispatch then sees a single type, so the JIT can inline the whole cycle
        clock.addListener(this::tick);
    }

    /**
     * One CPU cycle of the whole machine, in an order that matters - each step sees the ones before it this
     * cycle.
     */
    private void tick(){
        tickRateMonitor.tick();
        cpu.tick();
        //right after the CPU, so a locked byte is back at its value before anything else sees the tick's state
        memoryEditor.tick();
        apu.tick();
        ppu.tick();
        cpu.setIRQLine(apu.isIrqAsserted() || cartridge.isIrqAsserted());
        if (ppu.consumeNmiEdge()){
            cpu.signalNMI();
        }
        final int stallCycles = ppu.consumeOamDmaStallCycles();
        if (stallCycles > 0){
            cpu.stall(stallCycles);
        }
        //after the OAM DMA stall above, so a stall starting this tick holds recording back rather than the
        //same instruction being recorded twice - see InstructionTrace's class doc
        instructionTrace.tick();
        if (resampler.add(apu.outputSample())){
            audioOutput.write(resampler.output());
        }
        //polling the (possibly real, physical) gamepads is folded into this same frame-ready gate -
        //PPU.consumeFrameReady() is a one-shot flag (it resets itself the moment it's read true), so
        //checking it a second time would simply never see true
        if (ppu.consumeFrameReady()){
            framesPresented++;
            videoOutput.present(ppu.rgbFramebuffer());
            controllers.player1().poll();
            controllers.player2().poll();
            controllers.player3().poll();
            controllers.player4().poll();
        }
    }

    public void powerOn(){
        //signalled once the clock thread is executing, so a slow thread start can't eat into the warm-up
        final CountDownLatch clockStarted = new CountDownLatch(1);
        final Thread clockThread = new Thread(() -> {
            clockStarted.countDown();
            clock.run();
        });
        synchronized (lifecycleLock){
            cpu.reset();
            stopRequested = false;
            clock.prepareToRun(); //a stop() from here on ends the run, even one landing before run() starts
            clockThread.start();
        }

        boolean interrupted = false;
        try {
            clockStarted.await();
            if (!stopRequested){
                Thread.sleep(AUDIO_WARM_UP_MILLIS);
            }
        } catch (InterruptedException e){
            interrupted = true;
        }
        synchronized (lifecycleLock){
            //under the lock, so a powerOff() can't land between this check and start() and leave audio on
            if (!interrupted && !stopRequested){
                audioOutput.start();
            }
        }

        if (interrupted){
            clock.stop();
        }
        //retry join() until the thread has genuinely terminated - a single interrupted join() would
        //otherwise return early without actually waiting, silently leaving the clock thread running
        while (clockThread.isAlive()){
            try {
                clockThread.join();
            } catch (InterruptedException e){
                //an interrupt caught only here (audio already started) must still stop the clock - nothing else will
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

    /**
     * Freezes a running emulation without resetting or tearing anything down: the clock parks at the
     * end of its current frame, then the CPU is single-stepped to the end of whatever instruction it
     * was part-way through - so CPU state is fully captured by {@code getEnvironmentSnapshot()} for as
     * long as the pause lasts - and audio is silenced. {@link #resume()} carries on from exactly there.
     *
     * @return false if there was nothing running to pause
     */
    public boolean pause(){
        if (!clock.pause()){
            return false;
        }
        //safe to tick from this thread: a true pause() guarantees the clock thread is parked
        finishInFlightInstruction();
        audioOutput.pause();
        paused = true;
        return true;
    }

    /** True from a successful {@link #pause()} until {@link #resume()}. */
    public boolean isPaused(){
        return paused;
    }

    /** Carry on after a {@link #pause()}; does nothing if not paused. */
    public void resume(){
        paused = false;
        audioOutput.resume();
        clock.resume();
    }

    /**
     * The whole system's state, for a save state or debug capture. Only while {@link #pause()}d (or
     * before/after the clock runs at all) - otherwise the clock thread would be changing it mid-copy.
     * A clock that simply stopped (e.g. after {@link #powerOff()}) can leave the CPU part-way through an
     * instruction, unlike a pause; that instruction is finished first, so this may advance the system
     * by a few cycles.
     *
     * <p>Not captured: controller shift registers (a pause landing mid-controller-read could misread
     * one frame of input after a restore), and the audio resampler's fractional position.
     *
     * @throws IllegalStateException if the clock is running and not paused
     */
    public SystemSnapshot captureSnapshot(){
        requireNothingTicking();
        finishInFlightInstruction();
        return new SystemSnapshot(romCrc32(cartridge), cpu.snapshot(), ppu.snapshot(), apu.snapshot(), ram.snapshot(),
                cartridge.snapshot());
    }

    /**
     * Puts the whole system back as {@code snapshot} captured it. Same "paused or not running"
     * precondition as {@link #captureSnapshot()}.
     *
     * @throws IllegalStateException if the clock is running and not paused
     * @throws IllegalArgumentException if {@code snapshot} was taken with a different cartridge, or any of
     * its parts is the wrong shape - checked up front, so a rejected snapshot changes nothing
     */
    public void restoreSnapshot(final SystemSnapshot snapshot){
        requireNothingTicking();
        if (snapshot.romCrc32() != romCrc32(cartridge)){
            throw new IllegalArgumentException("Snapshot was taken with a different cartridge");
        }
        //every component that can reject a malformed snapshot (array sizes, mapper type) is checked before
        //any is restored - a CRC-valid save file can still decode to the wrong shapes, and stopping
        //part-way would leave a mix of old and restored state. The APU and CPU snapshots are plain
        //values with nothing to reject.
        cartridge.checkRestorable(snapshot.mapper());
        ram.checkRestorable(snapshot.ram());
        ppu.checkRestorable(snapshot.ppu());
        cartridge.restore(snapshot.mapper());
        ram.restore(snapshot.ram());
        ppu.restore(snapshot.ppu());
        apu.restore(snapshot.apu());
        cpu.restore(snapshot.cpu());
    }

    /** Ticks until the CPU is between instructions - callers must already know nothing else is ticking. */
    /**
     * Runs exactly one instruction - from wherever the CPU is, to the next instruction boundary (so a
     * part-finished instruction is just finished). An OAM DMA stall or interrupt entry that follows is
     * part of the same step. Only while {@link #pause()}d (or with the clock not running), like
     * {@link #captureSnapshot()}.
     *
     * @throws IllegalStateException if the clock is running and not paused, or the CPU doesn't reach an
     * instruction boundary within {@link #MAX_CYCLES_PER_STEPPED_INSTRUCTION} cycles
     */
    public void stepInstruction(){
        requireNothingTicking();
        int cycles = 0;
        do {
            if (cycles++ == MAX_CYCLES_PER_STEPPED_INSTRUCTION){
                throw new IllegalStateException("CPU didn't finish an instruction within "
                        + MAX_CYCLES_PER_STEPPED_INSTRUCTION + " cycles");
            }
            clock.tick();
        } while (!cpu.isAtInstructionBoundary());
    }

    /**
     * Runs exactly {@code cycles} CPU cycles - which can leave the CPU part-way through an instruction.
     * Same "paused or not running" precondition as {@link #stepInstruction()}.
     *
     * @throws IllegalArgumentException if {@code cycles} isn't positive
     */
    public void stepCycles(final int cycles){
        requireNothingTicking();
        if (cycles < 1){
            throw new IllegalArgumentException("Step at least one cycle, not " + cycles);
        }
        for (int i = 0; i < cycles; i++){
            clock.tick();
        }
    }

    /**
     * Runs until the next frame is finished and presented (the start of vertical blanking), then
     * finishes the instruction in progress - so each step shows exactly one new frame and stops at the
     * same point in it. A fixed cycle count can't do that: an NTSC frame is 29,780.5 CPU cycles. Same
     * "paused or not running" precondition as {@link #stepInstruction()}.
     *
     * @throws IllegalStateException if the clock is running and not paused, or no frame finishes within
     * {@link #MAX_CYCLES_PER_STEPPED_FRAME} cycles
     */
    public void stepFrame(){
        stepFrame(MAX_CYCLES_PER_STEPPED_FRAME);
    }

    /** {@link #stepFrame()} with a given cap - a test seam, since a real PPU always finishes a frame well inside the real one. */
    void stepFrame(final int maxCycles){
        requireNothingTicking();
        final long startingFrame = framesPresented;
        int cycles = 0;
        while (framesPresented <= startingFrame){
            if (cycles++ == maxCycles){
                throw new IllegalStateException("No frame finished within " + maxCycles + " cycles");
            }
            clock.tick();
        }
        finishInFlightInstruction();
    }

    private void finishInFlightInstruction(){
        while (!cpu.isAtInstructionBoundary()){
            clock.tick();
        }
    }

    /**
     * A debugging edit: sets one byte of CPU RAM ({@code $0000-$1FFF}, mirrored) or PRG-RAM
     * ({@code $6000-$7FFF}) any time. A PRG-RAM edit doesn't trigger a battery save.
     * <ul>
     *     <li><b>running?</b>: applied by the clock thread at its next tick, never written from this thread into memory the emulation is using.</li>
     *     <li><b>paused/not running?</b>: applied now!</li>
     * </ul>
     * 
     * @throws IllegalArgumentException for any other address, or a value outside 0-255
     */
    public void pokeMemory(final int address, final int value){
        if (nothingTicking()){
            memoryEditor.write(address, value);
        } else {
            memoryEditor.queue(address, value);
        }
    }

    /**
     * Holds a byte at {@code value} - set now (as {@link #pokeMemory}), then re-applied every tick, so
     * whatever the game writes there doesn't stick - until {@link #unlockMemory}.
     *
     * @throws IllegalArgumentException as {@link #pokeMemory}
     */
    public void lockMemory(final int address, final int value){
        memoryEditor.lock(address, value);
        pokeMemory(address, value);
    }

    public void unlockMemory(final int address){
        memoryEditor.unlock(address);
    }

    /** Locked bytes, address to value, in address order (RAM by its real {@code $0000-$07FF} address). */
    public Map<Integer, Integer> lockedMemory(){
        return memoryEditor.locks();
    }

    private boolean nothingTicking(){
        return !clock.isRunning() || paused;
    }

    private void requireNothingTicking(){
        if (!nothingTicking()){
            throw new IllegalStateException("Pause the NES before capturing, restoring or stepping it");
        }
    }

    /** Identifies a cartridge's ROM (PRG then CHR) - what {@link SystemSnapshot#romCrc32()} is checked against. */
    private static long romCrc32(final Cartridge cartridge){
        final CRC32 crc = new CRC32();
        crc.update(cartridge.rom().prgRom());
        crc.update(cartridge.rom().chrRom());
        return crc.getValue();
    }

    /**
     * Stops the emulation for good. Returns only once nothing can tick any more - even when called while
     * {@link #powerOn()} is still starting the clock - so {@link #pokeMemory}, {@link #captureSnapshot} or a
     * step can then act from this thread safely.
     */
    public void powerOff(){
        synchronized (lifecycleLock){
            stopRequested = true;
            clock.stopAndWait();
            audioOutput.stop();
        }
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

    RAM ram(){
        return ram;
    }

    TickRateMonitor tickRateMonitor(){
        return tickRateMonitor;
    }

    InstructionTrace instructionTrace(){
        return instructionTrace;
    }

    /** The CPU clock rate a real NTSC NES runs at - what {@link #tickRateMonitor()}'s measured rate should match. */
    static long intendedCpuHz(){
        return CPU_HZ;
    }

    Cartridge cartridge(){
        return cartridge;
    }

    Clock clock(){
        return clock;
    }
}
