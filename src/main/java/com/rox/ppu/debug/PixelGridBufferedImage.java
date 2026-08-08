package com.rox.ppu.debug;

import java.awt.image.BufferedImage;
import java.util.function.IntUnaryOperator;

/**
 * A {@link BufferedImage} with an 8x8-tile blit helper: takes a {@link TileDecoder}-shaped pixel grid
 * (values 0-3) and paints each pixel through a caller-supplied value-to-colour mapping - either one of
 * 4 evenly-spaced gray shades (before {@code NesPalette} existed, still used where no real palette
 * group applies) or 4 real {@code NesPalette} colours resolved from actual palette RAM. Optionally
 * flips the source horizontally/vertically (for OAM sprite attribute bits), can treat pixel value 0 as
 * transparent instead of painted (the NES sprite convention - lets whatever was drawn underneath show
 * through), and always clips against the image's own bounds - callers don't need to know/pass the
 * canvas size themselves.
 */
final class PixelGridBufferedImage extends BufferedImage {
    private static final int GRAY_STEP = 85; //0/85/170/255 - 4 evenly-spaced shades for a 2-bit value
    static final int TILE_PX = 8;

    PixelGridBufferedImage(final int width, final int height, final int imageType){
        super(width, height, imageType);
    }

    void drawTile(final int[][] pixels, final int fromX, final int fromY){
        drawTile(pixels, fromX, fromY, false, false);
    }

    void drawTile(final int[][] pixels, final int fromX, final int fromY,
                  final boolean flipH, final boolean flipV){
        drawTile(pixels, fromX, fromY, flipH, flipV, false);
    }

    /**
     * @param transparentZero when true, pixel value 0 is skipped entirely instead of painted -
     *                        the NES sprite convention, so overlapping lower-priority sprites don't
     *                        erase a higher-priority sprite already drawn underneath.
     */
    void drawTile(final int[][] pixels, final int fromX, final int fromY,
                  final boolean flipH, final boolean flipV, final boolean transparentZero){
        drawTile(pixels, fromX, fromY, flipH, flipV, transparentZero,
                value -> {
                    final int gray = value * GRAY_STEP;
                    return (gray << 16) | (gray << 8) | gray;
                });
    }

    /**
     * @param paletteColors this tile's 4 real colours (one per 2-bit pixel value 0-3), each a packed
     *                       {@code 0xRRGGBB} value already resolved from palette RAM through
     *                       {@code NesPalette} - unlike the grayscale overloads, which always map a
     *                       fixed 0-3 range, the caller picks which 4 colours a given tile actually uses.
     */
    void drawTile(final int[][] pixels, final int fromX, final int fromY,
                  final boolean flipH, final boolean flipV, final boolean transparentZero,
                  final int[] paletteColors){
        drawTile(pixels, fromX, fromY, flipH, flipV, transparentZero, value -> paletteColors[value]);
    }

    private void drawTile(final int[][] pixels, final int fromX, final int fromY,
                          final boolean flipH, final boolean flipV, final boolean transparentZero,
                          final IntUnaryOperator colorOf){
        for (int row = 0; row < TILE_PX; row++){
            for (int col = 0; col < TILE_PX; col++){
                final int value = pixels[row][col];
                if (transparentZero && value == 0){
                    continue;
                }
                final int px = fromX + (flipH ? TILE_PX - 1 - col : col);
                final int py = fromY + (flipV ? TILE_PX - 1 - row : row);
                if (px < 0 || px >= getWidth() || py < 0 || py >= getHeight()){
                    continue; //partially/fully off-canvas - a debug view, just clip it
                }
                setRGB(px, py, colorOf.applyAsInt(value));
            }
        }
    }
}
