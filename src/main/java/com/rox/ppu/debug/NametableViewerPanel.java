package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.Mirroring;
import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Renders all 4 logical nametables ({@code $2000}/{@code $2400}/{@code $2800}/{@code $2C00}) as a 2x2
 * grid of 32x30 tile grids (256x240 raw px each - the screen's own resolution), grayscale, decoded
 * through whichever background pattern table {@code $2000} bit 4 currently selects. Quadrants that
 * alias the same physical 1KB bank (per the cartridge's {@link Mirroring} mode) render identical
 * content - that's correct, not a bug: on real hardware they *are* the same RAM. Attribute-table
 * palette groups are ignored entirely - no colour exists yet (a later phase), every tile is decoded as
 * raw 0-3 pixel values mapped to gray shades.
 *
 * A green box marks the 256x240 section the game currently intends to display: {@code $2000}'s base
 * nametable plus the staged ({@code t}-register) scroll position. Not yet the live rendering-time
 * scroll (coarse-X/Y increments, the dot-257/280-304 copies) - that machinery doesn't exist until the
 * background-rendering phase, so this reflects what the game last asked for via {@code $2005}/
 * {@code $2006}, not necessarily what's mid-frame on real hardware. When that intended scroll
 * straddles a nametable seam the box legitimately splits into up to 4 rectangles (see
 * {@link ScrollViewport}) - it wraps back around to the opposite edge, same as real hardware scrolling.
 */
final class NametableViewerPanel extends JPanel {
    private static final int TILE_COLUMNS = 32;
    private static final int TILE_ROWS = 30;
    private static final int TILE_PX = 8;
    private static final int TABLE_WIDTH_PX = TILE_COLUMNS * TILE_PX;
    private static final int TABLE_HEIGHT_PX = TILE_ROWS * TILE_PX;
    private static final int GRID_WIDTH_PX = TABLE_WIDTH_PX * 2;
    private static final int GRID_HEIGHT_PX = TABLE_HEIGHT_PX * 2;
    private static final int PHYSICAL_NAMETABLE_SIZE = 0x400;
    private static final int SCALE = 1;
    private static final Color VIEWPORT_COLOR = Color.GREEN;

    private final PPU ppu;
    private final Cartridge cartridge;

    NametableViewerPanel(final PPU ppu, final Cartridge cartridge){
        this.ppu = ppu;
        this.cartridge = cartridge;
        setPreferredSize(new Dimension(GRID_WIDTH_PX * SCALE, GRID_HEIGHT_PX * SCALE));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, render(), getWidth(), getHeight());
    }

    private BufferedImage render(){
        final int[] nametable = ppu.nametableSnapshot();
        final int patternTableBase = ppu.controlRegisterDecoded().backgroundPatternTableBase();

        final PixelGridBufferedImage image = new PixelGridBufferedImage(GRID_WIDTH_PX, GRID_HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        for (int logicalTable = 0; logicalTable < 4; logicalTable++){
            drawTable(image, nametable, patternTableBase, logicalTable);
        }
        drawViewportBox(image);
        return image;
    }

    private void drawTable(final PixelGridBufferedImage image,
                           final int[] nametable,
                           final int patternTableBase,
                           final int logicalTable){
        final int physicalTableOffset = ppu.resolvePhysicalNametable(logicalTable) * PHYSICAL_NAMETABLE_SIZE;
        final int quadrantOriginX = (logicalTable & 0x01) * TABLE_WIDTH_PX;
        final int quadrantOriginY = ((logicalTable >> 1) & 0x01) * TABLE_HEIGHT_PX;

        for (int row = 0; row < TILE_ROWS; row++){
            for (int col = 0; col < TILE_COLUMNS; col++){
                final int tileIndex = nametable[physicalTableOffset + row * TILE_COLUMNS + col];
                final int[][] pixels = TileDecoder.decode(cartridge, patternTableBase, tileIndex);

                final int originX = quadrantOriginX + col * TILE_PX;
                final int originY = quadrantOriginY + row * TILE_PX;

                image.drawTile(pixels, originX, originY);
            }
        }
    }

    private void drawViewportBox(final BufferedImage image){
        final int logicalTable = ppu.controlRegisterDecoded().nametableSelect();
        final int originX = (logicalTable & 0x01) * TABLE_WIDTH_PX + ppu.scrollX();
        final int originY = ((logicalTable >> 1) & 0x01) * TABLE_HEIGHT_PX + ppu.scrollY();

        final Graphics2D g2 = image.createGraphics();
        g2.setColor(VIEWPORT_COLOR);
        for (final int[] segment : ScrollViewport.segments(originX, originY, TABLE_WIDTH_PX, TABLE_HEIGHT_PX,
                GRID_WIDTH_PX, GRID_HEIGHT_PX)){
            g2.drawRect(segment[0], segment[1], segment[2] - 1, segment[3] - 1);
        }
        g2.dispose();
    }
}
