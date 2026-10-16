package com.rox.debug.memory;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.OptionalInt;

/**
 * A hex/ASCII view of one {@link MemoryRegion} at a time, picked from a dropdown. {@link #refresh()} is
 * the caller's to drive (e.g. from a timer); it keeps the scroll position, so a refreshing view can
 * still be browsed. Double-click a byte to edit it, where {@link MemoryDebugSource#isEditable editable}.
 * EDT only.
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
        text.setToolTipText("Double-click a byte to edit it (RAM and PRG-RAM)");
        text.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(final MouseEvent event){
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)){
                    editByteAt(text.viewToModel2D(event.getPoint()));
                }
            }
        });
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

    private void editByteAt(final int textOffset){
        final MemoryRegion region = (MemoryRegion) regionSelector.getSelectedItem();
        final OptionalInt clicked = HexDump.addressAt(textOffset, region.start(), region.end(), true);
        if (clicked.isEmpty()){
            return;
        }
        final int address = clicked.getAsInt();
        if (!source.isEditable(address)){
            JOptionPane.showMessageDialog(this, String.format("$%04X is read-only here - only RAM and PRG-RAM can be edited.", address),
                    "Can't edit", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final String current = String.format("%02X", source.peek().applyAsInt(address));
        final Object typed = JOptionPane.showInputDialog(this, String.format("New value for $%04X (hex):", address),
                "Edit memory", JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (typed == null){
            return; //cancelled
        }
        try {
            source.poke(address, HexDump.parseByte(typed.toString()));
        } catch (IllegalArgumentException e){
            JOptionPane.showMessageDialog(this, e.getMessage(), "Can't edit", JOptionPane.ERROR_MESSAGE);
            return;
        }
        refresh();
    }
}
