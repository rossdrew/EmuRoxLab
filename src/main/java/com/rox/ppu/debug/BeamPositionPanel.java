package com.rox.ppu.debug;

import com.rox.ppu.PPU;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static com.rox.ppu.PPU.FrameTiming.DOTS_PER_SCANLINE;
import static com.rox.ppu.PPU.FrameTiming.SCANLINES_PER_FRAME;

/**
 * A single-dot "where is the beam right now" indicator: plots the PPU's current (dot, scanline)
 * position within a native 341x262 coordinate space (the full dot/scanline range, matching
 * {@code PPU.FrameTiming.DOTS_PER_SCANLINE}/{@code SCANLINES_PER_FRAME}, redrawn fresh from the current
 * instantaneous state each repaint - no history/sampling needed, unlike a scrolling chart.
 * Background flips white during vblank so the vblank window is visible at a glance.
 */
final class BeamPositionPanel extends JPanel {
    private static final int DOT_RADIUS = 4;
    private static final Color BEAM_COLOR = Color.GREEN;

    private final PPU ppu;

    BeamPositionPanel(final PPU ppu){
        this.ppu = ppu;
        setPreferredSize(new Dimension(200, 80));
    }

    @Override
    protected void paintComponent(final Graphics g){
        super.paintComponent(g);
        ScaledImageDrawer.drawCentered(g, render(), getWidth(), getHeight());
    }

    private BufferedImage render(){
        final BufferedImage beamStateImage = new BufferedImage(DOTS_PER_SCANLINE, SCANLINES_PER_FRAME, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g2 = beamStateImage.createGraphics();
        //Background
        g2.setColor(ppu.vblankFlag() ? Color.WHITE : Color.BLACK);
        g2.fillRect(0, 0, DOTS_PER_SCANLINE, SCANLINES_PER_FRAME);
        //Beam
        g2.setColor(BEAM_COLOR);
        final int x = ppu.dot();
        final int y = ppu.scanline();
        g2.fillOval(x - DOT_RADIUS, y - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
        g2.dispose();
        return beamStateImage;
    }
}
