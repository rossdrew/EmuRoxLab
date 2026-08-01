package com.rox.ppu.debug;

/**
 * Computes where a source image should be drawn to fit centered within a panel, preserving aspect
 * ratio (letterboxed/pillarboxed rather than stretched) - the pure geometry {@link ScaledImageDrawer}
 * needs before it hands off to {@code Graphics2D}.
 */
final class CenteredScale {
    private CenteredScale(){
    }

    /** @return {@code [x, y, width, height]} - the destination rectangle within the panel. */
    static int[] fit(final int imageWidth, final int imageHeight, final int panelWidth, final int panelHeight){
        final double scale = Math.min(panelWidth / (double) imageWidth, panelHeight / (double) imageHeight);
        final int drawWidth = (int) (imageWidth * scale);
        final int drawHeight = (int) (imageHeight * scale);
        final int x = (panelWidth - drawWidth) / 2;
        final int y = (panelHeight - drawHeight) / 2;
        return new int[]{x, y, drawWidth, drawHeight};
    }
}
