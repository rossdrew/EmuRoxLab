package com.rox.ppu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PPUMaskRegisterTest {

    @Test
    public void showBackgroundReflectsBitThree(){
        assertTrue(new PPUMaskRegister(0x08).showBackground());
        assertFalse(new PPUMaskRegister(0x00).showBackground());
    }

    @Test
    public void showBackgroundLeftReflectsBitOne(){
        assertTrue(new PPUMaskRegister(0x02).showBackgroundLeft());
        assertFalse(new PPUMaskRegister(0x00).showBackgroundLeft());
    }

    @Test
    public void showSpritesReflectsBitFour(){
        assertTrue(new PPUMaskRegister(0x10).showSprites());
        assertFalse(new PPUMaskRegister(0x00).showSprites());
    }

    @Test
    public void renderingEnabledIsFalseWhenBothBackgroundAndSpritesAreOff(){
        assertFalse(new PPUMaskRegister(0x00).renderingEnabled());
    }

    @Test
    public void renderingEnabledIsTrueWhenOnlyBackgroundIsOn(){
        assertTrue(new PPUMaskRegister(0x08).renderingEnabled());
    }

    @Test
    public void renderingEnabledIsTrueWhenOnlySpritesAreOn(){
        assertTrue(new PPUMaskRegister(0x10).renderingEnabled());
    }

    @Test
    public void rawValueReturnsExactlyWhatWasConstructedWith(){
        assertEquals(0x1E, new PPUMaskRegister(0x1E).rawValue());
    }

    @Test
    public void constructorMasksToASingleByte(){
        assertEquals(0xFF, new PPUMaskRegister(0x1FF).rawValue());
    }
}
