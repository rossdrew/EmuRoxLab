package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Plots all 64 OAM sprites at their real screen (X,Y) positions on a 256x240 canvas, grayscale -
 * chosen over a plain "grid of 64 tiles" layout since seeing sprites where they'll actually render is
 * more useful for sanity-checking OAM DMA/writes. Honours sprite size (8x8 vs 8x16, {@code $2000} bit
 * 5) and horizontal/vertical flip (attribute bits 6/7); doesn't offset Y by the real hardware's "OAM Y
 * is the sprite's top row minus one" quirk, close enough for a debug view. Sprite priority/palette are
 * ignored - no colour yet (a later phase).
 */
final class OamViewerPanel extends JPanel {
    private static final int SPRITE_COUNT = 64;
    private static final int BYTES_PER_SPRITE = 4;
    private static final int TILE_PX = 8;
    private static final int WIDTH_PX = 256;
    private static final int HEIGHT_PX = 240;
    private static final int PATTERN_TABLE_OFFSET = 0x1000; //tall-sprite tile-index-driven table select, not from PPUCTRL
    private static final int FLIP_HORIZONTAL_BIT = 0x40;
    private static final int FLIP_VERTICAL_BIT = 0x80;
    private static final int SCALE = 2;

    private final PPU ppu;
    private final Cartridge cartridge;

    OamViewerPanel(final PPU ppu, final Cartridge cartridge){
        this.ppu = ppu;
        this.cartridge = cartridge;
        setPreferredSize(new Dimension(WIDTH_PX * SCALE, HEIGHT_PX * SCALE));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, render(), getWidth(), getHeight());
    }

    private BufferedImage render(){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        final int[] oam = ppu.oamSnapshot();
        final boolean tallSprites = ppu.controlRegisterDecoded().tallSprites();

        //draw lowest-priority (highest OAM index) first so lower-index sprites correctly paint over
        //higher-index ones on overlap, matching real hardware's OAM-index sprite priority
        for (int sprite = SPRITE_COUNT - 1; sprite >= 0; sprite--){
            final int base = sprite * BYTES_PER_SPRITE;
            final int y = oam[base];
            final int tileIndex = oam[base + 1];
            final int attributes = oam[base + 2];
            final int x = oam[base + 3];
            final boolean flipH = (attributes & FLIP_HORIZONTAL_BIT) != 0;
            final boolean flipV = (attributes & FLIP_VERTICAL_BIT) != 0;

            if (!tallSprites){
                final int patternTable = ppu.controlRegisterDecoded().spritePatternTableBase();
                drawTile(image, x, y, patternTable, tileIndex, flipH, flipV);
            } else {
                final int patternTable = (tileIndex & 0x01) != 0 ? PATTERN_TABLE_OFFSET : 0;
                final int topTile = tileIndex & 0xFE;
                final int bottomTile = topTile | 0x01;
                final int firstTile = flipV ? bottomTile : topTile;
                final int secondTile = flipV ? topTile : bottomTile;
                drawTile(image, x, y, patternTable, firstTile, flipH, flipV);
                drawTile(image, x, y + TILE_PX, patternTable, secondTile, flipH, flipV);
            }
        }
        return image;
    }

    private void drawTile(final PixelGridBufferedImage image,
                          final int originX,
                          final int originY,
                          final int patternTable,
                          final int tileNumber,
                          final boolean flipH,
                          final boolean flipV){
        final int[][] pixels = TileDecoder.decode(cartridge, patternTable, tileNumber);
        image.drawTile(pixels, originX, originY, flipH, flipV, true);
    }
}
