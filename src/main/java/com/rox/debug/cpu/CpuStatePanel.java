package com.rox.debug.cpu;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

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
    private final JTextArea text = new JTextArea(TEXT_ROWS, TEXT_COLUMNS);
    private final JButton pauseResume = new JButton();
    private final JButton stepInstruction = new JButton("Step instruction");
    private final JButton stepFrame = new JButton("Step frame");
    private final JButton stepCycles = new JButton("Step cycles:");
    private final JSpinner cycleCount = new JSpinner(new SpinnerNumberModel(1, 1, MAX_STEP_CYCLES, 1));

    public CpuStatePanel(final CpuDebugSource source){
        super(new BorderLayout());
        this.source = source;
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(new JScrollPane(text), BorderLayout.CENTER);

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
        text.setText(CpuStateText.panel(source, UPCOMING_INSTRUCTION_COUNT));
        text.setCaretPosition(0);
        final boolean paused = source.isPaused();
        pauseResume.setText(paused ? "Resume" : "Pause");
        stepInstruction.setEnabled(paused);
        stepFrame.setEnabled(paused);
        stepCycles.setEnabled(paused);
        cycleCount.setEnabled(paused);
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
