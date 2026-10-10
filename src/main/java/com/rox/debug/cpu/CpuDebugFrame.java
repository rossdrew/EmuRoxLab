package com.rox.debug.cpu;

import javax.swing.JFrame;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A standalone window around a {@link CpuStatePanel}, refreshed ~10 times a second - until the
 * unified debug console (plan Phase 5) gives the panel a tab of its own. Closing it just hides it;
 * {@link #showWindow()} brings it back. EDT only.
 */
public final class CpuDebugFrame extends JFrame {
    private static final int REFRESH_MILLIS = 100;

    private final Timer refreshTimer;

    public CpuDebugFrame(final CpuDebugSource source){
        super("EmuRoxLab - CPU state");
        final CpuStatePanel panel = new CpuStatePanel(source);
        getContentPane().add(panel);
        pack();
        refreshTimer = new Timer(REFRESH_MILLIS, e -> panel.refresh());
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(final WindowEvent e){
                refreshTimer.stop(); //nothing to repaint while hidden
            }
        });
    }

    /** Shows (or re-shows and raises) the window and starts refreshing. */
    public void showWindow(){
        refreshTimer.start();
        setVisible(true);
        toFront();
    }

    /** Stops refreshing before disposing, so a closed window doesn't keep a timer running. */
    @Override
    public void dispose(){
        refreshTimer.stop();
        super.dispose();
    }
}
