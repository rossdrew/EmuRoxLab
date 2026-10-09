package com.rox.ppu;

/**
 * A {@link PPU}'s complete state apart from its framebuffer (resuming draws at most one part-stale
 * frame, which isn't worth 60K values per save). A pause can land mid-scanline, so this includes the
 * part-way rendering pipeline too, not just what a game can see through the registers. Arrays hold
 * 0-255 per element.
 */
public record PpuSnapshot(int[] oam, int[] nametableRam, int[] paletteRam, Timing timing, Registers registers,
                          BackgroundPipeline background, SpritePipeline sprites) {

    /** Beam position plus the one-shot flags that hang off it. */
    public record Timing(int dot, int scanline, boolean vblankFlag, boolean previousNmiLine, boolean nmiEdgePending,
                         boolean frameReady, boolean oamDmaPending) {
    }

    /** CPU-visible registers and their internal latches ({@code t}/{@code v}/fine X/write toggle/read buffer). */
    public record Registers(int control, int mask, int oamAddress, boolean writeToggle, int temporaryVramAddress,
                            int currentVramAddress, int fineXScroll, int readBuffer) {
    }

    /** Background shift registers and the next tile's already-fetched bytes. */
    public record BackgroundPipeline(int patternShiftLow, int patternShiftHigh, int attributeShiftLow,
                                     int attributeShiftHigh, int nextTileId, int nextTilePaletteGroup,
                                     int nextPatternLowByte, int nextPatternHighByte) {
    }

    /** Secondary OAM, sprite flags, and the per-slot data already loaded for the current scanline. */
    public record SpritePipeline(int[] secondaryOam, int secondaryOamCount, int secondaryOamSpriteZeroSlot,
                                 boolean spriteOverflow, boolean spriteZeroHitFlag, int[] patternLowBytes,
                                 int[] patternHighBytes, int[] attributes, int[] xPositions, boolean[] isSpriteZero,
                                 int activeSpriteCount) {
    }
}
