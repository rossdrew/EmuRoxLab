package com.rox;

import com.rox.apu.APU;
import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Wiring-level checks only, using a mocked {@link AudioOutput} rather than the real
 * {@code SpeakerAudioOutput} - a unit test shouldn't depend on audio hardware being present (e.g.
 * in CI). Whether the audio actually sounds correct is a manual smoke test, not something a unit
 * test can a
 * ssert.
 */
public class NESTest {
    private static final int PRG_ROM_SIZE = 0x4000;

    /** A minimal single-bank NROM (mapper 0) cartridge, PRG-ROM all zero - enough to plug into an NES. */
    private static Cartridge blankCartridge(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        return RomLoader.fromBytes(fileBytes);
    }

    /** A cartridge whose PRG-ROM (and so $C000, the default DMC sample address) reads back {@code $FF}. */
    private static Cartridge cartridgeWithNonZeroByteAtDmcSampleAddress(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        fileBytes[header.length + ((0xC000 - 0x8000) % PRG_ROM_SIZE)] = (byte) 0xFF;
        return RomLoader.fromBytes(fileBytes);
    }

    /** A cartridge whose reset vector points at a single "JMP $9000" instruction, looping on itself. */
    private static Cartridge selfLoopingCartridge(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        final int jmpOffset = header.length + ((0x9000 - 0x8000) % PRG_ROM_SIZE);
        fileBytes[jmpOffset] = 0x4C; //JMP absolute
        fileBytes[jmpOffset + 1] = 0x00; //target low byte
        fileBytes[jmpOffset + 2] = (byte) 0x90; //target high byte -> $9000 (itself)
        final int resetVectorOffset = header.length + ((0xFFFC - 0x8000) % PRG_ROM_SIZE); //mirrors into the 16KB bank
        fileBytes[resetVectorOffset] = 0x00; //reset vector low byte
        fileBytes[resetVectorOffset + 1] = (byte) 0x90; //reset vector high byte -> $9000
        return RomLoader.fromBytes(fileBytes);
    }

    /**
     * A cartridge whose reset vector points at a "JMP $9000" self-loop, and whose NMI vector points
     * at a separate "JMP $9100" self-loop - lets a test tell "still waiting for NMI" and "NMI was
     * serviced" apart just by watching where the CPU's PC settles.
     */
    private static Cartridge nmiSelfLoopingCartridge(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);

        final int resetLoopOffset = header.length + ((0x9000 - 0x8000) % PRG_ROM_SIZE);
        fileBytes[resetLoopOffset] = 0x4C; //JMP absolute
        fileBytes[resetLoopOffset + 1] = 0x00;
        fileBytes[resetLoopOffset + 2] = (byte) 0x90; //-> $9000 (itself)

        final int nmiLoopOffset = header.length + ((0x9100 - 0x8000) % PRG_ROM_SIZE);
        fileBytes[nmiLoopOffset] = 0x4C; //JMP absolute
        fileBytes[nmiLoopOffset + 1] = 0x00;
        fileBytes[nmiLoopOffset + 2] = (byte) 0x91; //-> $9100 (itself)

        final int resetVectorOffset = header.length + ((0xFFFC - 0x8000) % PRG_ROM_SIZE);
        fileBytes[resetVectorOffset] = 0x00;
        fileBytes[resetVectorOffset + 1] = (byte) 0x90; //reset vector -> $9000

        final int nmiVectorOffset = header.length + ((0xFFFA - 0x8000) % PRG_ROM_SIZE);
        fileBytes[nmiVectorOffset] = 0x00;
        fileBytes[nmiVectorOffset + 1] = (byte) 0x91; //NMI vector -> $9100

        return RomLoader.fromBytes(fileBytes);
    }

    @Test
    public void constructsWithCpuAndApuWiredOntoTheSharedClock(){
        new NES(mock(AudioOutput.class), blankCartridge());
    }

    @Test
    public void powerOnStartsAudioOutputRunsAndFeedsItSamplesUntilPoweredOff() throws InterruptedException {
        final AudioOutput audioOutput = mock(AudioOutput.class);
        final NES nes = new NES(audioOutput, blankCartridge());

        final Thread thread = new Thread(nes::powerOn);
        thread.start();

        verify(audioOutput, timeout(2000).atLeastOnce()).write(anyDouble());
        //wait until start() has actually happened, then give it a moment: powerOn() must still be
        //blocked (parked on the internal clock thread) at this point, not merely between calls
        verify(audioOutput, timeout(2000)).start();
        Thread.sleep(200);
        assertTrue(thread.isAlive(), "powerOn() should still be blocking (waiting on the clock thread) until powerOff() is called");

        nes.powerOff();
        thread.join(2000);

        assertFalse(thread.isAlive(), "clock thread should have stopped within the join budget");
        verify(audioOutput).stop();
    }

    /**
     * SpeakerAudioOutput drains a ring buffer that {@link AudioOutput#write} feeds and
     * {@link AudioOutput#start()}'s writer thread empties - starting playback before any samples
     * exist immediately underruns (audible clicks). powerOn() must let the clock tick (and so
     * start feeding samples) for a warm-up period *before* starting audio output, giving the
     * buffer a cushion.
     */
    @Test
    public void powerOnLetsTheClockWarmUpBeforeStartingAudioOutput() throws InterruptedException {
        final AudioOutput audioOutput = mock(AudioOutput.class);
        final long[] firstWriteNanos = {-1};
        final long[] startNanos = {-1};
        doAnswer(invocation -> {
            if (firstWriteNanos[0] == -1){
                firstWriteNanos[0] = System.nanoTime();
            }
            return null;
        }).when(audioOutput).write(anyDouble());
        doAnswer(invocation -> {
            startNanos[0] = System.nanoTime();
            return null;
        }).when(audioOutput).start();

        final NES nes = new NES(audioOutput, blankCartridge());
        final Thread thread = new Thread(nes::powerOn);
        thread.start();

        verify(audioOutput, timeout(2000)).start();
        verify(audioOutput, atLeastOnce()).write(anyDouble());

        nes.powerOff();
        thread.join(2000);

        assertTrue(firstWriteNanos[0] > 0 && startNanos[0] > 0, "expected both write() and start() to have been invoked");
        assertTrue(firstWriteNanos[0] < startNanos[0],
                "the clock should begin producing samples before audio output starts, giving the ring buffer a cushion");
        final long warmUpGapMillis = (startNanos[0] - firstWriteNanos[0]) / 1_000_000;
        assertTrue(warmUpGapMillis >= 100,
                "expected a substantial warm-up gap before audio output starts, was " + warmUpGapMillis + "ms");
    }

    /**
     * Interrupting the thread running powerOn() before it reaches the warm-up sleep means that sleep
     * throws immediately (Thread.sleep() checks interrupt status at entry) - powerOn() must catch
     * that, restore the interrupt status per standard practice, and carry on rather than propagating
     * or aborting. Restoring the status then means the very next blocking call (clockThread.join())
     * also observes it set and throws immediately too, exercising both catch blocks from one
     * interrupt() call.
     */
    @Test
    public void powerOnGracefullyHandlesInterruptionDuringWarmUpInsteadOfPropagatingOrHanging() throws InterruptedException {
        final AudioOutput audioOutput = mock(AudioOutput.class);
        final NES nes = new NES(audioOutput, blankCartridge());

        final Thread thread = new Thread(nes::powerOn);
        thread.start();
        thread.interrupt();

        thread.join(2000);
        assertFalse(thread.isAlive(),
                "powerOn() should return promptly when interrupted, not propagate InterruptedException or hang in join()");
        verify(audioOutput, timeout(2000)).start();

        //the internal clock thread is still running (powerOn() returned without genuinely waiting
        //for it, since join() threw immediately) - powerOff() stops it regardless
        nes.powerOff();
    }

    @Test
    public void powerOnResetsTheCpuToTheCartridgesResetVector() throws InterruptedException {
        final NES nes = new NES(mock(AudioOutput.class), selfLoopingCartridge());

        final Thread thread = new Thread(nes::powerOn);
        thread.start();

        //bounded busy-poll rather than a fixed sleep - the CPU should reach and then keep re-hitting
        //$9000 almost immediately if (and only if) powerOn() actually called cpu.reset()
        final long deadline = System.currentTimeMillis() + 2000;
        boolean reachedResetTarget = false;
        while (System.currentTimeMillis() < deadline){
            if (nes.cpu().getEnvironmentSnapshot().getPC() == 0x9000){
                reachedResetTarget = true;
                break;
            }
        }

        nes.powerOff();
        thread.join(2000);

        assertTrue(reachedResetTarget, "PC never reached $9000 - powerOn() should reset the CPU to the cartridge's reset vector");
    }

    /**
     * DMC's own sample-address generator only ever produces addresses in $8000-$FFFF (see
     * DMCChannel), so a real DMC sample always lives in cartridge PRG-ROM - proves the APU's DMA
     * reads actually reach the cartridge rather than the NES's internal RAM (which starts, and for
     * this cartridge's PRG-ROM content would coincidentally also be, all zero).
     *
     * Compares against a captured baseline rather than an absolute 0 - TriangleChannel's
     * outputSample() intentionally never returns exactly 0 (it freezes at its last sequence value,
     * defaulting to a nonzero 15, matching a documented real-hardware quirk), so the mixed
     * apu.outputSample() is never 0.0 even with every channel otherwise silent/disabled.
     */
    @Test
    public void dmcSampleFetchesReadFromCartridgePrgRomNotInternalRam(){
        final NES nes = new NES(mock(AudioOutput.class), cartridgeWithNonZeroByteAtDmcSampleAddress());
        final APU apu = nes.apu();
        final double baseline = apu.outputSample();

        apu.write(0x4010, 0x00); //IRQ off, loop off, rate index 0 (period 428)
        apu.write(0x4012, 0x00); //sample address $C000
        apu.write(0x4013, 0x00); //sample length: 1 byte
        apu.write(0x4015, 0x10); //enable DMC - starts playback, fetching the single ($FF) byte immediately

        //generously bounded poll (real requirement is ~2*(428+1) ticks for one shift-register clock,
        //which is enough for the sample byte's first ('1') bit to move the delta counter off zero)
        final int maxTicks = 5_000;
        int ticks = 0;
        while (apu.outputSample() == baseline){
            if (++ticks > maxTicks){
                fail("DMC output never moved off baseline - sample byte wasn't read as $FF, so it isn't coming from the cartridge");
            }
            nes.clock().tick();
        }
    }

    /**
     * End-to-end proof that the phase-3 PPU vblank/NMI wiring in the constructor actually reaches
     * the real {@code MOS6502} - {@code PPUTest} only proves {@code consumeNmiEdge()} in isolation.
     */
    @Test
    public void vblankNmiReachesTheCpusRealNmiLine(){
        final NES nes = new NES(mock(AudioOutput.class), nmiSelfLoopingCartridge());
        nes.cpu().reset(); //lands at $9000, the reset self-loop
        nes.ppu().write(0x2000, 0x80); //enable NMI generation on vblank

        //generously bounded poll (real requirement is PPU.TICKS_UNTIL_VBLANK_START, ~27,394 ticks)
        //rather than a hand-derived exact count, so this isn't fragile against timing details
        final int maxTicks = 100_000;
        int ticks = 0;
        while (nes.cpu().getEnvironmentSnapshot().getPC() != 0x9100){
            if (++ticks > maxTicks){
                fail("PPU vblank NMI never reached the CPU's real NMI line within " + maxTicks + " ticks");
            }
            nes.clock().tick();
        }
    }

    /**
     * Companion to {@link #vblankNmiReachesTheCpusRealNmiLine()}: proves NMI does NOT fire on just
     * any tick - only a real edge should trigger it. Without this, a broken "fire on every tick
     * instead of just on an edge" wiring bug would still pass the other test (it would just reach
     * $9100 even sooner) without ever being caught.
     */
    @Test
    public void vblankNmiDoesNotFireBeforeTheRealVblankEdge(){
        final NES nes = new NES(mock(AudioOutput.class), nmiSelfLoopingCartridge());
        nes.cpu().reset(); //lands at $9000
        nes.ppu().write(0x2000, 0x80); //enable NMI generation on vblank

        //real vblank start is ~27,394 ticks away - PC must still be cycling within the reset loop's
        //own 3 bytes (JMP $9000 fetches its opcode at $9000, then operand bytes at $9001/$9002
        //before landing back on $9000) well before that, not off in the NMI target ($9100)
        for (int i = 0; i < 1000; i++){
            nes.clock().tick();
        }

        final int pc = nes.cpu().getEnvironmentSnapshot().getPC();
        assertTrue(pc >= 0x9000 && pc <= 0x9002,
                "PC should still be cycling within the reset loop (was $" + Integer.toHexString(pc)
                        + ") - NMI must not fire before the real vblank edge");
    }

    /**
     * End-to-end proof that the phase-7 IRQ wiring in the constructor actually reaches the real
     * {@code MOS6502} - APUTest only proves the wiring logic in isolation against a mocked APU.
     * Drives the real clock (single-stepped via {@code tick()}, not the real-time-throttled
     * {@code run()}) directly, rather than re-deriving the listener wiring in the test itself.
     */
    @Test
    public void dmcExhaustionIrqReachesTheCpusRealIrqLine(){
        final NES nes = new NES(mock(AudioOutput.class), blankCartridge());
        final APU apu = nes.apu();

        apu.write(0x4010, 0x80); //DMC: IRQ enable, rate index 0 (period 428)
        apu.write(0x4012, 0x00); //sample address $C000
        apu.write(0x4013, 0x00); //sample length: 1 byte
        apu.write(0x4015, 0x10); //enable DMC - starts playback, fetching the single byte immediately

        assertFalse(nes.cpu().getEnvironmentSnapshot().isIRQLineAsserted(), "not pending until that one byte's 8 bits have shifted out");

        //generously bounded poll (real requirement is ~2*(428+1)*8 ticks) rather than a hand-derived
        //exact count, so this isn't fragile against the ticker's own timing details
        final int maxTicks = 100_000;
        int ticks = 0;
        while (!nes.cpu().getEnvironmentSnapshot().isIRQLineAsserted()){
            if (++ticks > maxTicks){
                fail("DMC IRQ never reached the CPU's IRQ line within " + maxTicks + " ticks");
            }
            nes.clock().tick();
        }
    }
}
