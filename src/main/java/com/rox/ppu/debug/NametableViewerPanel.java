package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.Mirroring;
import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Renders the physical nametable RAM as a 32x30 tile grid (256x240 raw px - the screen's own
 * resolution), grayscale, decoded through whichever background pattern table {@code $2000} bit 4
 * currently selects. Attribute-table palette groups are ignored entirely - no colour exists yet
 * (a later phase), every tile is decoded as raw 0-3 pixel values mapped to gray shades.
 */
final class NametableViewerPanel extends JPanel {
    private static final int TILE_COLUMNS = 32;
    private static final int TILE_ROWS = 30;
    private static final int TILE_PX = 8;
    private static final int WIDTH_PX = TILE_COLUMNS * TILE_PX;
    private static final int HEIGHT_PX = TILE_ROWS * TILE_PX;
    private static final int TILE_BYTES = 16;
    private static final int BACKGROUND_PATTERN_TABLE_BIT = 0x10;
    private static final int BACKGROUND_PATTERN_TABLE_OFFSET = 0x1000;
    private static final int NAMETABLE_SELECT_MASK = 0x03;
    private static final int PHYSICAL_NAMETABLE_SIZE = 0x400;
    private static final int SCALE = 2;
    private static final int GRAY_STEP = 85;

    private final PPU ppu;
    private final Cartridge cartridge;

    NametableViewerPanel(final PPU ppu, final Cartridge cartridge){
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
        final int[] nametable = ppu.nametableSnapshot();
        final int patternTableBase = (ppu.controlRegister() & BACKGROUND_PATTERN_TABLE_BIT) != 0
                ? BACKGROUND_PATTERN_TABLE_OFFSET : 0;
        //show whichever logical nametable $2000's NN bits currently select, resolved through the same
        //mirroring mode PPU itself uses - the physical 2KB snapshot isn't just "table 0" laid out flat
        final int physicalTableOffset = resolveCurrentPhysicalNametable() * PHYSICAL_NAMETABLE_SIZE;

        final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < TILE_ROWS; row++){
            for (int col = 0; col < TILE_COLUMNS; col++){
                final int tileIndex = nametable[physicalTableOffset + row * TILE_COLUMNS + col];
                final int tileBase = patternTableBase + tileIndex * TILE_BYTES;
                final int[] tileBytes = new int[TILE_BYTES];
                for (int i = 0; i < TILE_BYTES; i++){
                    tileBytes[i] = cartridge.readChr(tileBase + i);
                }
                final int[][] pixels = TileDecoder.decode(tileBytes);

                final int originX = col * TILE_PX;
                final int originY = row * TILE_PX;
                for (int pixelRow = 0; pixelRow < TILE_PX; pixelRow++){
                    for (int pixelCol = 0; pixelCol < TILE_PX; pixelCol++){
                        final int gray = pixels[pixelRow][pixelCol] * GRAY_STEP;
                        image.setRGB(originX + pixelCol, originY + pixelRow, (gray << 16) | (gray << 8) | gray);
                    }
                }
            }
        }
        return image;
    }

    /** Mirrors PPU's own (private) logical-to-physical nametable resolution for the selected NN bits. */
    private int resolveCurrentPhysicalNametable(){
        final int logicalTable = ppu.controlRegister() & NAMETABLE_SELECT_MASK;
        return switch (cartridge.nametableMirroring()){
            case Mirroring.HORIZONTAL -> logicalTable >> 1;
            case Mirroring.VERTICAL -> logicalTable & 0x01;
            case Mirroring.SINGLE_SCREEN_LOWER -> 0;
            case Mirroring.SINGLE_SCREEN_UPPER -> 1;
        };
    }
}
