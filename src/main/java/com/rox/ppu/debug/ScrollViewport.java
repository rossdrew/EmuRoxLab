package com.rox.ppu.debug;

/**
 * Computes the 1-4 rectangle segments a fixed-size viewport box splits into when its origin plus size
 * overflows a periodic canvas. The NES nametable grid wraps at its edges - scrolling past the
 * right/bottom seam continues seamlessly back at x=0/y=0 - so a viewport straddling that seam is
 * genuinely two (or, at a corner, four) separate on-screen regions, not one contiguous rectangle.
 *
 * Assumes {@code boxWidth <= canvasWidth} and {@code boxHeight <= canvasHeight} (true for a 256x240
 * viewport over a 256/512-wide, 240/480-tall nametable grid), so each axis wraps at most once.
 */
final class ScrollViewport {
    private ScrollViewport(){
    }

    /**
     * @return between 1 and 4 {@code [x, y, width, height]} rectangles, in canvas coordinates, whose
     *         union is the box wrapped onto a {@code canvasWidth x canvasHeight} periodic canvas.
     */
    static int[][] segments(final int originX, final int originY, final int boxWidth, final int boxHeight,
                             final int canvasWidth, final int canvasHeight){
        final int[][] xSpans = axisSpans(Math.floorMod(originX, canvasWidth), boxWidth, canvasWidth);
        final int[][] ySpans = axisSpans(Math.floorMod(originY, canvasHeight), boxHeight, canvasHeight);

        final int[][] segments = new int[xSpans.length * ySpans.length][4];
        int i = 0;
        for (final int[] xSpan : xSpans){
            for (final int[] ySpan : ySpans){
                segments[i++] = new int[]{xSpan[0], ySpan[0], xSpan[1], ySpan[1]};
            }
        }
        return segments;
    }

    /** @return {@code [[start, length]]}, or {@code [[start, firstLength], [0, remainder]]} if it wraps. */
    private static int[][] axisSpans(final int start, final int length, final int canvasLength){
        final int firstLength = Math.min(length, canvasLength - start);
        if (firstLength >= length){
            return new int[][]{{start, length}};
        }
        return new int[][]{{start, firstLength}, {0, length - firstLength}};
    }
}
