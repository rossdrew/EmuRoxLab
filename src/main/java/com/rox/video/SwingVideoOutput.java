package com.rox.video;

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
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

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

    private final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
    private final JPanel canvas = new JPanel(){
        @Override
        protected void paintComponent(final Graphics g){
            super.paintComponent(g);
            final int[] rect = CenteredScale.fit(WIDTH_PX, HEIGHT_PX, getWidth(), getHeight());
            final Graphics2D g2 = (Graphics2D) g;
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

    /** A modal error message. EDT only. */
    public void showError(final String title, final String message){
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** Appends {@code status} to the window title (or clears it, for {@code null}) - a quiet, non-modal notice. EDT only. */
    public void showStatus(final String status){
        frame.setTitle(status == null ? TITLE : TITLE + " - " + status);
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
