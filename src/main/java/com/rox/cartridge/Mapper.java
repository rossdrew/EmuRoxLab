package com.rox.cartridge;

import com.rox.mem.MemoryBus;

import java.util.Map;

/**
 * A cartridge board's banking strategy: the CPU-visible {@code $6000-$FFFF} window (PRG-RAM and
 * PRG-ROM, however the board maps/switches them) via {@link MemoryBus#read}/{@link MemoryBus#write},
 * plus the PPU-visible CHR pattern table window ({@code $0000-$1FFF}, CHR-ROM or CHR-RAM depending on
 * the board) and the board's current nametable mirroring mode. One implementation per iNES mapper
 * number - {@link NromMapper} for mapper 0, {@link Mmc1Mapper} for mapper 1, {@link Mmc3Mapper} for
 * mapper 4.
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

    /** A copy of this board's complete mutable state - see {@link MapperSnapshot}. */
    MapperSnapshot snapshot();

    /**
     * Checks {@code snapshot} could be {@link #restore}d into this board, without changing anything - so
     * a caller restoring several components can check them all before applying any.
     *
     * @throws IllegalArgumentException if {@code snapshot} is from a different kind of board, or one with
     * a different RAM layout
     */
    void checkRestorable(MapperSnapshot snapshot);

    /**
     * Puts this board back exactly as {@code snapshot} captured it - all or nothing: a snapshot that
     * fails {@link #checkRestorable} is rejected before anything changes.
     *
     * @throws IllegalArgumentException if {@code snapshot} is from a different kind of board, or one with
     * a different RAM layout
     */
    void restore(MapperSnapshot snapshot);

    /**
     * Whether this board is currently asserting an IRQ onto the CPU's IRQ line - a no-op {@code false}
     * default, since most boards (NROM, MMC1) have no IRQ capability of their own; {@link Mmc3Mapper}
     * is the one implementation that overrides this.
     */
    default boolean isIrqAsserted(){
        return false;
    }

    /**
     * Bank/IRQ register state worth showing in a debug view, as label -&gt; formatted-value pairs in
     * display order - an empty map default, since boards with nothing to switch (NROM) have none.
     * {@link Mmc1Mapper}/{@link Mmc3Mapper} override this with their own current register values.
     */
    default Map<String, String> debugState(){
        return Map.of();
    }
}
