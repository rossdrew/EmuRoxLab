package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.clock.TickRateMonitor;
import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.debug.cpu.InstructionTrace;
import com.rox.debug.labels.MemoryLabel;
import com.rox.ui.debugger.DebugTarget;
import com.rox.ui.debugger.NesMemoryMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NesRoxUiTargetTest {
    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000 (2 + 3 + 3 cycles per loop). PRG-ROM byte at $8000 is $42. */
    private static NES nes(){
        final Cartridge cartridge = TestRoms.nrom().prg(0, 0x42).at(0x9000, 0xE8, 0x86, 0x10, 0x4C, 0x00, 0x90).resetVector(0x9000).cartridge();
        final NES nes = new NES(mock(AudioOutput.class), cartridge, new ManuallyTickedClock());
        nes.cpu().reset();
        return nes;
    }

    private static NesRoxUiTarget target(final NES nes){
        return new NesRoxUiTarget(nes, List.of());
    }

    private static void runLoops(final NES nes, final int loops){
        for (int i = 0; i < loops * 8; i++){
            nes.clock().tick();
        }
    }

    @Test
    public void coversTheWholeCpuAddressSpaceWithTheNesMap(){
        final NesRoxUiTarget target = target(nes());

        assertEquals(0x10000, target.size());
        assertSame(target, target.memory());
        assertSame(NesMemoryMap.REGIONS, target.regions());
    }

    @Test
    public void readsAreSideEffectFreeNesReads(){
        final NES nes = nes();
        runLoops(nes, 3);
        final NesRoxUiTarget target = target(nes);
        final IntUnaryOperator reader = target.reader();

        assertEquals(3, target.peek(0x0010));
        assertEquals(3, reader.applyAsInt(0x0810), "RAM mirror");
        assertEquals(0x42, reader.applyAsInt(0x8000), "PRG-ROM");
        assertEquals(DebugCapture.REGISTER_PLACEHOLDER_BYTE, reader.applyAsInt(0x2002), "PPUSTATUS must not be read");
    }

    @Test
    public void aReaderIsOneCopyTakenWhenAskedFor(){
        final NES nes = nes();
        runLoops(nes, 3);
        final NesRoxUiTarget target = target(nes);
        final IntUnaryOperator earlier = target.reader();

        runLoops(nes, 2);

        assertEquals(3, earlier.applyAsInt(0x0010));
        assertEquals(5, target.reader().applyAsInt(0x0010));
    }

    @Test
    public void editsGoThroughTheNes(){
        final NesRoxUiTarget target = target(nes());

        assertTrue(target.isEditable(0x0010));
        assertFalse(target.isEditable(0x8000));
        target.poke(0x0810, 0x99);
        assertEquals(0x99, target.peek(0x0010));
        assertThrows(IllegalArgumentException.class, () -> target.poke(0x8000, 0x01));
    }

    @Test
    public void labelsBecomeWatchesAtTheirFirstByte(){
        final NesRoxUiTarget target = new NesRoxUiTarget(nes(),
                List.of(new MemoryLabel("rupees", 0x066D, 1), new MemoryLabel("score", 0x0070, 3)));

        assertEquals(List.of(new DebugTarget.Label("rupees", 0x066D), new DebugTarget.Label("score", 0x0070)),
                target.labels());
    }

    @Test
    public void cpuIsTheLiveRegisters(){
        final NES nes = nes();
        final NesRoxUiTarget target = target(nes);
        target.stepInstruction(); //INX

        final DebugTarget.CpuState cpu = target.cpu();
        final MOS6502Snapshot live = nes.cpu().liveState();

        assertEquals(new DebugTarget.CpuState(0x9001, live.a(), 1, live.y(), live.stackPointer(),
                NesRoxUiTarget.status(live)), cpu);
    }

    @Test
    public void statusPacksEachFlagIntoItsNvBdizcBitWithBit5Set(){
        assertEquals(0x20, NesRoxUiTarget.status(flags(false, false, false, false, false, false, false)));
        assertEquals(0xA0, NesRoxUiTarget.status(flags(true, false, false, false, false, false, false)));
        assertEquals(0x60, NesRoxUiTarget.status(flags(false, true, false, false, false, false, false)));
        assertEquals(0x30, NesRoxUiTarget.status(flags(false, false, true, false, false, false, false)));
        assertEquals(0x28, NesRoxUiTarget.status(flags(false, false, false, true, false, false, false)));
        assertEquals(0x24, NesRoxUiTarget.status(flags(false, false, false, false, true, false, false)));
        assertEquals(0x22, NesRoxUiTarget.status(flags(false, false, false, false, false, true, false)));
        assertEquals(0x21, NesRoxUiTarget.status(flags(false, false, false, false, false, false, true)));
        assertEquals(0xFF, NesRoxUiTarget.status(flags(true, true, true, true, true, true, true)));
    }

    private static MOS6502Snapshot flags(final boolean n, final boolean v, final boolean b, final boolean d,
                                         final boolean i, final boolean z, final boolean c){
        return new MOS6502Snapshot(0, 0, 0, 0, 0, 0, 0, 0, n, v, b, d, i, z, c, false, false);
    }

    @Test
    public void runControlDrivesTheNes(){
        final NES nes = nes();
        final NesRoxUiTarget target = target(nes);
        assertFalse(target.isPaused());

        target.pause();
        assertTrue(nes.isPaused());
        assertTrue(target.isPaused());

        target.resume();
        assertFalse(nes.isPaused());
    }

    @Test
    public void stepFrameRunsAFrame(){
        final NES nes = nes();

        target(nes).stepFrame();

        assertTrue(nes.tickRateMonitor().ticks() > 20_000, "ran (most of) a frame - the first frame starts mid-way");
    }

    @Test
    public void statusShowsInstructionsAndSpeed(){
        final NES nes = nes();
        final NesRoxUiTarget target = target(nes);
        for (int i = 0; i < 1234; i++){
            target.stepInstruction();
        }

        assertEquals("1,234 instructions  ·  0.000 MHz (0%)", target.status(),
                "no full measuring window has passed yet");
    }

    @Test
    public void statusShowsTheMeasuredSpeedInMhzAndAsAShareOfTheRealNes(){
        final AtomicLong now = new AtomicLong();
        final TickRateMonitor monitor = new TickRateMonitor(now::get, 1_000_000_000L);
        for (int i = 0; i < 894_887; i++){ //half of NES.intendedCpuHz(), over one second
            monitor.tick();
        }
        now.set(1_000_000_000L);
        final NES nes = mock(NES.class);
        when(nes.tickRateMonitor()).thenReturn(monitor);
        when(nes.instructionTrace()).thenReturn(new InstructionTrace(() -> true, () -> 0, address -> 0));

        assertEquals("0 instructions  ·  0.895 MHz (50%)", target(nes).status());
    }
}
