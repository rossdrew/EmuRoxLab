package com.rox.video;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private final BufferedImage image = new BufferedImage(WIDTH_PX, HEIGHT_PX, BufferedImage.TYPE_INT_RGB);
    private final JPanel canvas = new JPanel(){
        @Override
        protected void paintComponent(final Graphics g){
            super.paintComponent(g);
            final int[] rect = CenteredScale.fit(WIDTH_PX, HEIGHT_PX, getWidth(), getHeight());
            final Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(image, rect[0], rect[1], rect[2], rect[3], null);
        }
    };
    private final JFrame frame = new JFrame("EmuRoxLab");
    //present() is called once per emulated frame (~60/sec) from the clock thread, far faster than a
    //loaded EDT can necessarily keep up with - rather than queuing one invokeLater (and one retained
    //rgbFrame array) per call, which would grow unboundedly under any EDT backpressure, only the
    //latest frame is kept and at most one render task is ever pending at a time
    private final AtomicReference<int[]> pendingFrame = new AtomicReference<>();
    private final AtomicBoolean renderPending = new AtomicBoolean(false);

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
        frame.getContentPane().add(canvas);
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

    @Override
    public void present(final int[] rgbFrame){
        pendingFrame.set(rgbFrame);
        if (renderPending.compareAndSet(false, true)){
            SwingUtilities.invokeLater(this::renderPendingFrame);
        }
    }

    private void renderPendingFrame(){
        renderPending.set(false);
        image.setRGB(0, 0, WIDTH_PX, HEIGHT_PX, pendingFrame.get(), 0, WIDTH_PX);
        canvas.repaint();
    }

    /** Closes the window - the caller's responsibility, same as when to stop the emulation itself. */
    public void close(){
        SwingUtilities.invokeLater(frame::dispose);
    }
}
