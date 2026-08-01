package com.rox.ppu.debug;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws a native-resolution {@link BufferedImage} into a panel at the largest size that both fits the
 * panel and preserves the image's aspect ratio, centered (letterboxed/pillarboxed) rather than
 * stretched. Nearest-neighbour interpolation keeps pixel-art tiles crisp at non-integer scale factors
 * instead of blurring them.
 */
final class ScaledImageDrawer {
    private ScaledImageDrawer(){
    }

    static void drawCentered(final Graphics g, final BufferedImage image, final int panelWidth, final int panelHeight){
        final int[] rect = CenteredScale.fit(image.getWidth(), image.getHeight(), panelWidth, panelHeight);

        final Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(image, rect[0], rect[1], rect[2], rect[3], null);
    }
}
