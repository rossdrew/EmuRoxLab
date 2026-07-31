package com.rox.ppu.debug;

/**
 * Decodes a raw 16-byte NES CHR tile into an 8x8 grid of 2-bit pixel values (0-3): bytes 0-7 are
 * bit-plane 0 (one byte per row), bytes 8-15 are bit-plane 1 - column 0 is each row byte's MSB, not
 * its LSB. Pure decode logic only, no colour - callers (e.g. the debug viewer) decide how a 0-3 value
 * maps to a displayed shade/colour.
 */
public final class TileDecoder {
    private static final int TILE_SIZE = 8;
    private static final int BITPLANE_SIZE = 8;

    private TileDecoder(){
    }

    /**
     * @param tileBytes the tile's raw 16 CHR bytes
     * @return an 8x8 grid, {@code pixels[row][col]}, each value 0-3
     */
    public static int[][] decode(final int[] tileBytes){
        final int[][] pixels = new int[TILE_SIZE][TILE_SIZE];
        for (int row = 0; row < TILE_SIZE; row++){
            final int lowPlaneByte = tileBytes[row];
            final int highPlaneByte = tileBytes[BITPLANE_SIZE + row];
            for (int col = 0; col < TILE_SIZE; col++){
                final int bitShift = TILE_SIZE - 1 - col;
                final int lowBit = (lowPlaneByte >> bitShift) & 1;
                final int highBit = (highPlaneByte >> bitShift) & 1;
                pixels[row][col] = lowBit | (highBit << 1);
            }
        }
        return pixels;
    }
}
