package com.rox.ppu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NesPaletteTest {
    @Test
    public void indexZeroIsTheMidGreyReferenceColour(){
        assertEquals(0x666666, NesPalette.rgb(0x00));
    }

    @Test
    public void indexTwentyIsTheBrightWhiteReferenceColour(){
        assertEquals(0xFFFEFF, NesPalette.rgb(0x20));
    }

    @Test
    public void indexThirteenIsAnUnusedBlackEntry(){
        assertEquals(0x000000, NesPalette.rgb(0x0D));
    }

    @Test
    public void indexThreeDIsTheLightestGreyReferenceColour(){
        assertEquals(0xB8B8B8, NesPalette.rgb(0x3D));
    }

    @Test
    public void indexThreeFIsAnUnusedBlackEntry(){
        assertEquals(0x000000, NesPalette.rgb(0x3F));
    }

    @Test
    public void indexIsMaskedToSixBitsSoOutOfRangeValuesWrap(){
        assertEquals(NesPalette.rgb(0x00), NesPalette.rgb(0x40), "0x40 wraps to 0x00 via the 6-bit mask");
        assertEquals(NesPalette.rgb(0x05), NesPalette.rgb(0xC5), "high garbage bits above bit 5 must be masked off");
    }

    @Test
    public void everyEntryIsAValidTwentyFourBitColour(){
        for (int index = 0; index < 64; index++){
            final int rgb = NesPalette.rgb(index);
            assertEquals(rgb, rgb & 0xFFFFFF, "entry " + index + " must not set bits above the 24-bit RGB range");
        }
    }
}
