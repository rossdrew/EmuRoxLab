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

/**
 * All 8 of the PPU's palettes ({@code $3F00-$3F1F}) at once, one row per palette (background palettes
 * 0-3, then sprite palettes 0-3 - the same 8 names as {@link ChrViewerPanel}'s selector), each row
 * labelled to its left and gapped from its neighbours for readability. A header row along the top
 * numbers the 4 swatch columns, aligned with the swatches beneath it - both the header and every row
 * lay their 4 columns out with a plain {@link GridLayout} that always fills 100% of its available
 * width, rather than one of them centering a scaled image within its own space, so the columns can't
 * drift out of alignment regardless of how much extra width/height the sidebar ends up giving this
 * panel. Colours are resolved live from {@link PPU#paletteSnapshot()} through {@code NesPalette} via
 * {@link #refresh()}, called once per frame by {@link PpuDebugFrame}'s timer. Swatch 0 of each Sprite
 * row mirrors swatch 0 of its corresponding Background row (Sprite 1's colour 0 is Background 1's, not
 * Background 0's) - real hardware's backdrop-mirror quirk pairs each sprite palette with its
 * same-numbered background palette, not all 8 with a single shared entry (see
 * {@link PPU#paletteSnapshot()}'s own documentation).
 */
final class PaletteViewerPanel extends JPanel {
    private static final int COLORS_PER_PALETTE = 4;
    private static final int SWATCH_PX = 24;
    private static final int ROW_GAP_PX = 8;
    private static final float LABEL_FONT_SIZE = 24f;
    private static final int SWATCHES_WIDTH_PX = COLORS_PER_PALETTE * SWATCH_PX;

    private final PPU ppu;
    private final int labelColumnPx;
    private final PaletteRow[] rows = new PaletteRow[ChrViewerPanel.PALETTE_NAMES.length];

    PaletteViewerPanel(final PPU ppu){
        this.ppu = ppu;
        this.labelColumnPx = widestLabelWidth();

        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);

        final JPanel rowsPanel = new JPanel(new GridLayout(rows.length, 1, 0, ROW_GAP_PX));
        for (int paletteIndex = 0; paletteIndex < rows.length; paletteIndex++){
            rows[paletteIndex] = new PaletteRow(ChrViewerPanel.PALETTE_NAMES[paletteIndex]);
            rowsPanel.add(rows[paletteIndex]);
        }
        add(rowsPanel, BorderLayout.CENTER);

        refresh();
    }

    /** Pulls the PPU's current palette RAM into every row's swatches - called once per frame, not on every repaint, since colours only change when the game writes new ones. */
    void refresh(){
        final int[] paletteSnapshot = ppu.paletteSnapshot();
        for (int paletteIndex = 0; paletteIndex < rows.length; paletteIndex++){
            rows[paletteIndex].refresh(paletteSnapshot, paletteIndex * COLORS_PER_PALETTE);
        }
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

    /** One palette's name label plus its 4-swatch strip, side by side. */
    private final class PaletteRow extends JPanel {
        private final JPanel[] swatches = new JPanel[COLORS_PER_PALETTE];

        PaletteRow(final String name){
            final JLabel label = new JLabel(name);
            label.setFont(label.getFont().deriveFont(LABEL_FONT_SIZE));
            label.setPreferredSize(new Dimension(labelColumnPx, label.getPreferredSize().height));

            final JPanel swatchesPanel = new JPanel(new GridLayout(1, COLORS_PER_PALETTE));
            swatchesPanel.setPreferredSize(new Dimension(SWATCHES_WIDTH_PX, SWATCH_PX));
            for (int color = 0; color < COLORS_PER_PALETTE; color++){
                final JPanel swatch = new JPanel();
                swatch.setOpaque(true);
                swatches[color] = swatch;
                swatchesPanel.add(swatch);
            }

            setLayout(new BorderLayout());
            add(label, BorderLayout.WEST);
            add(swatchesPanel, BorderLayout.CENTER);
        }

        void refresh(final int[] paletteSnapshot, final int base){
            for (int color = 0; color < COLORS_PER_PALETTE; color++){
                swatches[color].setBackground(new Color(NesPalette.rgb(paletteSnapshot[base + color])));
            }
        }
    }
}
