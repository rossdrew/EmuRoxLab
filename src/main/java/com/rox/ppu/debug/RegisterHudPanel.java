package com.rox.ppu.debug;

import com.rox.ppu.PPU;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Font;

/** Plain-text dump of the PPU's current timing/register state, refreshed by {@link PpuDebugFrame}'s timer. */
final class RegisterHudPanel extends JPanel {
    private static final int NMI_ENABLE_BIT = 0x80;
    private static final int VRAM_INCREMENT_BIT = 0x04;
    private static final int NAMETABLE_SELECT_MASK = 0x03;

    private final PPU ppu;
    private final JTextArea text = new JTextArea();

    RegisterHudPanel(final PPU ppu){
        this.ppu = ppu;
        setLayout(new BorderLayout());
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(text, BorderLayout.CENTER);
        refresh();
    }

    void refresh(){
        final int control = ppu.controlRegister();
        text.setText(String.format(
                """
                scanline:  %d
                dot:       %d
                vblank:    %s

                PPUCTRL:   $%02X (NMI enable=%s, VRAM +%d, nametable select=%d)
                PPUMASK:   $%02X

                scroll X:  %d
                scroll Y:  %d
                OAM addr:  $%02X
                VRAM addr: $%04X
                """,
                ppu.scanline(), ppu.dot(), ppu.vblankFlag(),
                control, (control & NMI_ENABLE_BIT) != 0, (control & VRAM_INCREMENT_BIT) != 0 ? 32 : 1,
                control & NAMETABLE_SELECT_MASK,
                ppu.maskRegister(),
                ppu.scrollX(), ppu.scrollY(), ppu.oamAddress(), ppu.vramAddress()
        ));
    }
}
