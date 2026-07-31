package com.rox.cartridge;

/**
 * How a board's 2KB of physical nametable RAM is aliased across the PPU's 4 logical 1KB nametable
 * slots ({@code $2000}/{@code $2400}/{@code $2800}/{@code $2C00}). Four-screen (a board with its own
 * extra 2KB of nametable RAM, no aliasing at all) isn't modeled - no ROM this codebase currently loads
 * needs it.
 */
public enum Mirroring {
    HORIZONTAL,
    VERTICAL,
    SINGLE_SCREEN_LOWER,
    SINGLE_SCREEN_UPPER
}
