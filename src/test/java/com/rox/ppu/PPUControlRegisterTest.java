package com.rox.ppu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PPUControlRegisterTest {

    @Test
    public void nmiEnabledReflectsBitSeven(){
        assertTrue(new PPUControlRegister(0x80).nmiEnabled());
        assertFalse(new PPUControlRegister(0x00).nmiEnabled());
    }

    @Test
    public void tallSpritesReflectsBitFive(){
        assertTrue(new PPUControlRegister(0x20).tallSprites());
        assertFalse(new PPUControlRegister(0x00).tallSprites());
    }

    @Test
    public void backgroundPatternTableBaseSelectsBetweenZeroAndOneThousand(){
        assertEquals(0x1000, new PPUControlRegister(0x10).backgroundPatternTableBase());
        assertEquals(0x0000, new PPUControlRegister(0x00).backgroundPatternTableBase());
    }

    @Test
    public void spritePatternTableBaseSelectsBetweenZeroAndOneThousand(){
        assertEquals(0x1000, new PPUControlRegister(0x08).spritePatternTableBase());
        assertEquals(0x0000, new PPUControlRegister(0x00).spritePatternTableBase());
    }

    @Test
    public void vramIncrementIsThirtyTwoWhenBitSetOtherwiseOne(){
        assertEquals(32, new PPUControlRegister(0x04).vramIncrement());
        assertEquals(1, new PPUControlRegister(0x00).vramIncrement());
    }

    @Test
    public void nametableSelectReflectsTheLowTwoBitsOnly(){
        assertEquals(0, new PPUControlRegister(0x00).nametableSelect());
        assertEquals(3, new PPUControlRegister(0x03).nametableSelect());
        assertEquals(1, new PPUControlRegister(0xFD).nametableSelect()); //0xFD = 1111 1101 -> low 2 bits 01
    }

    @Test
    public void rawValueReturnsExactlyWhatWasConstructedWith(){
        assertEquals(0x55, new PPUControlRegister(0x55).rawValue());
    }

    @Test
    public void constructorMasksToASingleByte(){
        assertEquals(0xFF, new PPUControlRegister(0x1FF).rawValue());
    }
}
