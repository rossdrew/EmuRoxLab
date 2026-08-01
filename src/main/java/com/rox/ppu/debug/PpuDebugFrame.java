package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.ppu.PPU;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * A live PPU debug window: CHR pattern tables, the current nametable, OAM sprites and a register/
 * timing HUD, grayscale (no {@code NesPalette} until a later phase). A passive observer - it just
 * repaints from whatever the given {@link PPU}/{@link Cartridge}'s current state is on each timer
 * tick, no hooks into the emulation clock/thread needed.
 */
public final class PpuDebugFrame extends JFrame {
    private static final int REFRESH_MILLIS = 16; //~60fps

    public PpuDebugFrame(final PPU ppu, final Cartridge cartridge){
        super("PPU Debug Viewer");

        final ChrViewerPanel chrViewer = new ChrViewerPanel(cartridge);
        final NametableViewerPanel nametableViewer = new NametableViewerPanel(ppu, cartridge);
        final OamViewerPanel oamViewer = new OamViewerPanel(ppu, cartridge);
        final RegisterHudPanel registerHud = new RegisterHudPanel(ppu);

        final JPanel content = new JPanel(new GridLayout(2, 2));
        content.add(titled("CHR pattern tables", chrViewer));
        content.add(titled("Nametable", nametableViewer));
        content.add(titled("OAM sprites", oamViewer));
        content.add(titled("Registers", registerHud));
        setContentPane(content);

        final Timer timer = new Timer(REFRESH_MILLIS, e -> {
            registerHud.refresh();
            chrViewer.repaint();
            nametableViewer.repaint();
            oamViewer.repaint();
        });
        timer.start();

        pack();
    }

    private static JPanel titled(final String title, final JPanel panel){
        final JPanel wrapper = new JPanel(new BorderLayout());
        final TitledBorder titledBorder = BorderFactory.createTitledBorder(title);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD, 32f));
        wrapper.setBorder(titledBorder);
        wrapper.setBorder(titledBorder);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }
}
