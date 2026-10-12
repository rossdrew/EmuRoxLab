package com.rox.debug.labels;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The watch list: one row per label with its live value, a Lock checkbox, and buttons to add, rename,
 * remove, set and reload. All logic lives in {@link LabelWatchModel} - this only displays it and turns
 * clicks into calls. {@link #refresh()} is the caller's to drive (e.g. from a timer); EDT only.
 */
public final class LabelWatchPanel extends JPanel {
    private static final String[] COLUMNS = {"Name", "Address", "Hex", "Decimal", "Lock"};
    private static final int LOCK_COLUMN = 4;
    //large explicit sizes, like the other debug panels: Swing doesn't scale up its default fonts on a
    //high-DPI display here
    private static final float FONT_SIZE = 28f;
    private static final int ROW_HEIGHT = 44;

    private final LabelWatchModel model;
    private final RowsTableModel rows = new RowsTableModel();
    private final JTable table = new JTable(rows);
    private final JLabel problem = new JLabel();

    public LabelWatchPanel(final LabelWatchModel model){
        super(new BorderLayout());
        this.model = model;

        table.setFont(table.getFont().deriveFont(FONT_SIZE));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, FONT_SIZE));
        table.setRowHeight(ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(final MouseEvent e){
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) != LOCK_COLUMN){
                    setValue();
                }
            }
        });

        problem.setForeground(Color.RED.darker());
        problem.setFont(problem.getFont().deriveFont(FONT_SIZE));

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Add...", this::add));
        buttons.add(button("Rename...", this::rename));
        buttons.add(button("Remove", this::remove));
        buttons.add(button("Set value...", this::setValue));
        buttons.add(button("Reload", () -> {
            model.reload();
            rows.refreshAll();
        }));

        final JPanel top = new JPanel(new BorderLayout());
        top.add(buttons, BorderLayout.NORTH);
        top.add(problem, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    /** Re-reads every label's value and lock state, keeping the selection. */
    public void refresh(){
        rows.refreshValues();
        problem.setText(model.loadProblem().map(p -> "labels.txt couldn't be read (labels are read-only until it's fixed and reloaded): " + p).orElse(""));
        problem.setVisible(model.loadProblem().isPresent());
    }

    private JButton button(final String label, final Runnable action){
        final JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(FONT_SIZE));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void add(){
        final String text = JOptionPane.showInputDialog(this, "New label, e.g. '$066D rupees' or '$0670-$0671 keys':", "Add label",
                JOptionPane.PLAIN_MESSAGE);
        if (text != null){
            attempt(() -> model.add(text));
            rows.refreshAll();
        }
    }

    private void rename(){
        final int row = table.getSelectedRow();
        if (row < 0){
            return;
        }
        final String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename label", JOptionPane.PLAIN_MESSAGE,
                null, null, model.labels().get(row).name());
        if (name != null){
            attempt(() -> model.rename(row, name));
            rows.refreshValues();
        }
    }

    private void remove(){
        final int row = table.getSelectedRow();
        if (row >= 0){
            attempt(() -> model.remove(row));
            rows.refreshAll();
        }
    }

    private void setValue(){
        final int row = table.getSelectedRow();
        if (row < 0){
            return;
        }
        final LabelWatchModel.Row current = model.rows().get(row);
        final String text = (String) JOptionPane.showInputDialog(this,
                "Value for '" + current.name() + "' - one per byte, e.g. 31, $1F or 0x1F:", "Set value",
                JOptionPane.PLAIN_MESSAGE, null, null, current.hex());
        if (text != null){
            attempt(() -> model.setValue(row, text));
            rows.refreshValues();
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    /** Runs {@code action}, showing what went wrong rather than letting it escape the button handler. */
    private void attempt(final Action action){
        try {
            action.run();
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, e.getMessage(), "Couldn't do that", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The table's view of {@link LabelWatchModel#rows()}. */
    private final class RowsTableModel extends AbstractTableModel {
        private List<LabelWatchModel.Row> current = List.of();

        /** Values only (same rows) - keeps the selection across the frequent refreshes. */
        void refreshValues(){
            final List<LabelWatchModel.Row> latest = model.rows();
            final boolean sameRows = latest.size() == current.size();
            current = latest;
            if (sameRows && !current.isEmpty()){
                fireTableRowsUpdated(0, current.size() - 1);
            } else if (!sameRows){
                fireTableDataChanged();
            }
        }

        /** After rows were added or removed. */
        void refreshAll(){
            current = model.rows();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount(){
            return current.size();
        }

        @Override
        public int getColumnCount(){
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(final int column){
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(final int column){
            return column == LOCK_COLUMN ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(final int row, final int column){
            return column == LOCK_COLUMN && current.get(row).editable();
        }

        @Override
        public Object getValueAt(final int row, final int column){
            final LabelWatchModel.Row r = current.get(row);
            return switch (column){
                case 0 -> r.name();
                case 1 -> r.address();
                case 2 -> r.hex();
                case 3 -> r.decimal();
                default -> r.locked();
            };
        }

        @Override
        public void setValueAt(final Object value, final int row, final int column){
            if (column == LOCK_COLUMN){
                attempt(() -> model.setLocked(row, (Boolean) value));
                refreshValues();
            }
        }
    }
}
