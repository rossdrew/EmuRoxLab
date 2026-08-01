package com.rox.ppu.debug;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PixelGridBufferedImageTest {
    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;
    private static final int MID_GRAY = 0xAAAAAA; //value 2 * GRAY_STEP(85) = 170 = 0xAA per channel

    @Test
    public void drawTileMapsEachTwoBitValueToTheCorrespondingGrayShade(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = uniform(2);

        image.drawTile(pixels, 0, 0);

        assertEquals(MID_GRAY, image.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(MID_GRAY, image.getRGB(7, 7) & 0xFFFFFF);
    }

    @Test
    public void drawTileOffsetsIntoTheImageByFromXFromY(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(0, 0);

        image.drawTile(pixels, 4, 4);

        assertEquals(WHITE, image.getRGB(4, 4) & 0xFFFFFF, "marker should land at (fromX, fromY)");
        assertEquals(BLACK, image.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    public void drawTileWithoutFlipArgumentsBehavesLikeNoFlip(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(0, 0);

        image.drawTile(pixels, 0, 0);

        assertEquals(WHITE, image.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    public void flipHorizontalMirrorsColumnsWithinTheTile(){
        //marker off-centre (row 2, col 3): a subtraction-vs-addition bug in the flip math would only
        //show up here, not at col/row 0 where "7-0" and "7+0" happen to be indistinguishable
        final PixelGridBufferedImage image = new PixelGridBufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(2, 3);

        image.drawTile(pixels, 0, 0, true, false);

        assertEquals(WHITE, image.getRGB(4, 2) & 0xFFFFFF, "col 3 should land at column 7-3=4, row unchanged");
        assertEquals(BLACK, image.getRGB(3, 2) & 0xFFFFFF, "unflipped column position should be untouched");
    }

    @Test
    public void flipVerticalMirrorsRowsWithinTheTile(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(2, 3);

        image.drawTile(pixels, 0, 0, false, true);

        assertEquals(WHITE, image.getRGB(3, 5) & 0xFFFFFF, "row 2 should land at row 7-2=5, col unchanged");
        assertEquals(BLACK, image.getRGB(3, 2) & 0xFFFFFF, "unflipped row position should be untouched");
    }

    @Test
    public void flippingBothAxesMirrorsRowsAndColumns(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(2, 3);

        image.drawTile(pixels, 0, 0, true, true);

        assertEquals(WHITE, image.getRGB(4, 5) & 0xFFFFFF);
        assertEquals(BLACK, image.getRGB(3, 2) & 0xFFFFFF);
    }

    @Test
    public void pixelsPastTheImagesFarEdgeAreClippedWithoutThrowing(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = uniform(1);

        assertDoesNotThrow(() -> image.drawTile(pixels, 0, 0));

        assertEquals(0x555555, image.getRGB(3, 3) & 0xFFFFFF, "in-bounds corner should still be drawn");
    }

    @Test
    public void pixelsBeforeTheImagesNearEdgeAreClippedWithoutThrowing(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        final int[][] pixels = markerAt(4, 4); //bottom-right corner of the source tile

        assertDoesNotThrow(() -> image.drawTile(pixels, -4, -4));

        assertEquals(WHITE, image.getRGB(0, 0) & 0xFFFFFF, "source (4,4) should land in-bounds at (0,0)");
    }

    /** An 8x8 grid, every pixel set to {@code value}. */
    private static int[][] uniform(final int value){
        final int[][] pixels = new int[8][8];
        for (final int[] row : pixels){
            java.util.Arrays.fill(row, value);
        }
        return pixels;
    }

    /** An 8x8 grid, all zero except a single 3 (white) at {@code [markerRow][markerCol]}. */
    private static int[][] markerAt(final int markerRow, final int markerCol){
        final int[][] pixels = new int[8][8];
        pixels[markerRow][markerCol] = 3;
        return pixels;
    }
}
