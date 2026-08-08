package com.rox.ppu;

import static com.rox.ByteUtil.*;

/**
 * bit 0: grayscale mode
 * bit 1: show background in the leftmost 8 pixels
 * bit 2: show sprites in the leftmost 8 pixels
 * bit 3: show background
 * bit 4: show sprites
 * bits 5-7: color emphasis (red/green/blue)
 */
public class PPUMaskRegister {
    private static final int SHOW_BACKGROUND_LEFT_BIT = 0x02; //PPUMASK bit 1
    private static final int SHOW_BACKGROUND_BIT = 0x08;      //PPUMASK bit 3
    private static final int SHOW_SPRITES_BIT = 0x10;         //PPUMASK bit 4

    private static final int RENDERING_ENABLED_MASK = SHOW_BACKGROUND_BIT | SHOW_SPRITES_BIT;

    private final int value;

    PPUMaskRegister(final int registerValue){
        value = registerValue & BYTE_MASK;
    }

    public boolean showBackground(){
        return (value & SHOW_BACKGROUND_BIT) != 0;
    }

    public boolean showBackgroundLeft(){
        return (value & SHOW_BACKGROUND_LEFT_BIT) != 0;
    }

    public boolean showSprites(){
        return (value & SHOW_SPRITES_BIT) != 0;
    }

    public boolean renderingEnabled(){
        return (value & RENDERING_ENABLED_MASK) != 0;
    }

    public int rawValue(){
        return value;
    }
}
