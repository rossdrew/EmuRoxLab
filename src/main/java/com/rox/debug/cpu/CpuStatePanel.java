package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Live CPU state - registers, flags, clock rate, recent and upcoming instructions (all rendered by
 * {@link CpuStateText#panel}) - plus Pause/Resume, and while paused, Step instruction, Step frame
 * and Step N cycles. {@link #refresh()} is the caller's to drive (e.g. from a Swing Timer); EDT only.
 */
public final class CpuStatePanel extends JPanel {
    static final int UPCOMING_INSTRUCTION_COUNT = 10;
    private static final int TEXT_ROWS = 24;
    private static final int TEXT_COLUMNS = 56;
    //large explicit sizes, like RomInfoPanel/RegisterHudPanel: Swing doesn't scale up its default fonts
    //on a high-DPI display here, leaving them unreadably small
    private static final int FONT_SIZE = 32;
    private static final float BUTTON_FONT_SIZE = 28f;
    //stepping runs on the EDT, so a huge count would freeze the window - this is ~33 frames
    private static final int MAX_STEP_CYCLES = 1_000_000;

    private final CpuDebugSource source;
    private final JTextPane text = new JTextPane();
    private final RegisterChanges changes = new RegisterChanges();
    private final JumpFlash jumpFlash = new JumpFlash(System::currentTimeMillis);
    private final Map<CpuTextHighlighter.Kind, AttributeSet> styles = styles();
    private final JButton pauseResume = new JButton();
    private final JButton stepInstruction = new JButton("Step instruction");
    private final JButton stepFrame = new JButton("Step frame");
    private final JButton stepCycles = new JButton("Step cycles:");
    private final JSpinner cycleCount = new JSpinner(new SpinnerNumberModel(1, 1, MAX_STEP_CYCLES, 1));

    public CpuStatePanel(final CpuDebugSource source){
        super(new BorderLayout());
        this.source = source;
        text.setEditable(false);
        final Font font = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);
        text.setFont(font);
        final JScrollPane scrollPane = new JScrollPane(text);
        final FontMetrics metrics = text.getFontMetrics(font);
        scrollPane.setPreferredSize(new Dimension(metrics.charWidth('M') * TEXT_COLUMNS, metrics.getHeight() * TEXT_ROWS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(scrollPane, BorderLayout.CENTER);

        pauseResume.setFocusable(false); //keep keyboard focus (and controller input) on the game window
        pauseResume.setFont(pauseResume.getFont().deriveFont(BUTTON_FONT_SIZE));
        pauseResume.addActionListener(e -> {
            if (source.isPaused()){
                source.resume();
            } else {
                source.pause();
            }
            refresh();
        });
        stepInstruction.addActionListener(e -> step(source::stepInstruction));
        stepFrame.addActionListener(e -> step(source::stepFrame));
        stepCycles.addActionListener(e -> step(() -> source.stepCycles((Integer) cycleCount.getValue())));
        for (final JComponent control : new JComponent[]{stepInstruction, stepFrame, stepCycles, cycleCount}){
            control.setFont(control.getFont().deriveFont(BUTTON_FONT_SIZE));
        }
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(pauseResume);
        buttons.add(stepInstruction);
        buttons.add(stepFrame);
        buttons.add(stepCycles);
        buttons.add(cycleCount);
        add(buttons, BorderLayout.NORTH);
        refresh();
    }

    public void refresh(){
        //one read, so the text and the change highlights describe the same moment
        final MOS6502Snapshot cpu = source.state();
        final String content = CpuStateText.panel(cpu, source, UPCOMING_INSTRUCTION_COUNT);
        final StyledDocument document = text.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            document.insertString(0, content, null);
        } catch (BadLocationException e){
            throw new IllegalStateException(e); //can't happen: whole-document replace
        }
        final boolean paused = source.isPaused();
        //only worked out while paused: JumpFlash never flashes while running anyway
        final boolean flashing = paused
                ? jumpFlash.update(cpu, true, source.recentInstructions().stream().map(TracedInstruction::instruction).toList(),
                        source.upcomingInstructions(1).get(0).address())
                : jumpFlash.update(cpu, false, List.of(), cpu.pc());
        for (final CpuTextHighlighter.Span span : CpuTextHighlighter.highlight(content, changes.update(cpu), flashing)){
            document.setCharacterAttributes(span.start(), span.length(), styles.get(span.kind()), false);
        }
        text.setCaretPosition(0);
        pauseResume.setText(paused ? "Resume" : "Pause");
        stepInstruction.setEnabled(paused);
        stepFrame.setEnabled(paused);
        stepCycles.setEnabled(paused);
        cycleCount.setEnabled(paused);
    }

    /** Colours for a light background; backgrounds mark the current instruction and changed values. */
    private static Map<CpuTextHighlighter.Kind, AttributeSet> styles(){
        final Map<CpuTextHighlighter.Kind, AttributeSet> styles = new EnumMap<>(CpuTextHighlighter.Kind.class);
        styles.put(CpuTextHighlighter.Kind.PAUSED, style(new Color(0xC0, 0x40, 0x00), true, null));
        styles.put(CpuTextHighlighter.Kind.REGISTER_NAME, style(Color.GRAY, false, null));
        styles.put(CpuTextHighlighter.Kind.REGISTER_VALUE, style(new Color(0x00, 0x30, 0x90), true, null));
        styles.put(CpuTextHighlighter.Kind.CHANGED, style(null, false, new Color(0xFF, 0xE0, 0x8A)));
        styles.put(CpuTextHighlighter.Kind.FLAG_SET, style(new Color(0x00, 0x80, 0x00), true, null));
        styles.put(CpuTextHighlighter.Kind.FLAG_CLEAR, style(Color.LIGHT_GRAY, false, null));
        styles.put(CpuTextHighlighter.Kind.ACTIVE, style(new Color(0xB0, 0x00, 0x00), true, null));
        styles.put(CpuTextHighlighter.Kind.HEADER, style(Color.GRAY, false, null));
        styles.put(CpuTextHighlighter.Kind.CURRENT_LINE, style(null, false, new Color(0xD6, 0xE9, 0xFF)));
        styles.put(CpuTextHighlighter.Kind.ADDRESS, style(Color.GRAY, false, null));
        styles.put(CpuTextHighlighter.Kind.JUMP_FLASH, style(Color.WHITE, true, new Color(0xD0, 0x00, 0x90)));
        styles.put(CpuTextHighlighter.Kind.FLOW, style(new Color(0x80, 0x00, 0x80), true, null));
        styles.put(CpuTextHighlighter.Kind.LOAD_STORE, style(new Color(0x00, 0x40, 0xC0), true, null));
        styles.put(CpuTextHighlighter.Kind.ARITHMETIC, style(new Color(0x00, 0x70, 0x70), true, null));
        styles.put(CpuTextHighlighter.Kind.FLAGS_AND_STACK, style(new Color(0xA0, 0x50, 0x00), true, null));
        styles.put(CpuTextHighlighter.Kind.IMMEDIATE, style(new Color(0x00, 0x80, 0x00), false, null));
        styles.put(CpuTextHighlighter.Kind.OPERAND, style(new Color(0x90, 0x60, 0x00), false, null));
        styles.put(CpuTextHighlighter.Kind.UNKNOWN, style(Color.RED, false, null));
        return styles;
    }

    private static AttributeSet style(final Color foreground, final boolean bold, final Color background){
        final SimpleAttributeSet style = new SimpleAttributeSet();
        if (foreground != null){
            StyleConstants.setForeground(style, foreground);
            //only alongside a colour: background-only styles layer over text and must leave its boldness alone
            StyleConstants.setBold(style, bold);
        }
        if (background != null){
            StyleConstants.setBackground(style, background);
        }
        return style;
    }

    private void step(final Runnable stepAction){
        try {
            stepAction.run();
        } catch (IllegalStateException e){
            JOptionPane.showMessageDialog(this, e.getMessage(), "Couldn't step", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }
}
