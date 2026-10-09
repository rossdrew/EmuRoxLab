package com.rox.debug.cpu;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Live CPU state - registers, flags, clock rate, recent and upcoming instructions (all rendered by
 * {@link CpuStateText#panel}) - plus a Pause/Resume button. {@link #refresh()} is the caller's to
 * drive (e.g. from a Swing Timer); EDT only.
 */
public final class CpuStatePanel extends JPanel {
    static final int UPCOMING_INSTRUCTION_COUNT = 10;
    private static final int TEXT_ROWS = 24;
    private static final int TEXT_COLUMNS = 56;
    //large explicit sizes, like RomInfoPanel/RegisterHudPanel: Swing doesn't scale up its default fonts
    //on a high-DPI display here, leaving them unreadably small
    private static final int FONT_SIZE = 32;
    private static final float BUTTON_FONT_SIZE = 28f;

    private final CpuDebugSource source;
    private final JTextArea text = new JTextArea(TEXT_ROWS, TEXT_COLUMNS);
    private final JButton pauseResume = new JButton();

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
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(pauseResume);
        add(buttons, BorderLayout.NORTH);
        refresh();
    }

    public void refresh(){
        text.setText(CpuStateText.panel(source, UPCOMING_INSTRUCTION_COUNT));
        text.setCaretPosition(0);
        pauseResume.setText(source.isPaused() ? "Resume" : "Pause");
    }
}
