package com.rox.cartridge.debug;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.INesRom;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.Map;

/**
 * Plain-text dump of a loaded ROM's header metadata (mapper, PRG/CHR size, mirroring, battery,
 * trainer) plus whatever bank/IRQ state {@link Cartridge#debugState()} reports for the current
 * mapper. This project is NTSC-hardcoded throughout (no PAL support), and the iNES 1.0 header has no
 * reliable region field of its own, so region is shown as a fixed label rather than parsed.
 */
final class RomInfoPanel extends JPanel {
    private static final int TEXT_COLUMNS = 50;
    //7 header lines (mapper/PRG/CHR/mirroring/battery/trainer/region) + 1 blank separator +
    //1 mapper-state heading line, plus one row per debugState() entry appended below it
    private static final int FIXED_ROW_COUNT = 9;

    private final Cartridge cartridge;
    private final JTextArea text;

    RomInfoPanel(final Cartridge cartridge){
        this.cartridge = cartridge;
        this.text = new JTextArea(FIXED_ROW_COUNT + cartridge.debugState().size(), TEXT_COLUMNS);
        setLayout(new BorderLayout());
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 32));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(text, BorderLayout.CENTER);
        refresh();
    }

    void refresh(){
        final INesRom rom = cartridge.rom();

        final StringBuilder mapperState = new StringBuilder();
        final Map<String, String> debugState = cartridge.debugState();
        if (debugState.isEmpty()){
            mapperState.append("(fixed mapping, no bank registers)");
        } else {
            mapperState.append("--- Mapper state ---\n");
            debugState.forEach((key, value) -> mapperState.append(String.format("%-14s%s%n", key + ":", value)));
        }

        text.setText(String.format(
                """
                Mapper:     %d (%s)
                PRG-ROM:    %d KB
                CHR-ROM:    %d KB
                Mirroring:  %s
                Battery:    %s
                Trainer:    %s
                Region:     NTSC (hardcoded)

                %s
                """,
                rom.mapperNumber(), MapperNames.nameOf(rom.mapperNumber()),
                rom.prgRom().length / 1024, rom.chrRom().length / 1024,
                cartridge.nametableMirroring(), rom.hasBattery(), rom.hasTrainer(),
                mapperState
        ));
    }
}
