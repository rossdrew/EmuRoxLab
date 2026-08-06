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

    BackgroundViewerPanel(final PPU ppu){
        this.ppu = ppu;
        setPreferredSize(new Dimension(WIDTH_PX * SCALE, HEIGHT_PX * SCALE));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, render(), getWidth(), getHeight());
    }

    private BufferedImage render(){
        final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        final int[] framebuffer = ppu.framebuffer();
        for (int y = 0; y < HEIGHT_PX; y++){
            for (int x = 0; x < WIDTH_PX; x++){
                final int paletteIndex = framebuffer[y * WIDTH_PX + x];
                final int gray = paletteIndex * GRAY_SCALE / PALETTE_INDEX_MAX;
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        return image;
    }
}
