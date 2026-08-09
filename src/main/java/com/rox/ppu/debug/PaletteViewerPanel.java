package com.rox.ppu.debug;

import com.rox.ppu.NesPalette;
import com.rox.ppu.PPU;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * All 8 of the PPU's palettes ({@code $3F00-$3F1F}) at once, one row per palette (background palettes
 * 0-3, then sprite palettes 0-3 - the same 8 names as {@link ChrViewerPanel}'s selector), each row
 * labelled to its left and gapped from its neighbours for readability. A header row along the top
 * numbers the 4 swatch columns, aligned with the swatches beneath it. Colours are resolved live from
 * {@link PPU#paletteSnapshot()} through {@code NesPalette}. Swatch 0 of every row is that palette's
 * shared "colour 0" entry - real hardware's backdrop-mirror quirk means it's the same for all 8
 * palettes, never independently set (see {@link PPU#paletteSnapshot()}'s own documentation).
 */
final class PaletteViewerPanel extends JPanel {
    private static final int COLORS_PER_PALETTE = 4;
    private static final int SWATCH_PX = 24;
    private static final int ROW_GAP_PX = 8;
    private static final float LABEL_FONT_SIZE = 24f;
    private static final int SWATCHES_WIDTH_PX = COLORS_PER_PALETTE * SWATCH_PX;

    private final PPU ppu;
    private final int labelColumnPx;

    PaletteViewerPanel(final PPU ppu){
        this.ppu = ppu;
        this.labelColumnPx = widestLabelWidth();

        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);

        final JPanel rows = new JPanel(new GridLayout(ChrViewerPanel.PALETTE_NAMES.length, 1, 0, ROW_GAP_PX));
        for (int paletteIndex = 0; paletteIndex < ChrViewerPanel.PALETTE_NAMES.length; paletteIndex++){
            rows.add(new PaletteRow(ChrViewerPanel.PALETTE_NAMES[paletteIndex], paletteIndex));
        }
        add(rows, BorderLayout.CENTER);
    }

    /** Width (in px) of the widest palette name at {@link #LABEL_FONT_SIZE} - keeps every row's label column, and the header's spacer above them, the same width so the swatch columns line up. */
    private static int widestLabelWidth(){
        final JLabel measuring = new JLabel();
        measuring.setFont(measuring.getFont().deriveFont(LABEL_FONT_SIZE));
        int widest = 0;
        for (final String name : ChrViewerPanel.PALETTE_NAMES){
            measuring.setText(name);
            widest = Math.max(widest, measuring.getPreferredSize().width);
        }
        return widest;
    }

    /** A blank spacer matching the row labels' width, plus swatch-column numbers 0-3 above the swatches. */
    private JPanel header(){
        final JPanel spacer = new JPanel();
        spacer.setPreferredSize(new Dimension(labelColumnPx, 1));

        final JPanel numbers = new JPanel(new GridLayout(1, COLORS_PER_PALETTE));
        numbers.setPreferredSize(new Dimension(SWATCHES_WIDTH_PX, 1));
        for (int color = 0; color < COLORS_PER_PALETTE; color++){
            final JLabel number = new JLabel(String.valueOf(color), SwingConstants.CENTER);
            number.setFont(number.getFont().deriveFont(LABEL_FONT_SIZE));
            numbers.add(number);
        }

        final JPanel header = new JPanel(new BorderLayout());
        header.add(spacer, BorderLayout.WEST);
        header.add(numbers, BorderLayout.CENTER);
        return header;
    }

    private BufferedImage render(final int paletteIndex){
        final int[] paletteSnapshot = ppu.paletteSnapshot();
        final int base = paletteIndex * COLORS_PER_PALETTE;
        final BufferedImage image = new BufferedImage(SWATCHES_WIDTH_PX, SWATCH_PX, BufferedImage.TYPE_INT_RGB);
        final Graphics g2 = image.getGraphics();
        for (int color = 0; color < COLORS_PER_PALETTE; color++){
            final int rgb = NesPalette.rgb(paletteSnapshot[base + color]);
            g2.setColor(new Color(rgb));
            g2.fillRect(color * SWATCH_PX, 0, SWATCH_PX, SWATCH_PX);
        }
        g2.dispose();
        return image;
    }

    /** One palette's name label plus its 4-swatch strip, side by side. */
    private final class PaletteRow extends JPanel {
        PaletteRow(final String name, final int paletteIndex){
            final JLabel label = new JLabel(name);
            label.setFont(label.getFont().deriveFont(LABEL_FONT_SIZE));
            label.setPreferredSize(new Dimension(labelColumnPx, label.getPreferredSize().height));

            final JPanel swatches = new JPanel(){
                @Override
                protected void paintComponent(final Graphics g){
                    super.paintComponent(g);
                    ScaledImageDrawer.drawCentered(g, render(paletteIndex), getWidth(), getHeight());
                }
            };
            swatches.setPreferredSize(new Dimension(SWATCHES_WIDTH_PX, SWATCH_PX));

            setLayout(new BorderLayout());
            add(label, BorderLayout.WEST);
            add(swatches, BorderLayout.CENTER);
        }
    }
}
