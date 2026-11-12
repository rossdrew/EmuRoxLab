package com.rox.debug;

/**
 * Where the PPU is drawing: scanline 0-261 (0-239 visible, 241 starts vblank, 261 is the pre-render line) and dot
 * 0-340 along it.
 */
public record BeamPosition(int scanline, int dot) {
    /** For a hit recorded without a PPU to ask. */
    public static final BeamPosition UNKNOWN = new BeamPosition(-1, -1);

    public boolean isKnown(){
        return !equals(UNKNOWN);
    }
}
