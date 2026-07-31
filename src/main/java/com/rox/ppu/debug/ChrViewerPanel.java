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
    private static final int GAP = 8;
    private static final int GRAY_STEP = 85; //0/85/170/255 - 4 evenly-spaced shades for a 2-bit value

    private final Cartridge cartridge;

    ChrViewerPanel(final Cartridge cartridge){
        this.cartridge = cartridge;
        setPreferredSize(new Dimension(TABLE_PX * SCALE * 2 + GAP * 3, TABLE_PX * SCALE + GAP * 2));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        final BufferedImage left = renderTable(0);
        final BufferedImage right = renderTable(PATTERN_TABLE_SIZE);
        g.drawImage(left, GAP, GAP, TABLE_PX * SCALE, TABLE_PX * SCALE, null);
        g.drawImage(right, GAP * 2 + TABLE_PX * SCALE, GAP, TABLE_PX * SCALE, TABLE_PX * SCALE, null);
    }

    private BufferedImage renderTable(final int tableBase){
        final BufferedImage image = new BufferedImage(TABLE_PX, TABLE_PX, BufferedImage.TYPE_INT_RGB);
        for (int tileIndex = 0; tileIndex < TILES_PER_ROW * TILES_PER_ROW; tileIndex++){
            final int tileBase = tableBase + tileIndex * TILE_BYTES;
            final int[] tileBytes = new int[TILE_BYTES];
            for (int i = 0; i < TILE_BYTES; i++){
                tileBytes[i] = cartridge.readChr(tileBase + i);
            }
            final int[][] pixels = TileDecoder.decode(tileBytes);

            final int originX = (tileIndex % TILES_PER_ROW) * TILE_PX;
            final int originY = (tileIndex / TILES_PER_ROW) * TILE_PX;
            for (int row = 0; row < TILE_PX; row++){
                for (int col = 0; col < TILE_PX; col++){
                    final int gray = pixels[row][col] * GRAY_STEP;
                    image.setRGB(originX + col, originY + row, (gray << 16) | (gray << 8) | gray);
                }
            }
        }
        return image;
    }
}
