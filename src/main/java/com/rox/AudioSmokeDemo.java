package com.rox;

import com.rox.apu.APU;
import com.rox.audio.Resampler;
import com.rox.audio.SpeakerAudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.clock.FPSClock;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.cpu.mos6502.assembler.AssembledProgram;
import com.rox.cpu.mos6502.assembler.Assembler;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.Memory;
import com.rox.mem.MemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.NESMemoryBus;
import com.rox.mem.RAM;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

/**
 * Manual audible smoke test for the Mixer + real-time audio output phase - not a unit test. Run it
 * directly and listen: it should play each of the five channels in turn - pulse1, pulse2, triangle,
 * noise, then DMC - roughly a third of a second each, then hold the DMC sample (looping) through the
 * system speaker for the remainder of the run.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.AudioSmokeDemo</pre>
 */
public final class AudioSmokeDemo {
    private static final int RUN_SECONDS = 6;
    private static final int PROGRAM_START_ADDRESS = 0x8000;
    //gives the JVM's JIT time to compile the emulation's hot per-cycle tick loop, and lets the
    //ring buffer build up a cushion, before audio playback (and thus underrun risk) begins
    private static final long WARM_UP_MILLIS = 300;

    //DMC sample: alternating full-up/full-down bytes ($4012=$00 -> $C000, $4013=$0F -> 241 bytes),
    //loaded into RAM below before the clock starts. Each byte ramps the delta counter fully in one
    //direction (8 bits, LSB first), so consecutive $FF/$00 bytes step up then down - at rate index 0
    //(428 cycles/bit) that's a ~261Hz buzz. allChannels' DMC control write sets the loop flag, so it
    //keeps cycling through this buffer indefinitely once enabled.
    private static final int DMC_SAMPLE_ADDRESS = 0xC000;
    private static final int DMC_SAMPLE_LENGTH = 241;

    //NROM (mapper 0) has a single 16KB PRG-ROM bank - allChannels is loaded at $8000 within it
    private static final int PRG_ROM_SIZE = 0x4000;

    final static String singleNote = """
                                    LDA #$01      ; enable pulse channel 1 ($4015 bit0) - channels start disabled
                                    STA $4015
                                    LDA #$BF      ; duty=2, halt/loop set (sustain the note), constant volume, volume=15
                                    STA $4000
                                    LDA #$00      ; sweep off
                                    STA $4001
                                    LDA #$FD      ; timer low byte (t=253 -> ~440Hz)
                                    STA $4002
                                    LDA #$00      ; timer high=0, length index=0 (this write also restarts the envelope/sequencer)
                                    STA $4003
                            LOOP:   JMP LOOP
            """;

    final static String arpeggio = """
                        LDA #$01      ; enable pulse channel 1 ($4015 bit0) - channels start disabled
                        STA $4015
                        LDA #$BF      ; duty=2, halt/loop set (sustain each note), constant volume, volume=15
                        STA $4000
                        LDA #$00      ; sweep off
                        STA $4001

                        ; C4 (~261Hz, t=$1AB): retune $4002/$4003, then burn cycles for this note's duration.
                        ; Rewriting the timer while the channel is already sounding just retunes it (no
                        ; re-enable needed); the halt bit above keeps the length counter from silencing it
                        ; between notes.
                        LDA #$AB
                        STA $4002
                        LDA #$01     ; timer high=1, length index=0 (also restarts the envelope/sequencer)
                        STA $4003
                        JSR DELAY
                        JSR DELAY

                        ; E4 (~330Hz, t=$152)
                        LDA #$52
                        STA $4002
                        LDA #$01
                        STA $4003
                        JSR DELAY
                        JSR DELAY

                        ; G4 (~392Hz, t=$11C)
                        LDA #$1C
                        STA $4002
                        LDA #$01
                        STA $4003
                        JSR DELAY
                        JSR DELAY

                        ; C5 (~523Hz, t=$D5)
                        LDA #$D5  
                        STA $4002
                        LDA #$00
                        STA $4003
                        JSR DELAY
                        JSR DELAY

                LOOP:   JMP LOOP     ; hold the last note (still sustained by the halt bit) for the rest of the run

                        ; ~184ms of CPU cycles per call (256x256 nested countdown), so two calls give each
                        ; note ~368ms before the next one retunes the channel.
                DELAY:  LDY #$00
                OUTER:  LDX #$00
                INNER:  DEX
                        BNE INNER
                        DEY
                        BNE OUTER
                        RTS
                """;

    final static String allChannels = """
                        ; DMC setup (must happen before enabling bit 4 below) - loops the 241-byte
                        ; $FF/$00 sample poked into RAM at $C000 by main(), producing a steady
                        ; ~261Hz buzz once enabled.
                        LDA #$40      ; loop=1, IRQ off, rate index 0 (period 428)
                        STA $4010
                        LDA #$00      ; sample address = $C000 + 0*64
                        STA $4012
                        LDA #$0F      ; sample length = 15*16+1 = 241 bytes
                        STA $4013

                        ; ---- Pulse 1 (~440Hz A4) ----
                        LDA #$01      ; enable only pulse1
                        STA $4015
                        LDA #$BF      ; duty=2, halt (sustain), constant volume, volume=15
                        STA $4000
                        LDA #$00      ; sweep off
                        STA $4001
                        LDA #$FD      ; timer low (t=$FD -> ~440Hz)
                        STA $4002
                        LDA #$00      ; timer high=0, length index=0 (also restarts envelope/sequencer)
                        STA $4003
                        JSR DELAY
                        JSR DELAY

                        ; ---- Pulse 2 (~330Hz E4, different duty so it's distinguishable from pulse1) ----
                        LDA #$02      ; enable only pulse2 - disabling pulse1 forces its length counter to 0
                        STA $4015
                        LDA #$3F      ; duty=0, halt, constant volume, volume=15
                        STA $4004
                        LDA #$00      ; sweep off
                        STA $4005
                        LDA #$52      ; timer low (t=$152 -> ~330Hz)
                        STA $4006
                        LDA #$01      ; timer high=1, length index=0
                        STA $4007
                        JSR DELAY
                        JSR DELAY

                        ; ---- Triangle (~261Hz C4) ----
                        LDA #$04      ; enable only triangle - disables pulse2
                        STA $4015
                        LDA #$FF      ; linear-counter control=1 (halt), reload=$7F
                        STA $4008
                        LDA #$AB      ; timer low (t=$1AB -> ~261Hz)
                        STA $400A
                        LDA #$01      ; timer high=1, length index=0 (also requests linear-counter reload)
                        STA $400B
                        JSR DELAY
                        JSR DELAY

                        ; ---- Noise ----
                        LDA #$08      ; enable only noise - disables triangle
                        STA $4015
                        LDA #$3F      ; halt, constant volume, volume=15
                        STA $400C
                        LDA #$06      ; mode=0 (long/hiss sequence), period index=6
                        STA $400E
                        LDA #$00      ; length index=0 (also restarts envelope)
                        STA $400F
                        JSR DELAY
                        JSR DELAY

                        ; ---- DMC ----
                        LDA #$10      ; enable only DMC - disables noise; starts playback of the
                        STA $4015     ; sample set up above (loops, so keeps sounding thereafter)

                LOOP:   JMP LOOP     ; hold DMC playback (looping) for the rest of the run

                        ; ~184ms of CPU cycles per call (256x256 nested countdown); two calls give
                        ; each channel ~368ms before the next one takes over.
                DELAY:  LDY #$00
                OUTER:  LDX #$00
                INNER:  DEX
                        BNE INNER
                        DEY
                        BNE OUTER
                        RTS
                """;

    //allChannels wrapped as a minimal NROM cartridge, so it loads through NESMemoryBus's cartridge
    //routing exactly like a real ROM would, rather than being poked directly into RAM at $8000
    final static Cartridge cartridge = buildCartridge();

    final static Memory ram = new RAM(0x10000);
    final static MemoryBus ramBus = new MemoryBus8Bit(ram);
    final static APU apu = new APU(ramBus);
    final static NESMemoryBus nesMemoryBus = new NESMemoryBus(ramBus, apu, cartridge);
    final static MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(nesMemoryBus));

    final static Resampler resampler = new Resampler(1_789_773, 44_100);

    final static FPSClock clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());

    private AudioSmokeDemo(){
    }

    private static Cartridge buildCartridge(){
        final AssembledProgram assembled = Assembler.assemble(allChannels, PROGRAM_START_ADDRESS);
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        final int[] programBytes = assembled.bytes();
        for (int i = 0; i < programBytes.length; i++){
            fileBytes[header.length + i] = (byte) programBytes[i];
        }
        return RomLoader.fromBytes(fileBytes);
    }

    //XXX Need to tidy all of this up
    public static void main(final String[] args) throws Exception {
        for (int i = 0; i < DMC_SAMPLE_LENGTH; i++){
            ram.write(DMC_SAMPLE_ADDRESS + i, (i % 2 == 0) ? 0xFF : 0x00);
        }

        cpu.setPC(PROGRAM_START_ADDRESS);

        final SpeakerAudioOutput audioOutput = new SpeakerAudioOutput();

        //DIAGNOSTICS (temporary): captures every resampled sample so we can analyse the actual
        //signal for gaps programmatically, instead of relying on human perception
        final double[] captured = new double[250_000];
        final int[] capturedCount = {0};

        clock.addListener(cpu);
        clock.addListener(apu);
        clock.addListener(() -> resampler.accept(apu.outputSample()).ifPresent(sample -> {
            audioOutput.write(sample);
            if (capturedCount[0] < captured.length){
                captured[capturedCount[0]++] = sample;
            }
        }));

        System.out.println("Playing pulse1, pulse2, triangle, noise, then DMC in turn, then holding DMC "
                + "(looping) for " + RUN_SECONDS + " seconds total...");
        final Thread clockThread = new Thread(clock::run);
        clockThread.start();
        Thread.sleep(WARM_UP_MILLIS); //let the clock run (and the ring buffer fill) before we start draining it
        audioOutput.start();
        try {
            Thread.sleep(RUN_SECONDS * 1000L);
        } finally {
            //always stop the clock (a non-daemon thread - left running, it would keep the JVM alive
            //forever) and the audio output, even if sleep above is interrupted
            clock.stop();
            try {
                clockThread.join();
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
            audioOutput.stop();
        }
        analyzeCapturedSignalForGaps(captured, capturedCount[0]);
        System.out.println("Done.");
    }

    /**
     * DIAGNOSTICS (temporary): a normal half-cycle of a ~440Hz wave at 44100Hz is ~50 samples of
     * silence between "on" pulses. Flags any silent run much longer than that as a likely gap.
     */
    private static void analyzeCapturedSignalForGaps(final double[] samples, final int count){
        final int anomalyThresholdSamples = 150; //~3x a normal half-cycle's worth of silence
        int currentZeroRun = 0;
        int longestZeroRun = 0;
        int anomalousRunCount = 0;
        for (int i = 0; i < count; i++){
            if (samples[i] == 0.0){
                currentZeroRun++;
                longestZeroRun = Math.max(longestZeroRun, currentZeroRun);
            } else {
                if (currentZeroRun > anomalyThresholdSamples){
                    anomalousRunCount++;
                    final double atMillis = (i - currentZeroRun) / 44.1;
                    System.err.println("[gap analysis] anomalous silent run of " + currentZeroRun
                            + " samples at ~" + Math.round(atMillis) + "ms");
                }
                currentZeroRun = 0;
            }
        }
        System.err.println("[gap analysis] samples analysed: " + count + ", longest silent run: " + longestZeroRun
                + " samples (~" + String.format("%.2f", longestZeroRun / 44.1) + "ms), anomalous runs: " + anomalousRunCount);
    }
}