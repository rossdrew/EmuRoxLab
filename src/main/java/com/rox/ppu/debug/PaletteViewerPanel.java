package com.rox.ppu.debug;

import com.rox.ppu.NesPalette;
import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * The 8 palettes ({@code $3F00-$3F1F}) as an 8-row x 4-column grid of colour swatches - rows 0-3 are
 * the background palettes, rows 4-7 the sprite palettes, each resolved live from
 * {@link PPU#paletteSnapshot()} through {@code NesPalette}. Column 0 of every row is that palette's
 * shared "colour 0" entry - real hardware's backdrop-mirror quirk means the 4 sprite rows' colour 0
 * always matches their corresponding background row's, never independently set (see
 * {@link PPU#paletteSnapshot()}'s own documentation).
 */
final class PaletteViewerPanel extends JPanel {
    private static final int PALETTES = 8;
    private static final int COLORS_PER_PALETTE = 4;
    private static final int SWATCH_PX = 16;
    private static final int GRID_WIDTH_PX = COLORS_PER_PALETTE * SWATCH_PX;
    private static final int GRID_HEIGHT_PX = PALETTES * SWATCH_PX;
    private static final int SCALE = 3;

    private final PPU ppu;

    PaletteViewerPanel(final PPU ppu){
        this.ppu = ppu;
        setPreferredSize(new Dimension(GRID_WIDTH_PX * SCALE, GRID_HEIGHT_PX * SCALE));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, render(), getWidth(), getHeight());
    }

    private BufferedImage render(){
        final int[] paletteSnapshot = ppu.paletteSnapshot();
        final BufferedImage image = new BufferedImage(GRID_WIDTH_PX, GRID_HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        final Graphics g2 = image.getGraphics();
        for (int palette = 0; palette < PALETTES; palette++){
            for (int color = 0; color < COLORS_PER_PALETTE; color++){
                final int rgb = NesPalette.rgb(paletteSnapshot[palette * COLORS_PER_PALETTE + color]);
                g2.setColor(new Color(rgb));
                g2.fillRect(color * SWATCH_PX, palette * SWATCH_PX, SWATCH_PX, SWATCH_PX);
            }
        }
        g2.dispose();
        return image;
    }
}
