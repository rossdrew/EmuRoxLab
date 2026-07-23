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
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.NESMemoryBus;
import com.rox.mem.RAM;
import com.rox.time.SystemTimeSource;
import com.rox.time.ThreadSleeper;

/**
 * Manual audible smoke test for the Mixer + real-time audio output phase - not a unit test. Run it
 * directly and listen: it should play a steady ~440Hz tone on pulse channel 1 through the system
 * speaker for a few seconds.
 *
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.rox.AudioSmokeDemo</pre>
 */
public final class AudioSmokeDemo {
    private static final int RUN_SECONDS = 4;
    private static final int PROGRAM_START_ADDRESS = 0x8000;

    private AudioSmokeDemo(){
    }

    public static void main(final String[] args) throws Exception {
        final String program = """
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

        final Memory ram = new RAM(0x10000);
        final AssembledProgram assembled = Assembler.assemble(program, PROGRAM_START_ADDRESS);
        for (int i = 0; i < assembled.length(); i++){
            ram.write(assembled.startAddress() + i, assembled.bytes()[i]);
        }

        final APU apu = new APU();
        final NESMemoryBus nesMemoryBus = new NESMemoryBus(new MemoryBus8Bit(ram), apu);
        final MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(nesMemoryBus));
        cpu.setPC(assembled.startAddress());

        final SpeakerAudioOutput audioOutput = new SpeakerAudioOutput();
        final Resampler resampler = new Resampler(1_789_773, 44_100);

        final FPSClock clock = new FPSClock(1_789_773, 60, new SystemTimeSource(), new ThreadSleeper());
        clock.addListener(cpu);
        clock.addListener(apu);
        clock.addListener(() -> resampler.accept(apu.outputSample()).ifPresent(audioOutput::write));

        System.out.println("Playing a steady ~440Hz tone for " + RUN_SECONDS + " seconds...");
        audioOutput.start();
        final Thread clockThread = new Thread(clock::run);
        clockThread.start();
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
        System.out.println("Done.");
    }
}
