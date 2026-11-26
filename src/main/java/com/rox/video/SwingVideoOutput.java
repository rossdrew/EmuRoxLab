package com.rox.video;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * The actual "screen" a player would watch, as opposed to the {@code com.rox.ppu.debug} package's
 * inspection panels - a plain, freely resizable {@link JFrame} blitting each presented frame at the
 * largest size that both fits the window and preserves the NES's 256x240 aspect ratio ({@link
 * CenteredScale}), letterboxed/pillarboxed (centered, not stretched) rather than distorted,
 * nearest-neighbour interpolated (crisp pixel art, not blurred). {@link #present} is
 * called from the emulation's clock thread (see {@code NES}'s video-output clock listener), never the
 * EDT, so every Swing interaction there is marshalled via {@link SwingUtilities#invokeLater} rather
 * than touched directly - only construction is the caller's responsibility to run on the EDT (matching
 * this package's sibling {@code PpuDebugFrame}'s own convention), since it happens once, not per frame.
 */
public final class SwingVideoOutput implements VideoOutput {
    private static final int WIDTH_PX = 256;
    private static final int HEIGHT_PX = 240;
    private static final int SCALE = 3;
    private static final String TITLE = "EmuRoxLab";
    private static final int DESCRIPTION_ROWS = 8;
    private static final int DESCRIPTION_COLUMNS = 50;
    //a large explicit size, like the debug panels': Swing's default font is unreadably small on a
    //high-DPI display here
    private static final float MENU_FONT_SIZE = 24f;
    private static final float JITTER_FONT_SIZE = 18f;
    private static final int JITTER_REFRESH_MILLIS = 1000;
    /** {@code -Dframejitter.log=true} also prints the readout to stdout once a second, to compare runs. */
    private static final boolean LOG_JITTER = Boolean.getBoolean("framejitter.log");
    /** Shown small, on black, while no game is loaded - {@code null} if it can't be read. */
    private static final BufferedImage IDLE_IMAGE = readImage("scavenged_nes.png");
    /** {@link #IDLE_IMAGE} fits within this fraction of the window's width and height. */
    private static final int IDLE_IMAGE_FRACTION = 3;

    private final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
    private final JPanel canvas = new JPanel(){
        @Override
        protected void paintComponent(final Graphics g){
            super.paintComponent(g);
            final Graphics2D g2 = (Graphics2D) g;
            if (idle){
                paintIdle(g2);
                return;
            }
            final int[] rect = CenteredScale.fit(WIDTH_PX, HEIGHT_PX, getWidth(), getHeight());
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(image, rect[0], rect[1], rect[2], rect[3], null);
            //a frame counts as shown once it's painted - Swing may delay or merge repaints, and repaints
            //of an unchanged image (a resize, an expose) aren't new frames
            if (imageChanged){
                imageChanged = false;
                final long now = System.nanoTime();
                shown.record(now);
                shownListener.accept(now);
            }
        }
    };
    //a new frame is in the image but not yet painted - EDT only
    private boolean imageChanged;
    private final JFrame frame = new JFrame(TITLE);
    //present() is called once per emulated frame (~60/sec) from the clock thread, far faster than a
    //loaded EDT can necessarily keep up with - rather than queuing one invokeLater (and one retained
    //rgbFrame array) per call, which would grow unboundedly under any EDT backpressure, only the
    //latest frame is kept and at most one render task is ever pending at a time
    private final AtomicReference<int[]> pendingFrame = new AtomicReference<>();
    private final AtomicBoolean renderPending = new AtomicBoolean(false);
    //makes "set the latest frame, schedule a render if none is pending" and "take that frame, clear pending"
    //atomic with each other - otherwise a frame presented between a render clearing pending and reading
    //the frame is taken by that render and also schedules a second, redundant one
    private final Object frameHandoff = new Object();
    /** EDT only, like every other Swing interaction here. */
    private final JMenuBar menuBar = new JMenuBar();
    /** shown in the title after {@link #TITLE} - EDT only */
    private String gameName;
    /** {@link #IDLE_IMAGE} instead of frames, once {@link #showGame} says no game is loaded - EDT only */
    private boolean idle;
    private String status;
    /** see {@link #scaledIdleImage} */
    private BufferedImage scaledIdleImage;
    //frame-jitter readout: gaps between frames the emulation produced (clock thread) and between frames
    //actually drawn (EDT), plus frames replaced by a newer one before the EDT got to them
    private final FrameJitter produced = new FrameJitter();
    private final FrameJitter shown = new FrameJitter();
    private final AtomicInteger skipped = new AtomicInteger();
    private final JLabel jitterLabel = new JLabel(" ");
    private volatile LongConsumer producedListener = nanos -> { };
    private volatile LongConsumer shownListener = nanos -> { };
    private final Timer jitterTimer = new Timer(JITTER_REFRESH_MILLIS, e -> refreshJitter());

    /** The X button does nothing - see {@link #SwingVideoOutput(Runnable)} to opt into closing meaning something. */
    public SwingVideoOutput(){
        this(() -> { });
    }

    /**
     * @param onClose called when the window is closed (the X button) - the caller's cue to stop
     * whatever is feeding it frames. Registered before the window is ever shown, not afterward via a
     * separate setter: a listener added post-construction could miss a close that happens in the gap
     * between the window becoming visible and the caller getting around to registering one.
     */
    public SwingVideoOutput(final Runnable onClose){
        canvas.setPreferredSize(new Dimension(WIDTH_PX * SCALE, HEIGHT_PX * SCALE));
        frame.getContentPane().add(canvas, BorderLayout.CENTER);
        jitterLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(JITTER_FONT_SIZE)));
        frame.getContentPane().add(jitterLabel, BorderLayout.SOUTH);
        jitterTimer.start();
        //F10 would otherwise open the menu bar, swallowing it as a menu item's shortcut
        menuBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0), "none");
        frame.setJMenuBar(menuBar);
        AppIcon.applyTo(frame);
        frame.pack();
        //DO_NOTHING_ON_CLOSE + the listener below (rather than the confusing default of HIDE_ON_CLOSE,
        //which would hide the window without disposing it) - onClose is the caller's decision, not this
        //class's, about what closing the window should actually do
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(final WindowEvent e){
                onClose.run();
            }
        });
        frame.setVisible(true);
    }

    /**
     * @param listener receives real key events once the window has focus - unlike the close listener,
     * there's no register-before-{@code setVisible} ordering requirement here, since key events only
     * matter once emulation is actually running.
     */
    public void addKeyListener(final KeyListener listener){
        frame.addKeyListener(listener);
    }

    /**
     * Adds {@code label} to the menu at {@code menuPath} (e.g. {@code File, Open, ROM}), creating menus as
     * needed. {@code accelerator} (or {@code null}) is shown beside it and triggers it while the window
     * has focus. EDT only, and {@code action} runs on the EDT too, so it can show dialogs directly.
     *
     * @return the item, e.g. to disable it
     */
    public JMenuItem addMenuItem(final List<String> menuPath, final String label, final KeyStroke accelerator,
                                 final Runnable action){
        final JMenuItem item = new JMenuItem(label);
        item.setFont(item.getFont().deriveFont(MENU_FONT_SIZE));
        item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        menu(menuPath).add(item);
        return item;
    }

    /**
     * Adds a tick-box item to the menu at {@code menuPath}, starting {@code selected}; {@code onToggle} gets each
     * new state. EDT only, like {@code onToggle}.
     */
    public void addCheckBoxMenuItem(final List<String> menuPath, final String label, final boolean selected,
                                    final Consumer<Boolean> onToggle){
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, selected);
        item.setFont(item.getFont().deriveFont(MENU_FONT_SIZE));
        item.addActionListener(e -> onToggle.accept(item.isSelected()));
        menu(menuPath).add(item);
    }

    /**
     * Below whatever the menu at {@code menuPath} holds now, shows {@code entries} as they are each time
     * it opens - e.g. a list that changes. EDT only, like the entries' actions.
     */
    public void fillMenuOnOpen(final List<String> menuPath, final Supplier<List<MenuEntry>> entries){
        final JMenu menu = menu(menuPath);
        final int fixedItems = menu.getMenuComponentCount();
        menu.addMenuListener(new MenuListener(){
            @Override
            public void menuSelected(final MenuEvent e){
                while (menu.getMenuComponentCount() > fixedItems){
                    menu.remove(fixedItems);
                }
                final List<MenuEntry> filled = entries.get();
                if (!filled.isEmpty() && fixedItems > 0){
                    menu.addSeparator();
                }
                filled.forEach(entry -> addEntry(menu, entry));
            }

            @Override
            public void menuDeselected(final MenuEvent e){
            }

            @Override
            public void menuCanceled(final MenuEvent e){
            }
        });
    }

    private static void addEntry(final JMenu menu, final MenuEntry entry){
        if (entry == MenuEntry.SEPARATOR){
            menu.addSeparator();
            return;
        }
        final JMenuItem item = new JMenuItem(entry.label());
        item.setFont(item.getFont().deriveFont(MENU_FONT_SIZE));
        if (entry.action() == null){
            item.setEnabled(false);
        } else {
            item.addActionListener(e -> entry.action().run());
        }
        menu.add(item);
    }

    /** The menu at {@code path} under the menu bar, created (empty, at the end) if missing. */
    private JMenu menu(final List<String> path){
        JMenu menu = null;
        for (final String name : path){
            menu = menu == null ? topLevelMenu(name) : childMenu(menu, name);
        }
        return menu;
    }

    private JMenu topLevelMenu(final String name){
        for (int i = 0; i < menuBar.getMenuCount(); i++){
            if (menuBar.getMenu(i).getText().equals(name)){
                return menuBar.getMenu(i);
            }
        }
        final JMenu menu = newMenu(name);
        menuBar.add(menu);
        menuBar.revalidate();
        return menu;
    }

    private JMenu childMenu(final JMenu parent, final String name){
        for (int i = 0; i < parent.getItemCount(); i++){
            if (parent.getItem(i) instanceof JMenu child && child.getText().equals(name)){
                return child;
            }
        }
        final JMenu menu = newMenu(name);
        parent.add(menu);
        return menu;
    }

    private static JMenu newMenu(final String name){
        final JMenu menu = new JMenu(name);
        menu.setFont(menu.getFont().deriveFont(MENU_FONT_SIZE));
        return menu;
    }

    /**
     * Asks what went wrong, in a modal multi-line text box. EDT only.
     *
     * @return the description (possibly empty), or empty if cancelled
     */
    public Optional<String> askForIssueDescription(){
        final JTextArea description = new JTextArea(DESCRIPTION_ROWS, DESCRIPTION_COLUMNS);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        //JOptionPane focuses its OK button by default - put the cursor in the text box instead
        description.addAncestorListener(new AncestorListener(){
            @Override
            public void ancestorAdded(final AncestorEvent event){
                description.requestFocusInWindow();
            }

            @Override
            public void ancestorRemoved(final AncestorEvent event){
            }

            @Override
            public void ancestorMoved(final AncestorEvent event){
            }
        });
        final JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("Emulation is frozen. What went wrong?"), BorderLayout.NORTH);
        panel.add(new JScrollPane(description), BorderLayout.CENTER);
        final int choice = JOptionPane.showConfirmDialog(frame, panel, "Flag issue", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return choice == JOptionPane.OK_OPTION ? Optional.of(description.getText()) : Optional.empty();
    }

    /**
     * Asks for a file to open, in a modal chooser starting in {@code startIn} (or the working directory,
     * for {@code null}) and showing only files with one of {@code extensions}. EDT only.
     *
     * @return the chosen file, or empty if cancelled
     */
    public Optional<Path> chooseFileToOpen(final String title, final Path startIn, final String description,
                                           final String... extensions){
        final JFileChooser chooser = new JFileChooser(startIn == null ? null : startIn.toFile());
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
                ? Optional.of(chooser.getSelectedFile().toPath())
                : Optional.empty();
    }

    /**
     * Hands files dragged onto the window to {@code onDrop}, on the EDT. Any files are taken; deciding
     * which to use is the caller's.
     */
    public void onFilesDropped(final Consumer<List<Path>> onDrop){
        frame.setTransferHandler(new TransferHandler(){
            @Override
            public boolean canImport(final TransferSupport support){
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(final TransferSupport support){
                if (!canImport(support)){
                    return false;
                }
                try {
                    final List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    onDrop.accept(files.stream().map(file -> ((File) file).toPath()).toList());
                    return true;
                } catch (UnsupportedFlavorException | IOException e){
                    System.err.println("Couldn't read what was dropped: " + e.getMessage());
                    return false;
                }
            }
        });
    }

    /** A modal error message. EDT only. */
    public void showError(final String title, final String message){
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** Appends {@code status} to the window title (or clears it, for {@code null}) - a quiet, non-modal notice. EDT only. */
    public void showStatus(final String status){
        this.status = status;
        updateTitle();
    }

    /**
     * Names the game being played in the window title, clearing any status - or, for {@code null}, shows
     * that none is: the idle image, with the last game's frame cleared so the next doesn't flash it. EDT only.
     */
    public void showGame(final String gameName){
        this.gameName = gameName;
        this.status = null;
        idle = gameName == null;
        updateTitle();
        if (idle){
            final Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH_PX, HEIGHT_PX);
            g.dispose();
        }
        canvas.repaint();
    }

    /** Black, with {@link #IDLE_IMAGE} small in the middle. EDT only. */
    private void paintIdle(final Graphics2D g){
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        if (IDLE_IMAGE == null){
            return;
        }
        final int areaWidth = width / IDLE_IMAGE_FRACTION;
        final int areaHeight = height / IDLE_IMAGE_FRACTION;
        final int[] rect = CenteredScale.fit(IDLE_IMAGE.getWidth(), IDLE_IMAGE.getHeight(), areaWidth, areaHeight);
        if (rect[2] <= 0 || rect[3] <= 0){
            return;
        }
        g.drawImage(scaledIdleImage(rect[2], rect[3]), (width - areaWidth) / 2 + rect[0], (height - areaHeight) / 2 + rect[1], null);
    }

    /**
     * {@link #IDLE_IMAGE} at this size, area-averaged - a one-step bicubic shrink to a fraction of its size
     * drops detail, leaving the text jagged. Cached until the window is resized. EDT only.
     */
    private BufferedImage scaledIdleImage(final int width, final int height){
        if (scaledIdleImage == null || scaledIdleImage.getWidth() != width || scaledIdleImage.getHeight() != height){
            scaledIdleImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = scaledIdleImage.createGraphics();
            g.drawImage(IDLE_IMAGE.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
            g.dispose();
        }
        return scaledIdleImage;
    }

    private static BufferedImage readImage(final String resource){
        try (InputStream in = SwingVideoOutput.class.getResourceAsStream(resource)){
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e){
            System.err.println("Couldn't read " + resource + ": " + e.getMessage());
            return null;
        }
    }

    private void updateTitle(){
        final StringBuilder title = new StringBuilder(TITLE);
        if (gameName != null){
            title.append(" - ").append(gameName);
        }
        if (status != null){
            title.append(" - ").append(status);
        }
        frame.setTitle(title.toString());
    }

    @Override
    public void present(final int[] rgbFrame){
        final long now = System.nanoTime();
        produced.record(now);
        producedListener.accept(now);
        synchronized (frameHandoff){
            pendingFrame.set(rgbFrame);
            if (renderPending.compareAndSet(false, true)){
                SwingUtilities.invokeLater(this::renderPendingFrame);
            } else {
                skipped.incrementAndGet();
            }
        }
    }

    /**
     * The most recent complete frame handed to {@link #present} (a copy, packed {@code 0xRRGGBB}), or
     * {@code null} if none has been yet - unlike {@code PPU.rgbFramebuffer()} read mid-frame (e.g.
     * while paused), this is never part-drawn, so it's what a screenshot should show.
     */
    public int[] lastPresentedFrame(){
        final int[] frame = pendingFrame.get();
        return frame == null ? null : frame.clone();
    }

    private void renderPendingFrame(){
        final int[] frame;
        synchronized (frameHandoff){
            frame = pendingFrame.get();
            renderPending.set(false);
        }
        if (imageChanged){
            //the previous frame was never painted - its repaint was merged into this one
            skipped.incrementAndGet();
        }
        image.setRGB(0, 0, WIDTH_PX, HEIGHT_PX, frame, 0, WIDTH_PX);
        imageChanged = true;
        canvas.repaint();
    }

    /**
     * Also hands each frame's {@link System#nanoTime()} to {@code produced} (on the clock thread, as the
     * emulation finishes it) and to {@code shown} (on the EDT, once it has been painted) - e.g. for a live graph.
     */
    public void onFrameTimes(final LongConsumer produced, final LongConsumer shown){
        producedListener = produced;
        shownListener = shown;
    }

    /** EDT, once a second: the last second's frame timing under the game, in two lines. */
    private void refreshJitter(){
        final FrameJitter.Stats emu = produced.takeStats();
        final FrameJitter.Stats drawn = shown.takeStats();
        final int skippedFrames = skipped.getAndSet(0);
        jitterLabel.setText("<html>emu&nbsp;&nbsp; " + emu.describe().replace(" ", "&nbsp;")
                + "<br>shown " + drawn.describe().replace(" ", "&nbsp;") + "&nbsp;&nbsp;skipped " + skippedFrames + "</html>");
        if (LOG_JITTER){
            System.out.println("[frame jitter] emu " + emu.describe() + "  |  shown " + drawn.describe()
                    + "  skipped " + skippedFrames);
        }
    }

    /** Closes the window - the caller's responsibility, same as when to stop the emulation itself. */
    public void close(){
        SwingUtilities.invokeLater(() -> {
            //a running Timer keeps posting to the EDT, which would keep the JVM from exiting
            jitterTimer.stop();
            frame.dispose();
        });
    }
}
