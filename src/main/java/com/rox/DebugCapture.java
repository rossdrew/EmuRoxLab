package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cpu.mos6502.assembler.Disassembler;
import com.rox.debug.report.DebugReport;
import com.rox.mem.NESMemoryBus;
import com.rox.save.SystemSnapshot;

import java.time.LocalDateTime;
import java.util.function.IntUnaryOperator;

/**
 * Builds a {@link DebugReport} from a paused {@link NES} - the {@code NES}-touching glue for a flagged
 * issue, kept in this package (like {@code PpuDebugViewerDemo}) so it can use {@code NES}'s
 * package-private component accessors without widening them.
 */
final class DebugCapture {
    static final int UPCOMING_INSTRUCTION_COUNT = 10;
    /**
     * Stands in for any {@code $2000-$4017} byte when disassembling: reading those registers for real
     * has side effects (clearing vblank, advancing {@code $2007}'s buffer, shifting controller bits).
     * {@code $FF} isn't a modeled opcode, so code there shows as {@code ??? ($FF)} rather than
     * plausible-looking instructions.
     */
    static final int REGISTER_PLACEHOLDER_BYTE = 0xFF;
    /** What {@link NESMemoryBus} itself returns for the unmapped {@code $4018-$5FFF}. */
    static final int UNMAPPED_BYTE = 0;

    private DebugCapture(){
    }

    /**
     * @param lastFrame the last complete frame shown, or {@code null} if none yet
     * @throws IllegalStateException if {@code nes} is running and not paused
     */
    static DebugReport capture(final NES nes, final String romName, final int[] lastFrame, final LocalDateTime capturedAt){
        final SystemSnapshot snapshot = nes.captureSnapshot();
        final IntUnaryOperator peek = sideEffectFreePeek(snapshot.ram(), nes.cartridge());
        return new DebugReport(capturedAt, romName, "", lastFrame, nes.ppu().rgbFramebuffer(), snapshot,
                nes.cartridge().debugState(),
                Disassembler.disassembleForward(peek, snapshot.cpu().pc(), UPCOMING_INSTRUCTION_COUNT));
    }

    /**
     * Mirrors {@link NESMemoryBus}'s routing - including RAM mirroring and the unmapped
     * {@code $4018-$5FFF} - minus the registers whose reads have side effects.
     */
    static IntUnaryOperator sideEffectFreePeek(final int[] ram, final Cartridge cartridge){
        return address -> {
            if (address <= NESMemoryBus.CPU_RAM_END_ADDRESS){
                return ram[address & NESMemoryBus.CPU_RAM_MIRROR_MASK];
            }
            if (address <= NESMemoryBus.IO_END_ADDRESS){
                return REGISTER_PLACEHOLDER_BYTE;
            }
            if (address >= NESMemoryBus.CARTRIDGE_START_ADDRESS){
                return cartridge.read(address); //PRG-RAM/PRG-ROM reads have no side effects on any mapper
            }
            return UNMAPPED_BYTE;
        };
    }
}
