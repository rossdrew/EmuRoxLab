package com.rox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ByteUtilTest {
    @Test
    public void toUnsignedGivesEachByteAs0To255(){
        assertArrayEquals(new int[]{0, 1, 0x7F, 0x80, 0xFF}, ByteUtil.toUnsigned(new byte[]{0, 1, 0x7F, (byte) 0x80, (byte) 0xFF}));
    }

    @Test
    public void toBytesKeepsTheLow8Bits(){
        assertArrayEquals(new byte[]{0, 1, 0x7F, (byte) 0x80, (byte) 0xFF}, ByteUtil.toBytes(new int[]{0, 1, 0x7F, 0x80, 0x1FF}));
    }

    @Test
    public void emptyStaysEmpty(){
        assertArrayEquals(new int[0], ByteUtil.toUnsigned(new byte[0]));
        assertArrayEquals(new byte[0], ByteUtil.toBytes(new int[0]));
    }
}
