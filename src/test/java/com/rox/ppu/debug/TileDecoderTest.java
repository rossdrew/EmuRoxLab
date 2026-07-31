package com.rox.ppu.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TileDecoderTest {

    @Test
    public void allZeroTileDecodesToAllZeroPixels(){
        final int[][] pixels = TileDecoder.decode(new int[16]);

        for (final int[] row : pixels){
            assertArrayEquals(new int[]{0, 0, 0, 0, 0, 0, 0, 0}, row);
        }
    }

    @Test
    public void bothPlanesFullySetDecodesToAllThrees(){
        final int[] tile = new int[16];
        for (int i = 0; i < 16; i++){
            tile[i] = 0xFF;
        }

        final int[][] pixels = TileDecoder.decode(tile);

        for (final int[] row : pixels){
            assertArrayEquals(new int[]{3, 3, 3, 3, 3, 3, 3, 3}, row);
        }
    }

    @Test
    public void onlyLowPlaneSetDecodesToAllOnes(){
        final int[] tile = new int[16];
        for (int row = 0; row < 8; row++){
            tile[row] = 0xFF; //low plane
        }
        //high plane (bytes 8-15) left at 0

        final int[][] pixels = TileDecoder.decode(tile);

        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1, 1}, pixels[0]);
    }

    @Test
    public void onlyHighPlaneSetDecodesToAllTwos(){
        final int[] tile = new int[16];
        for (int row = 0; row < 8; row++){
            tile[8 + row] = 0xFF; //high plane
        }
        //low plane (bytes 0-7) left at 0

        final int[][] pixels = TileDecoder.decode(tile);

        assertArrayEquals(new int[]{2, 2, 2, 2, 2, 2, 2, 2}, pixels[0]);
    }

    @Test
    public void columnZeroIsTheRowBytesMostSignificantBitNotLeastSignificant(){
        final int[] tile = new int[16];
        tile[0] = 0b1000_0000; //low plane, row 0: only the MSB set

        final int[][] pixels = TileDecoder.decode(tile);

        assertEquals(1, pixels[0][0], "MSB of the row byte must decode to column 0");
        for (int col = 1; col < 8; col++){
            assertEquals(0, pixels[0][col], "every other column in this row should be 0");
        }
    }

    @Test
    public void alternatingBitsInLowPlaneOnlyDecodeToAlternatingOnesAndZerosMsbFirst(){
        final int[] tile = new int[16];
        tile[0] = 0b1010_1010; //low plane, row 0
        //high plane row 0 (tile[8]) left at 0, so decoded values are 0 or 1 only

        final int[][] pixels = TileDecoder.decode(tile);

        assertArrayEquals(new int[]{1, 0, 1, 0, 1, 0, 1, 0}, pixels[0]);
    }

    @Test
    public void eachRowIsDecodedIndependentlyFromItsOwnBytesNotTransposed(){
        final int[] tile = new int[16];
        tile[0] = 0xFF; //low plane row 0: all pixels get low bit 1
        tile[1] = 0x00; //low plane row 1: all pixels get low bit 0
        tile[8] = 0x00; //high plane row 0: all pixels get high bit 0
        tile[9] = 0xFF; //high plane row 1: all pixels get high bit 1

        final int[][] pixels = TileDecoder.decode(tile);

        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1, 1}, pixels[0], "row 0: low=1,high=0 -> pixel 1");
        assertArrayEquals(new int[]{2, 2, 2, 2, 2, 2, 2, 2}, pixels[1], "row 1: low=0,high=1 -> pixel 2");
    }
}
