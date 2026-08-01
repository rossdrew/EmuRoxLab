package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Renders both CHR pattern tables ({@code $0000-$0FFF} and {@code $1000-$1FFF}) as 16x16 tile grids
 * (128x128 raw px each), grayscale - no {@code NesPalette} exists yet (a later phase), so a tile's
 * raw 2-bit pixel value (0-3) is mapped straight to one of 4 evenly-spaced gray shades.
 */
final class ChrViewerPanel extends JPanel {
    private static final int TILES_PER_ROW = 16;
    private static final int TILE_PX = 8;
    private static final int TABLE_PX = TILES_PER_ROW * TILE_PX;
    private static final int TILE_BYTES = 16;
    private static final int PATTERN_TABLE_SIZE = 0x1000;
    private static final int SCALE = 3;
    private static final int PADDING = 4;
    private static final int COMPOSITE_WIDTH = TABLE_PX * 2 + PADDING * 3;
    private static final int COMPOSITE_HEIGHT = TABLE_PX + PADDING * 2;

    private final Cartridge cartridge;

    ChrViewerPanel(final Cartridge cartridge){
        this.cartridge = cartridge;
        setPreferredSize(new Dimension(TABLE_PX * SCALE * 2 + PADDING * 3, TABLE_PX * SCALE + PADDING * 2));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, renderComposite(), getWidth(), getHeight());
    }

    /** Both tables plus their gap, pre-composited at native (1x) resolution so the whole thing scales as one image. */
    private BufferedImage renderComposite(){
        final BufferedImage composite = new BufferedImage(COMPOSITE_WIDTH, COMPOSITE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        final Graphics compositeGraphics = composite.getGraphics();
        compositeGraphics.drawImage(renderTable(0), PADDING, PADDING, null);
        compositeGraphics.drawImage(renderTable(PATTERN_TABLE_SIZE), PADDING * 2 + TABLE_PX, PADDING, null);
        return composite;
    }

    private BufferedImage renderTable(final int tableBase){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(TABLE_PX, TABLE_PX, BufferedImage.TYPE_INT_RGB);
        final int tileCount = TILES_PER_ROW * TILES_PER_ROW;

        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++){
            final int tileBase = tableBase + tileIndex * TILE_BYTES;

            final int[][] pixels = TileDecoder.decode(cartridge, tileBase);

            final int originX = (tileIndex % TILES_PER_ROW) * TILE_PX;
            final int originY = (tileIndex / TILES_PER_ROW) * TILE_PX;

            image.drawTile(pixels, originX, originY);
        }
        return image;
    }
}
