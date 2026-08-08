package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.ppu.NesPalette;
import com.rox.ppu.PPU;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * Renders both CHR pattern tables ({@code $0000-$0FFF} and {@code $1000-$1FFF}) as 16x16 tile grids
 * (128x128 raw px each), in real colour. Unlike the nametable/OAM panels, a raw CHR tile carries no
 * palette group of its own - it's just 2-bit pixel data reused by whichever tile/sprite happens to
 * reference it - so a dropdown lets the viewer pick which of the 8 palettes (4 background + 4 sprite,
 * read live from palette RAM through {@code NesPalette}) to preview tiles through, defaulting to
 * background palette 0.
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
    private static final int COLORS_PER_PALETTE = 4;
    private static final String[] PALETTE_NAMES = {
            "Background 0", "Background 1", "Background 2", "Background 3",
            "Sprite 0", "Sprite 1", "Sprite 2", "Sprite 3",
    };

    private final PPU ppu;
    private final Cartridge cartridge;
    private final JComboBox<String> paletteSelector = new JComboBox<>(PALETTE_NAMES);
    private final JPanel canvas = new JPanel(){
        @Override
        protected void paintComponent(final Graphics g){
            super.paintComponent(g);
            ScaledImageDrawer.drawCentered(g, renderComposite(), getWidth(), getHeight());
        }
    };

    ChrViewerPanel(final PPU ppu, final Cartridge cartridge){
        this.ppu = ppu;
        this.cartridge = cartridge;
        canvas.setPreferredSize(new Dimension(TABLE_PX * SCALE * 2 + PADDING * 3, TABLE_PX * SCALE + PADDING * 2));
        setLayout(new BorderLayout());
        add(paletteSelector, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        paletteSelector.addActionListener(e -> canvas.repaint());
    }

    /** This palette's 4 colours (background palettes 0-3 then sprite palettes 0-3), resolved through {@code NesPalette}. */
    private int[] selectedColors(){
        final int selection = paletteSelector.getSelectedIndex();
        final int base = Math.max(selection, 0) * COLORS_PER_PALETTE;
        final int[] paletteSnapshot = ppu.paletteSnapshot();
        final int[] colors = new int[COLORS_PER_PALETTE];
        for (int i = 0; i < COLORS_PER_PALETTE; i++){
            colors[i] = NesPalette.rgb(paletteSnapshot[base + i]);
        }
        return colors;
    }

    /** Both tables plus their gap, pre-composited at native (1x) resolution so the whole thing scales as one image. */
    private BufferedImage renderComposite(){
        final int[] colors = selectedColors();
        final BufferedImage composite = new BufferedImage(COMPOSITE_WIDTH, COMPOSITE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        final Graphics compositeGraphics = composite.getGraphics();
        compositeGraphics.drawImage(renderTable(0, colors), PADDING, PADDING, null);
        compositeGraphics.drawImage(renderTable(PATTERN_TABLE_SIZE, colors), PADDING * 2 + TABLE_PX, PADDING, null);
        return composite;
    }

    private BufferedImage renderTable(final int tableBase, final int[] colors){
        final PixelGridBufferedImage image = new PixelGridBufferedImage(TABLE_PX, TABLE_PX, BufferedImage.TYPE_INT_RGB);
        final int tileCount = TILES_PER_ROW * TILES_PER_ROW;

        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++){
            final int tileBase = tableBase + tileIndex * TILE_BYTES;

            final int[][] pixels = TileDecoder.decode(cartridge, tileBase);

            final int originX = (tileIndex % TILES_PER_ROW) * TILE_PX;
            final int originY = (tileIndex / TILES_PER_ROW) * TILE_PX;

            image.drawTile(pixels, originX, originY, false, false, false, colors);
        }
        return image;
    }
}
