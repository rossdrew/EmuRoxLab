package com.rox.ppu.debug;

import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Renders the PPU's actual rendered background - {@link PPU#rgbFramebuffer()} - at its native 256x240
 * resolution, in real colour (each raw palette index resolved through {@code NesPalette}). Unlike the
 * CHR/nametable panels (which decode tiles directly from CHR/nametable RAM, ignoring scroll position),
 * this shows exactly what the background rendering pipeline produced - the real scroll position,
 * attribute palette groups and per-pixel shift-register timing all already applied.
 */
final class BackgroundViewerPanel extends JPanel {
    private static final int WIDTH_PX = PPU.FRAMEBUFFER_WIDTH;
    private static final int HEIGHT_PX = PPU.FRAMEBUFFER_HEIGHT;
    private static final int SCALE = 2;

    private final PPU ppu;
    //reused across repaints rather than allocated fresh each time - at a 16ms refresh timer, allocating
    //a new image on the EDT every tick is wasteful when the bulk setRGB overload can blit the whole
    //frame (already a fresh array from ppu.rgbFramebuffer()) in one native call instead
    private final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);

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
        image.setRGB(0, 0, WIDTH_PX, HEIGHT_PX, ppu.rgbFramebuffer(), 0, WIDTH_PX);
    }
}
