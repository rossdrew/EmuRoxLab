package com.rox.debug.breakpoints;

import com.rox.debug.cpu.CpuStateText;
import com.rox.debug.labels.LabelFileException;
import com.rox.debug.labels.LabelStore;
import com.rox.debug.labels.MemoryLabel;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * The breakpoint and watchpoint list: type an address ({@code $C123}) or label name, pick what to stop on, Add;
 * select one and Remove (or Delete). Also says what last stopped the emulation. {@link #refresh()} is the
 * caller's to drive (e.g. from a timer); EDT only.
 */
public final class BreakpointsPanel extends JPanel {
    /** What the dropdown offers - a breakpoint, or a watchpoint of each {@link WatchKind}. */
    private static final String[] KINDS = {"Breakpoint (PC)", "Watch reads", "Watch writes", "Watch reads + writes"};
    private static final WatchKind[] WATCH_KINDS = {null, WatchKind.READ, WatchKind.WRITE, WatchKind.ACCESS};
    //large explicit sizes, like the other debug panels: Swing doesn't scale up its default fonts on a
    //high-DPI display here
    private static final float FONT_SIZE = 28f;
    private static final int ADDRESS_COLUMNS = 12;

    /** A list row: what it shows, and how to remove it. */
    private record Entry(String text, Runnable remove){
        @Override
        public String toString(){
            return text;
        }
    }

    private final Breakpoints breakpoints;
    private final Supplier<Optional<BreakHit>> lastHit;
    private final LabelStore labelStore;
    private final JTextField address = new JTextField(ADDRESS_COLUMNS);
    private final JComboBox<String> kind = new JComboBox<>(KINDS);
    private final DefaultListModel<Entry> entries = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(entries);
    private final JLabel status = new JLabel(" ");
    private final JLabel problem = new JLabel(" ");
    /** What the list was last built from, so a refresh only rebuilds it (losing the selection) on a change. */
    private Object shown;

    public BreakpointsPanel(final Breakpoints breakpoints, final Supplier<Optional<BreakHit>> lastHit, final LabelStore labelStore){
        super(new BorderLayout());
        this.breakpoints = breakpoints;
        this.lastHit = lastHit;
        this.labelStore = labelStore;

        address.setFont(address.getFont().deriveFont(FONT_SIZE));
        address.addActionListener(e -> add());
        address.setToolTipText("$C123, or a label's name");
        kind.setFont(kind.getFont().deriveFont(FONT_SIZE));
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, (int) FONT_SIZE));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(final KeyEvent e){
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE){
                    remove();
                }
            }
        });
        status.setFont(status.getFont().deriveFont(Font.BOLD, FONT_SIZE));
        status.setForeground(new Color(0xD0, 0x00, 0x00));
        problem.setFont(problem.getFont().deriveFont(FONT_SIZE));
        problem.setForeground(Color.RED.darker());

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(address);
        controls.add(kind);
        controls.add(button("Add", this::add));
        controls.add(button("Remove", this::remove));
        final JPanel top = new JPanel(new BorderLayout());
        top.add(controls, BorderLayout.NORTH);
        top.add(problem, BorderLayout.CENTER);
        top.add(status, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        refresh();
    }

    public void refresh(){
        status.setText(lastHit.get().map(hit -> CpuStateText.HIT_PREFIX + CpuStateText.hit(hit)).orElse(" "));
        final Object current = List.of(breakpoints.breakpoints(), breakpoints.watches());
        if (!current.equals(shown)){
            shown = current;
            rebuildList();
        }
    }

    private void rebuildList(){
        final List<MemoryLabel> labels = labels();
        entries.clear();
        for (final int pc : breakpoints.breakpoints()){
            entries.addElement(new Entry("Break at " + AddressText.describe(pc, labels), () -> breakpoints.removeBreakpoint(pc)));
        }
        for (final Map.Entry<Integer, WatchKind> watch : breakpoints.watches().entrySet()){
            final int watched = watch.getKey();
            entries.addElement(new Entry(watchText(watch.getValue()) + " " + AddressText.describe(watched, labels),
                    () -> breakpoints.removeWatch(watched)));
        }
    }

    private void add(){
        final OptionalInt parsed = AddressText.parse(address.getText(), labels());
        if (parsed.isEmpty()){
            problem.setText("Not an address ($C123) or label name: " + address.getText());
            return;
        }
        final WatchKind watch = WATCH_KINDS[kind.getSelectedIndex()];
        if (watch == null){
            breakpoints.addBreakpoint(parsed.getAsInt());
        } else {
            breakpoints.addWatch(parsed.getAsInt(), watch);
        }
        problem.setText(" ");
        address.setText("");
        refresh();
    }

    private void remove(){
        final Entry selected = list.getSelectedValue();
        if (selected != null){
            selected.remove().run();
            refresh();
        }
    }

    /** The ROM's labels as saved now - empty if there are none, or they can't be read. */
    private List<MemoryLabel> labels(){
        try {
            return new ArrayList<>(labelStore.load());
        } catch (IOException | LabelFileException e){
            return List.of();
        }
    }

    private JButton button(final String label, final Runnable action){
        final JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(FONT_SIZE));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static String watchText(final WatchKind kind){
        return switch (kind){
            case READ -> "Watch reads of";
            case WRITE -> "Watch writes to";
            case ACCESS -> "Watch reads + writes of";
        };
    }
}
