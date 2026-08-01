package com.rox.ppu.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScrollViewportTest {

    @Test
    public void noWrapWhenBoxFitsEntirelyWithinCanvas(){
        final int[][] segments = ScrollViewport.segments(10, 20, 256, 240, 512, 480);

        assertEquals(1, segments.length);
        assertArrayEquals(new int[]{10, 20, 256, 240}, segments[0]);
    }

    @Test
    public void exactFitAtFarEdgeDoesNotWrap(){
        final int[][] segments = ScrollViewport.segments(256, 240, 256, 240, 512, 480);

        assertEquals(1, segments.length);
        assertArrayEquals(new int[]{256, 240, 256, 240}, segments[0]);
    }

    @Test
    public void originAtZeroDoesNotWrap(){
        final int[][] segments = ScrollViewport.segments(0, 0, 256, 240, 512, 480);

        assertEquals(1, segments.length);
        assertArrayEquals(new int[]{0, 0, 256, 240}, segments[0]);
    }

    @Test
    public void horizontalOverflowSplitsIntoTwoSegments(){
        final int[][] segments = ScrollViewport.segments(400, 20, 256, 240, 512, 480);

        assertEquals(2, segments.length);
        assertArrayEquals(new int[]{400, 20, 112, 240}, segments[0]);
        assertArrayEquals(new int[]{0, 20, 144, 240}, segments[1]);
    }

    @Test
    public void verticalOverflowSplitsIntoTwoSegments(){
        final int[][] segments = ScrollViewport.segments(10, 300, 256, 240, 512, 480);

        assertEquals(2, segments.length);
        assertArrayEquals(new int[]{10, 300, 256, 180}, segments[0]);
        assertArrayEquals(new int[]{10, 0, 256, 60}, segments[1]);
    }

    @Test
    public void overflowingBothAxesSplitsIntoFourSegments(){
        final int[][] segments = ScrollViewport.segments(400, 300, 256, 240, 512, 480);

        assertEquals(4, segments.length);
        assertArrayEquals(new int[]{400, 300, 112, 180}, segments[0]);
        assertArrayEquals(new int[]{400, 0, 112, 60}, segments[1]);
        assertArrayEquals(new int[]{0, 300, 144, 180}, segments[2]);
        assertArrayEquals(new int[]{0, 0, 144, 60}, segments[3]);
    }

    @Test
    public void negativeOriginIsFloorModdedIntoCanvasBounds(){
        final int[][] segments = ScrollViewport.segments(-412, -430, 256, 240, 512, 480);

        assertEquals(1, segments.length);
        assertArrayEquals(new int[]{100, 50, 256, 240}, segments[0]);
    }

    @Test
    public void singleScreenSizedCanvasWithZeroOriginFillsWholeCanvas(){
        final int[][] segments = ScrollViewport.segments(0, 0, 256, 240, 256, 240);

        assertEquals(1, segments.length);
        assertArrayEquals(new int[]{0, 0, 256, 240}, segments[0]);
    }
}
