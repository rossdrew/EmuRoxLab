package com.rox.video;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The actual "screen" a player would watch, as opposed to the {@code com.rox.ppu.debug} package's
 * inspection panels - a plain {@link JFrame} blitting each presented frame at a fixed integer scale,
 * nearest-neighbour interpolated (crisp pixel art, not blurred). {@link #present} is called from the
 * emulation's clock thread (see {@code NES}'s video-output clock listener), never the EDT, so every
 * Swing interaction there is marshalled via {@link SwingUtilities#invokeLater} rather than touched
 * directly - only construction is the caller's responsibility to run on the EDT (matching this
 * package's sibling {@code PpuDebugFrame}'s own convention), since it happens once, not per frame.
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
            final Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    };
    private final JFrame frame = new JFrame("EmuRoxLab");

    public SwingVideoOutput(){
        canvas.setPreferredSize(new Dimension(WIDTH_PX * SCALE, HEIGHT_PX * SCALE));
        frame.getContentPane().add(canvas);
        frame.pack();
        frame.setResizable(false); //setPreferredSize is only a packing hint - without this the user can freely resize away from the fixed scale
        frame.setVisible(true);
    }

    @Override
    public void present(final int[] rgbFrame){
        SwingUtilities.invokeLater(() -> {
            image.setRGB(0, 0, WIDTH_PX, HEIGHT_PX, rgbFrame, 0, WIDTH_PX);
            canvas.repaint();
        });
    }

    /** Closes the window - the caller's responsibility, same as when to stop the emulation itself. */
    public void close(){
        SwingUtilities.invokeLater(frame::dispose);
    }
}
