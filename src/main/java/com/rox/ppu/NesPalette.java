package com.rox.ppu;

/**
 * The 2C02 PPU's fixed 64-entry NTSC master palette: packed {@code 0xRRGGBB} values, indexed by the
 * raw 6-bit palette-RAM index (0-63) that {@link PPU#framebuffer()}/{@link PPU#paletteSnapshot()}
 * entries carry. Real hardware outputs analog NTSC composite video, not RGB, so any RGB table here is
 * necessarily a lookalike approximation - this is the reference table from nesdev's PPU_palettes wiki
 * page, the one most emulators converge on. Columns 0x0D-0x0F/0x1D-0x1F/0x2D-0x2F/0x3D-0x3F are unused
 * "black" duplicates on real hardware (0x0D itself is actually a sync-signal-adjacent "blacker than
 * black" that can damage some CRTs if displayed - harmless here, rendered as plain black).
 */
public final class NesPalette {
    private static final int[] RGB = {
            0x666666, 0x002A88, 0x1412A7, 0x3B00A4, 0x5C007E, 0x6E0040, 0x6C0600, 0x561D00,
            0x333500, 0x0B4800, 0x005200, 0x004F08, 0x00404D, 0x000000, 0x000000, 0x000000,
            0xADADAD, 0x155FD9, 0x4240FF, 0x7527FE, 0xA01ACC, 0xB71E7B, 0xB53120, 0x994E00,
            0x6B6D00, 0x388700, 0x0C9300, 0x008F32, 0x007C8D, 0x000000, 0x000000, 0x000000,
            0xFFFEFF, 0x64B0FF, 0x9290FF, 0xC676FF, 0xF36AFF, 0xFE6ECC, 0xFE8170, 0xEA9E22,
            0xBCBE00, 0x88D800, 0x5CE430, 0x45E082, 0x48CDDE, 0x4F4F4F, 0x000000, 0x000000,
            0xFFFEFF, 0xC0DFFF, 0xD3D2FF, 0xE8C8FF, 0xFBC2FF, 0xFEC4EA, 0xFECCC5, 0xF7D8A5,
            0xE4E594, 0xCFEF96, 0xBDF4AB, 0xB3F3CC, 0xB5EBF2, 0xB8B8B8, 0x000000, 0x000000,
    };

    private static final int PALETTE_INDEX_MASK = 0x3F;

    private NesPalette(){
    }

    /** @param paletteIndex a raw palette-RAM value, masked to 6 bits; @return the packed {@code 0xRRGGBB} colour. */
    public static int rgb(final int paletteIndex){
        return RGB[paletteIndex & PALETTE_INDEX_MASK];
    }
}
