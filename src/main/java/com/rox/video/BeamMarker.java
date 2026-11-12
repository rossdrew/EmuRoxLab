package com.rox.video;

import com.rox.debug.BeamPosition;

/**
 * Where to mark the PPU's beam on the 256x240 picture. Scanlines 0-239 are its rows and dots 1-256 its columns
 * (pixel x = dot - 1); anywhere else the beam is off the picture, so the marker sits on the edge it's past, labelled.
 *
 * @param x 0-255, the column (on an edge: the nearest)
 * @param y 0-239, the row (on an edge: the nearest)
 * @param edge which edge the beam is past, or {@code null} when it's on the picture
 * @param label what it's doing off the picture, e.g. {@code vblank, scanline 245}; {@code null} on the picture
 */
public record BeamMarker(int x, int y, Edge edge, String label) {
    public enum Edge {
        /** hblank: past the end of a visible line */
        RIGHT,
        /** post-render and vblank: past the last visible line */
        BOTTOM,
        /** pre-render: about to start the first visible line */
        TOP
    }

    private static final int LAST_VISIBLE_SCANLINE = 239;
    private static final int POST_RENDER_SCANLINE = 240;
    private static final int PRE_RENDER_SCANLINE = 261;
    private static final int LAST_VISIBLE_DOT = 256;
    private static final int WIDTH = 256;
    private static final int HEIGHT = 240;

    public static BeamMarker of(final BeamPosition beam){
        final int scanline = beam.scanline();
        final int dot = beam.dot();
        final int column = Math.clamp(dot - 1, 0, WIDTH - 1);
        if (scanline <= LAST_VISIBLE_SCANLINE){
            return dot >= 1 && dot <= LAST_VISIBLE_DOT
                    ? new BeamMarker(dot - 1, scanline, null, null)
                    : new BeamMarker(WIDTH - 1, scanline, Edge.RIGHT, "hblank, dot " + dot);
        }
        if (scanline == PRE_RENDER_SCANLINE){
            return new BeamMarker(column, 0, Edge.TOP, "pre-render, dot " + dot);
        }
        return new BeamMarker(column, HEIGHT - 1, Edge.BOTTOM,
                scanline == POST_RENDER_SCANLINE ? "post-render, dot " + dot : "vblank, scanline " + scanline);
    }

    public boolean onPicture(){
        return edge == null;
    }
}
