package com.rox.debug;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A standalone debug window around one panel, re-drawn on a timer while shown - until the unified
 * debug console (plan Phase 5) gives each panel a tab of its own. Closing it just hides it (and stops
 * refreshing); {@link #showWindow()} brings it back. EDT only.
 */
public final class RefreshingDebugFrame extends JFrame {
    private final Timer refreshTimer;

    /**
     * @param refresh how {@code content} redraws itself - run every {@code refreshMillis} while shown
     */
    public RefreshingDebugFrame(final String title, final JComponent content, final Runnable refresh, final int refreshMillis){
        super(title);
        getContentPane().add(content);
        pack();
        refreshTimer = new Timer(refreshMillis, e -> refresh.run());
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
