package com.rox.debug.memory;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Point;

/**
 * A hex/ASCII view of one {@link MemoryRegion} at a time, picked from a dropdown. {@link #refresh()} is
 * the caller's to drive (e.g. from a timer); it keeps the scroll position, so a refreshing view can
 * still be browsed. EDT only.
 */
public final class MemoryViewerPanel extends JPanel {
    private static final int TEXT_ROWS = 20;
    private static final int TEXT_COLUMNS = 74;
    //large explicit sizes, like the other debug panels: Swing doesn't scale up its default fonts on a
    //high-DPI display here
    private static final int FONT_SIZE = 28;
    private static final float SELECTOR_FONT_SIZE = 28f;

    private final MemoryDebugSource source;
    private final JComboBox<MemoryRegion> regionSelector = new JComboBox<>(MemoryRegion.values());
    private final JTextArea text = new JTextArea(TEXT_ROWS, TEXT_COLUMNS);
    private final JScrollPane scrollPane = new JScrollPane(text);

    public MemoryViewerPanel(final MemoryDebugSource source){
        super(new BorderLayout());
        this.source = source;
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE));
        //never move the caret on setText(), so a refresh doesn't scroll the view back to the top
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        regionSelector.setFont(regionSelector.getFont().deriveFont(SELECTOR_FONT_SIZE));
        //focusable, so the keyboard can switch regions once this window is clicked into - it's a separate
        //window from the game's, so making it unfocusable never kept focus on the game anyway
        regionSelector.addActionListener(e -> {
            refresh();
            scrollPane.getViewport().setViewPosition(new Point(0, 0)); //a new region starts at its top
        });
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(regionSelector, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        refresh();
    }

    public void refresh(){
        final MemoryRegion region = (MemoryRegion) regionSelector.getSelectedItem();
        final Point scrolledTo = scrollPane.getViewport().getViewPosition();
        text.setText(HexDump.format(source.peek(), region.start(), region.end(), true));
        scrollPane.getViewport().setViewPosition(scrolledTo);
    }
}
