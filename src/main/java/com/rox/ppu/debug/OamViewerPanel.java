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
    private static final int SPRITE_SIZE_BIT = 0x20;
    private static final int SPRITE_PATTERN_TABLE_BIT = 0x08;
    private static final int PATTERN_TABLE_OFFSET = 0x1000;
    private static final int FLIP_HORIZONTAL_BIT = 0x40;
    private static final int FLIP_VERTICAL_BIT = 0x80;
    private static final int SCALE = 2;
    private static final int GRAY_STEP = 85;

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
        final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        final int[] oam = ppu.oamSnapshot();
        final boolean tallSprites = (ppu.controlRegister() & SPRITE_SIZE_BIT) != 0;

        for (int sprite = 0; sprite < SPRITE_COUNT; sprite++){
            final int base = sprite * BYTES_PER_SPRITE;
            final int y = oam[base];
            final int tileIndex = oam[base + 1];
            final int attributes = oam[base + 2];
            final int x = oam[base + 3];
            final boolean flipH = (attributes & FLIP_HORIZONTAL_BIT) != 0;
            final boolean flipV = (attributes & FLIP_VERTICAL_BIT) != 0;

            if (!tallSprites){
                final int patternTable = (ppu.controlRegister() & SPRITE_PATTERN_TABLE_BIT) != 0
                        ? PATTERN_TABLE_OFFSET : 0;
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

    private void drawTile(final BufferedImage image,
                          final int originX,
                          final int originY,
                          final int patternTable,
                          final int tileNumber,
                          final boolean flipH,
                          final boolean flipV){
        final int[][] pixels = TileDecoder.decode(cartridge, patternTable, tileNumber);

        for (int row = 0; row < TILE_PX; row++){
            for (int col = 0; col < TILE_PX; col++){
                final int px = originX + (flipH ? TILE_PX - 1 - col : col);
                final int py = originY + (flipV ? TILE_PX - 1 - row : row);
                if (px < 0 || px >= WIDTH_PX || py < 0 || py >= HEIGHT_PX){
                    continue; //sprite partially/fully off-canvas - a debug view, just clip it
                }
                final int gray = pixels[row][col] * GRAY_STEP;
                image.setRGB(px, py, (gray << 16) | (gray << 8) | gray);
            }
        }
    }
}
