package com.rox.ppu.debug;

import com.rox.cartridge.Cartridge;
import com.rox.ppu.PPU;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A live PPU debug window: CHR pattern tables, the current nametable, OAM sprites, the actual rendered
 * background, a palette swatch grid and a register/timing HUD - all in real colour, via
 * {@code NesPalette}. A passive observer of whatever
 * {@link PPU}/{@link Cartridge} it's currently {@link #rebind bound} to - it just repaints from their
 * current state on each timer tick, no hooks into the emulation clock/thread needed. Which ROM is
 * running is entirely the caller's concern: a File &gt; Open ROM... menu reports the chosen path to
 * whatever handler {@link #setOnOpenRom} registered, and {@link #rebind} is how the caller then hands
 * back the new state to display once that ROM is actually loaded and running.
 */
public final class PpuDebugFrame extends JFrame {
    private static final int REFRESH_MILLIS = 16; //~60fps
    //matches the outsized scale of the rest of this window (32pt titled borders, 50pt HUD text) -
    //Swing's default menu font looks tiny sitting above panels rendered at that scale
    private static final float MENU_FONT_SIZE = 50f;
    private static final float FILE_CHOOSER_FONT_SIZE = 28f;
    private static final Dimension FILE_CHOOSER_SIZE = new Dimension(1200, 800);

    private final JFileChooser romChooser = new JFileChooser();
    private Timer refreshTimer;
    private Consumer<Path> onOpenRom = romPath -> { };

    public PpuDebugFrame(){
        super("PPU Debug Viewer");
        romChooser.setFileFilter(new FileNameExtensionFilter("NES ROMs (*.nes)", "nes"));
        romChooser.setPreferredSize(FILE_CHOOSER_SIZE);
        applyFont(romChooser, new Font(Font.SANS_SERIF, Font.PLAIN, (int) FILE_CHOOSER_FONT_SIZE));
        setJMenuBar(buildMenuBar());
        setContentPane(placeholderContent());
        pack();
    }

    /** Registers the callback invoked (on the EDT) with the file path chosen via File &gt; Open ROM.... */
    public void setOnOpenRom(final Consumer<Path> onOpenRom){
        this.onOpenRom = onOpenRom;
    }

    /** Swaps in a freshly-loaded ROM's live state, replacing whatever the window was previously showing (if anything). */
    public void rebind(final PPU ppu, final Cartridge cartridge){
        if (refreshTimer != null){
            refreshTimer.stop();
        }

        final ChrViewerPanel chrViewer = new ChrViewerPanel(ppu, cartridge);
        final NametableViewerPanel nametableViewer = new NametableViewerPanel(ppu, cartridge);
        final OamViewerPanel oamViewer = new OamViewerPanel(ppu, cartridge);
        final BackgroundViewerPanel backgroundViewer = new BackgroundViewerPanel(ppu);
        final PaletteViewerPanel paletteViewer = new PaletteViewerPanel(ppu);
        final RegisterHudPanel registerHud = new RegisterHudPanel(ppu);

        final JPanel imagePanels = new JPanel(new GridLayout(2, 2));
        imagePanels.add(titled("CHR pattern tables", chrViewer));
        imagePanels.add(titled("Nametable", nametableViewer));
        imagePanels.add(titled("OAM sprites", oamViewer));
        imagePanels.add(titled("Background", backgroundViewer));

        final JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.add(titled("Registers", registerHud), BorderLayout.CENTER);
        sidebar.add(titled("Palettes", paletteViewer), BorderLayout.SOUTH);

        final JPanel content = new JPanel(new BorderLayout());
        content.add(imagePanels, BorderLayout.CENTER);
        content.add(sidebar, BorderLayout.EAST);
        setContentPane(content);

        refreshTimer = new Timer(REFRESH_MILLIS, e -> {
            registerHud.refresh();
            chrViewer.repaint();
            nametableViewer.repaint();
            oamViewer.repaint();
            backgroundViewer.repaint();
            paletteViewer.repaint();
        });
        refreshTimer.start();

        pack();
    }

    /** Reports a ROM that failed to load (unreadable file, unsupported mapper, ...) via a dialog rather than failing silently. */
    public void showLoadError(final Path romPath, final Exception cause){
        final JOptionPane optionPane = new JOptionPane(
                "Couldn't load " + romPath.getFileName() + ":\n" + cause.getMessage(),
                JOptionPane.ERROR_MESSAGE);
        applyFont(optionPane, new Font(Font.SANS_SERIF, Font.PLAIN, (int) FILE_CHOOSER_FONT_SIZE));
        optionPane.createDialog(this, "Failed to load ROM").setVisible(true);
    }

    /** Stops the refresh timer before disposing, so a closed window doesn't keep repainting forever. */
    @Override
    public void dispose(){
        if (refreshTimer != null){
            refreshTimer.stop();
        }
        super.dispose();
    }

    private JMenuBar buildMenuBar(){
        final Font menuFont = new Font(Font.SANS_SERIF, Font.PLAIN, (int) MENU_FONT_SIZE);

        final JMenuItem openRom = new JMenuItem("Open ROM...");
        openRom.setMnemonic('O');
        openRom.setFont(menuFont);
        openRom.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openRom.addActionListener(e -> {
            if (romChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
                onOpenRom.accept(romChooser.getSelectedFile().toPath());
            }
        });

        final JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        fileMenu.setFont(menuFont);
        fileMenu.add(openRom);

        final JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        return menuBar;
    }

    private static JPanel placeholderContent(){
        final JPanel content = new JPanel(new BorderLayout());
        final JLabel label = new JLabel("File → Open ROM... to begin", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 24f));
        content.setPreferredSize(new Dimension(600, 300));
        content.add(label, BorderLayout.CENTER);
        return content;
    }

    /**
     * Recursively sets {@code font} on {@code component} and every descendant - {@link JFileChooser}
     * has no single UIManager key that cascades to its list/buttons/combo box/labels across all
     * look-and-feels, so its default font (and thus the whole dialog) otherwise stays tiny next to the
     * rest of this oversized window.
     */
    private static void applyFont(final Component component, final Font font){
        component.setFont(font);
        if (component instanceof Container container){
            for (final Component child : container.getComponents()){
                applyFont(child, font);
            }
        }
    }

    private static JPanel titled(final String title, final JPanel panel){
        final JPanel wrapper = new JPanel(new BorderLayout());
        final TitledBorder titledBorder = BorderFactory.createTitledBorder(title);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD, 32f));
        wrapper.setBorder(titledBorder);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }
}
