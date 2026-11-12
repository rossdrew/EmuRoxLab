package com.rox.video;

import com.rox.debug.BeamPosition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeamMarkerTest {
    @ParameterizedTest(name = "scanline {0}, dot {1} is pixel ({2}, {3})")
    @CsvSource({"0, 1, 0, 0", "33, 120, 119, 33", "239, 256, 255, 239", "100, 1, 0, 100"})
    void onAVisibleLineAVisibleDotIsAPixel(final int scanline, final int dot, final int x, final int y){
        final BeamMarker marker = BeamMarker.of(new BeamPosition(scanline, dot));

        assertEquals(new BeamMarker(x, y, null, null), marker);
        assertTrue(marker.onPicture());
    }

    @ParameterizedTest
    @CsvSource({"33, 257", "33, 340", "33, 0"})
    void pastTheEndOfAVisibleLineIsHblankOnTheRightEdge(final int scanline, final int dot){
        assertEquals(new BeamMarker(255, scanline, BeamMarker.Edge.RIGHT, "hblank, dot " + dot),
                BeamMarker.of(new BeamPosition(scanline, dot)));
    }

    @Test
    void theLineAfterThePictureIsPostRenderOnTheBottomEdge(){
        assertEquals(new BeamMarker(99, 239, BeamMarker.Edge.BOTTOM, "post-render, dot 100"), BeamMarker.of(new BeamPosition(240, 100)));
    }

    @ParameterizedTest
    @CsvSource({"241, 1, 0", "245, 300, 255", "260, 128, 127"})
    void vblankIsOnTheBottomEdge(final int scanline, final int dot, final int x){
        final BeamMarker marker = BeamMarker.of(new BeamPosition(scanline, dot));

        assertEquals(new BeamMarker(x, 239, BeamMarker.Edge.BOTTOM, "vblank, scanline " + scanline), marker);
        assertFalse(marker.onPicture());
    }

    @Test
    void preRenderIsOnTheTopEdge(){
        assertEquals(new BeamMarker(0, 0, BeamMarker.Edge.TOP, "pre-render, dot 0"), BeamMarker.of(new BeamPosition(261, 0)));
        assertEquals(new BeamMarker(255, 0, BeamMarker.Edge.TOP, "pre-render, dot 340"), BeamMarker.of(new BeamPosition(261, 340)));
    }
}
