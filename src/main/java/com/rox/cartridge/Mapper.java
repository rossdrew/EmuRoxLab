package com.rox.cartridge;

import com.rox.mem.MemoryBus;

/**
 * A cartridge board's banking strategy: the CPU-visible {@code $6000-$FFFF} window (PRG-RAM and
 * PRG-ROM, however the board maps/switches them) via {@link MemoryBus#read}/{@link MemoryBus#write},
 * plus the PPU-visible CHR pattern table window ({@code $0000-$1FFF}, CHR-ROM or CHR-RAM depending on
 * the board) and the board's current nametable mirroring mode. One implementation per iNES mapper
 * number - {@link NromMapper} for mapper 0, {@link Mmc1Mapper} for mapper 1.
 */
public interface Mapper extends MemoryBus {
    /** Read a byte from the PPU's CHR pattern table space, address {@code $0000-$1FFF}. */
    int readChr(int address);

    /** Write a byte to the PPU's CHR pattern table space, address {@code $0000-$1FFF} - a no-op on CHR-ROM boards. */
    void writeChr(int address, int value);

    /** How this board's 2KB of nametable RAM is currently aliased across the PPU's 4 logical nametables. */
    Mirroring nametableMirroring();

    /** The board's 8KB of PRG-RAM ({@code $6000-$7FFF}), 0-255 per element - a defensive copy, not a live view. */
    int[] prgRam();

    /** Replaces the board's PRG-RAM wholesale, e.g. when restoring a battery-backed save. {@code prgRam.length} must match {@link #prgRam()}'s. */
    void restorePrgRam(int[] prgRam);
}
