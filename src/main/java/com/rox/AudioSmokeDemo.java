package com.rox;

import com.rox.apu.APU;
import com.rox.audio.Resampler;
import com.rox.audio.SpeakerAudioOutput;
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
 * directly and listen: it should play a short ascending arpeggio (C4-E4-G4-C5) on pulse channel 1,
 * then hold the last note through the system speaker for the remainder of the run.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.AudioSmokeDemo</pre>
 */
public final class AudioSmokeDemo {
    private static final int RUN_SECONDS = 4;
    private static final int PROGRAM_START_ADDRESS = 0x8000;
    //gives the JVM's JIT time to compile the emulation's hot per-cycle tick loop, and lets the
    //ring buffer build up a cushion, before audio playback (and thus underrun risk) begins
    private static final long WARM_UP_MILLIS = 300;

    final static Memory ram = new RAM(0x10000);
    final static MemoryBus ramBus = new MemoryBus8Bit(ram);
    final static APU apu = new APU(ramBus);
    final static NESMemoryBus nesMemoryBus = new NESMemoryBus(ramBus, apu);
    final static MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(nesMemoryBus));

    final static Resampler resampler = new Resampler(1_789_773, 44_100);

    final static FPSClock clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());

    final static String singleNote = """
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

    private AudioSmokeDemo(){
    }

    //XXX Need to tidy all of this up
    public static void main(final String[] args) throws Exception {
        final AssembledProgram assembled = Assembler.assemble(arpeggio, PROGRAM_START_ADDRESS);
        for (int i = 0; i < assembled.length(); i++){
            ram.write(assembled.startAddress() + i, assembled.bytes()[i]);
        }

        cpu.setPC(assembled.startAddress());

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

        System.out.println("Playing a short ascending arpeggio (C4-E4-G4-C5), then holding the last note, for "
                + RUN_SECONDS + " seconds...");
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