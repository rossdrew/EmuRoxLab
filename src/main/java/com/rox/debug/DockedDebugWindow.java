package com.rox.debug;

import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern UI prototype: one debugger window holding several panels as ModernDocking dockables - drag a tab
 * to rearrange, split, tab together or float it. Each panel refreshes on its own timer, only while it's
 * actually showing (a background tab or a hidden window doesn't redraw). Like {@link RefreshingDebugFrame},
 * opening it leaves keyboard focus on the game, and closing it just hides it. EDT only.
 */
public final class DockedDebugWindow extends JFrame {
    private static final Dimension DEFAULT_SIZE = new Dimension(2200, 1300);

    /** One panel to dock: {@code refresh} runs every {@code refreshMillis} while it's showing. */
    public record Panel(String id, String title, JComponent content, Runnable refresh, int refreshMillis) {
    }

    private final Map<String, PanelDockable> dockables = new LinkedHashMap<>();
    private final List<Timer> timers = new ArrayList<>();

    /** @param panels docked as: first on the left, second on the right, any more as tabs with the second */
    public DockedDebugWindow(final String title, final List<Panel> panels){
        super(title);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setAutoRequestFocus(false);
        Docking.initialize(this);
        add(new RootDockingPanel(this), BorderLayout.CENTER);
        setSize(DEFAULT_SIZE);

        for (final Panel panel : panels){
            final PanelDockable dockable = new PanelDockable(panel);
            dockables.put(panel.id(), dockable);
            final Timer timer = new Timer(panel.refreshMillis(), e -> {
                if (dockable.isShowing()){
                    panel.refresh().run();
                }
            });
            timers.add(timer);
        }
        dockDefaultLayout(new ArrayList<>(dockables.values()));
    }

    private void dockDefaultLayout(final List<PanelDockable> panels){
        if (panels.isEmpty()){
            return;
        }
        Docking.dock(panels.get(0), this);
        if (panels.size() > 1){
            Docking.dock(panels.get(1), panels.get(0), DockingRegion.EAST, 0.5);
        }
        for (int i = 2; i < panels.size(); i++){
            Docking.dock(panels.get(i), panels.get(1), DockingRegion.CENTER);
        }
    }

    /** Shows (or raises) the window with panel {@code id} brought forward - re-docking it if it was closed. */
    public void showPanel(final String id){
        timers.forEach(Timer::start);
        setVisible(true);
        toFront();
        Docking.display(dockables.get(id));
    }

    @Override
    public void dispose(){
        timers.forEach(Timer::stop);
        //also closes any panel floated into its own window - those aren't owned by this one
        Docking.deregisterAllDockingPanels();
        super.dispose();
    }

    /** An existing debug panel, wrapped so ModernDocking can dock it. */
    private static final class PanelDockable extends JPanel implements Dockable {
        private final Panel panel;

        PanelDockable(final Panel panel){
            super(new BorderLayout());
            this.panel = panel;
            add(panel.content(), BorderLayout.CENTER);
            Docking.registerDockable(this);
        }

        @Override
        public String getPersistentID(){
            return panel.id();
        }

        @Override
        public String getTabText(){
            return panel.title();
        }

        /** The panels scroll themselves where they need to. */
        @Override
        public boolean isWrappableInScrollpane(){
            return false;
        }
    }
}
