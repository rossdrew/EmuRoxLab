package com.rox.ppu.debug;

import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Renders the PPU's actual rendered background - {@link PPU#framebuffer()} - at its native 256x240
 * resolution, grayscale: each raw palette index (0-63) maps to one of 64 evenly-spaced gray shades, no
 * {@code NesPalette} colour conversion yet (a later phase). Unlike the CHR/nametable panels (which
 * decode tiles directly from CHR/nametable RAM, ignoring scroll position), this shows exactly what the
 * background rendering pipeline produced - the real scroll position, attribute palette groups and
 * per-pixel shift-register timing all already applied.
 */
final class BackgroundViewerPanel extends JPanel {
    private static final int WIDTH_PX = PPU.FRAMEBUFFER_WIDTH;
    private static final int HEIGHT_PX = PPU.FRAMEBUFFER_HEIGHT;
    private static final int PALETTE_INDEX_MAX = 63; //6-bit index, 0-63
    private static final int GRAY_SCALE = 255;
    private static final int SCALE = 2;

    private final PPU ppu;
    //reused across repaints rather than allocated fresh each time - at a 16ms refresh timer, allocating
    //a new image and making 61,440 individual setRGB calls on the EDT every tick is wasteful when the
    //bulk overload can convert the whole frame in one native call instead
    private final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
    private final int[] rgbPixels = new int[WIDTH_PX * HEIGHT_PX];

    BackgroundViewerPanel(final PPU ppu){
        this.ppu = ppu;
        setPreferredSize(new Dimension(WIDTH_PX * SCALE, HEIGHT_PX * SCALE));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        updateImage();
        ScaledImageDrawer.drawCentered(g, image, getWidth(), getHeight());
    }

    private void updateImage(){
        final int[] framebuffer = ppu.framebuffer();
        for (int i = 0; i < rgbPixels.length; i++){
            final int gray = framebuffer[i] * GRAY_SCALE / PALETTE_INDEX_MAX;
            rgbPixels[i] = (gray << 16) | (gray << 8) | gray;
        }
        image.setRGB(0, 0, WIDTH_PX, HEIGHT_PX, rgbPixels, 0, WIDTH_PX);
    }
}
