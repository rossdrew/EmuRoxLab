package com.rox.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CenteredScaleTest {

    @Test
    public void imageExactlyFillingThePanelNeedsNoScalingOrOffset(){
        final int[] rect = CenteredScale.fit(100, 50, 100, 50);

        assertArrayEquals(new int[]{0, 0, 100, 50}, rect);
    }

    @Test
    public void widerPanelThanImageAspectLettersBoxesOnTheXAxis(){
        //square image, wide panel: height-constrained, so it centers horizontally with bars on the sides
        final int[] rect = CenteredScale.fit(100, 100, 200, 100);

        assertArrayEquals(new int[]{50, 0, 100, 100}, rect);
    }

    @Test
    public void tallerPanelThanImageAspectLettersBoxesOnTheYAxis(){
        //square image, tall panel: width-constrained, so it centers vertically with bars top/bottom
        final int[] rect = CenteredScale.fit(100, 100, 100, 200);

        assertArrayEquals(new int[]{0, 50, 100, 100}, rect);
    }

    @Test
    public void panelSmallerThanImageDownscalesProportionally(){
        final int[] rect = CenteredScale.fit(200, 100, 100, 100);

        assertArrayEquals(new int[]{0, 25, 100, 50}, rect);
    }

    @Test
    public void panelLargerThanImageUpscalesProportionally(){
        final int[] rect = CenteredScale.fit(50, 50, 200, 100);

        assertArrayEquals(new int[]{50, 0, 100, 100}, rect);
    }

    @Test
    public void fractionalScaleAndOffsetsTruncateRatherThanRound(){
        //scale = 0.75 exactly (binary-exact, no floating-point surprises); height offset (100-75)/2=12.5
        //must truncate to 12, not round to 13
        final int[] rect = CenteredScale.fit(200, 100, 150, 100);

        assertArrayEquals(new int[]{0, 12, 150, 75}, rect);
    }
}
