package com.rox.ppu;

import static com.rox.ByteUtil.BYTE_MASK;

/**
 * VPHB SINN
 * V (bit 7): NMI enable
 * P (bit 6): PPU master/slave select - unmodeled
 * H (bit 5): sprite size (0=8x8, 1=8x16)
 * B (bit 4): background pattern table select (0=$0000, 1=$1000)
 * S (bit 3): sprite pattern table select for 8x8 sprites - ignored in 8x16 mode, where the tile
 * index's own low bit selects the table instead (0=$0000, 1=$1000)
 * I (bit 2): VRAM address increment per $2007 access (0=+1, 1=+32)
 * NN (bits 1-0): base nametable select (0-3)
 */
public class PPUControlRegister {
    private static final int NMI_ENABLE_BIT = 0x80;
    private static final int SPRITE_SIZE_BIT = 0x20;
    private static final int BACKGROUND_PATTERN_TABLE_SELECT_BIT = 0x10;
    private static final int SPRITE_PATTERN_TABLE_SELECT_BIT = 0x08;
    private static final int VRAM_INCREMENT_BIT = 0x04;
    private static final int NAMETABLE_SELECT_MASK = 0x03;

    private static final int PATTERN_TABLE_SIZE = 0x1000;
    private static final int VRAM_INCREMENT_32 = 32;
    private static final int VRAM_INCREMENT_1 = 1;

    private final int value;

    PPUControlRegister(final int registerValue){
        value = registerValue & BYTE_MASK;
    }

    public boolean nmiEnabled(){
        return (value & NMI_ENABLE_BIT) != 0;
    }

    public boolean tallSprites(){
        return (value & SPRITE_SIZE_BIT) != 0;
    }

    public int backgroundPatternTableBase(){
        return (value & BACKGROUND_PATTERN_TABLE_SELECT_BIT) != 0 ? PATTERN_TABLE_SIZE : 0;
    }

    /** Only meaningful for 8x8 sprites - 8x16 sprites pick their table from the tile index's own low bit instead. */
    public int spritePatternTableBase(){
        return (value & SPRITE_PATTERN_TABLE_SELECT_BIT) != 0 ? PATTERN_TABLE_SIZE : 0;
    }

    public int vramIncrement(){
        return (value & VRAM_INCREMENT_BIT) != 0 ? VRAM_INCREMENT_32 : VRAM_INCREMENT_1;
    }

    public int nametableSelect(){
        return value & NAMETABLE_SELECT_MASK;
    }

    public int rawValue(){
        return value;
    }
}
