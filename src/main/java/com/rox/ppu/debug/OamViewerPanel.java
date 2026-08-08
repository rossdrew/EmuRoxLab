package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.ppu.NesPalette;
import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Plots all 64 OAM sprites at their real screen (X,Y) positions on a 256x240 canvas, in real colour -
 * chosen over a plain "grid of 64 tiles" layout since seeing sprites where they'll actually render is
 * more useful for sanity-checking OAM DMA/writes. Honours sprite size (8x8 vs 8x16, {@code $2000} bit
 * 5), horizontal/vertical flip (attribute bits 6/7) and each sprite's own palette (attribute bits 0-1,
 * resolved through {@code NesPalette}); doesn't offset Y by the real hardware's "OAM Y is the sprite's
 * top row minus one" quirk, close enough for a debug view. Sprite priority (attribute bit 5, background
 * over/under sprite) is ignored - every sprite is drawn regardless.
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
    private static final int PALETTE_INDEX_MASK = 0x03;
    private static final int SPRITE_PALETTE_BASE = 16; //paletteSnapshot()'s $3F10-$3F1F half
    private static final int COLORS_PER_PALETTE = 4;
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
        final int[] paletteSnapshot = ppu.paletteSnapshot();
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
            final int[] spriteColors = resolveSpriteColors(paletteSnapshot, attributes & PALETTE_INDEX_MASK);

            if (!tallSprites){
                final int patternTable = ppu.controlRegisterDecoded().spritePatternTableBase();
                drawTile(image, x, y, patternTable, tileIndex, flipH, flipV, spriteColors);
            } else {
                final int patternTable = (tileIndex & 0x01) != 0 ? PATTERN_TABLE_OFFSET : 0;
                final int topTile = tileIndex & 0xFE;
                final int bottomTile = topTile | 0x01;
                final int firstTile = flipV ? bottomTile : topTile;
                final int secondTile = flipV ? topTile : bottomTile;
                drawTile(image, x, y, patternTable, firstTile, flipH, flipV, spriteColors);
                drawTile(image, x, y + TILE_PX, patternTable, secondTile, flipH, flipV, spriteColors);
            }
        }
        return image;
    }

    /** This sprite palette's 4 colours (indices {@code SPRITE_PALETTE_BASE + palette*4 .. +3}), resolved through {@code NesPalette}. */
    private static int[] resolveSpriteColors(final int[] paletteSnapshot, final int palette){
        final int base = SPRITE_PALETTE_BASE + palette * COLORS_PER_PALETTE;
        final int[] colors = new int[COLORS_PER_PALETTE];
        for (int i = 0; i < COLORS_PER_PALETTE; i++){
            colors[i] = NesPalette.rgb(paletteSnapshot[base + i]);
        }
        return colors;
    }

    private void drawTile(final PixelGridBufferedImage image,
                          final int originX,
                          final int originY,
                          final int patternTable,
                          final int tileNumber,
                          final boolean flipH,
                          final boolean flipV,
                          final int[] spriteColors){
        final int[][] pixels = TileDecoder.decode(cartridge, patternTable, tileNumber);
        image.drawTile(pixels, originX, originY, flipH, flipV, true, spriteColors);
    }
}
