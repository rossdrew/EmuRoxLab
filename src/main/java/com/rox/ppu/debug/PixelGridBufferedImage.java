package com.rox.ppu.debug;

import java.awt.image.BufferedImage;

/**
 * A grayscale {@link BufferedImage} with an 8x8-tile blit helper: takes a {@link TileDecoder}-shaped
 * pixel grid (values 0-3) and paints it as one of 4 evenly-spaced gray shades. Optionally flips the
 * source horizontally/vertically (for OAM sprite attribute bits) and always clips against the image's
 * own bounds - callers don't need to know/pass the canvas size themselves.
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
        for (int row = 0; row < TILE_PX; row++){
            for (int col = 0; col < TILE_PX; col++){
                final int px = fromX + (flipH ? TILE_PX - 1 - col : col);
                final int py = fromY + (flipV ? TILE_PX - 1 - row : row);
                if (px < 0 || px >= getWidth() || py < 0 || py >= getHeight()){
                    continue; //partially/fully off-canvas - a debug view, just clip it
                }
                final int gray = pixels[row][col] * GRAY_STEP;
                setRGB(px, py, (gray << 16) | (gray << 8) | gray);
            }
        }
    }
}
