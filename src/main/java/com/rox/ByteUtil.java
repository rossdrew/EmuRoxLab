package com.rox;

public final class ByteUtil {
    /** Mask to remove any digits that don't represent a byte, i.e. the 8 least significant bits */
    public static final int BYTE_MASK = 0xFF;
    /** Mask to remove any digits that don't represent a word, i.e. the 16 least significant bits */
    public static final int WORD_MASK = 0xFFFF;

    public static int[] bitMap = new int[] {
            0b0000000000000001, //Byte
            0b0000000000000010,
            0b0000000000000100,
            0b0000000000001000,
            0b0000000000010000,
            0b0000000000100000,
            0b0000000001000000,
            0b0000000010000000,
            0b0000000100000000, //Word
            0b0000001000000000,
            0b0000010000000000,
            0b0000100000000000,
            0b0001000000000000,
            0b0010000000000000,
            0b0100000000000000,
            0b1000000000000000
    };

    private ByteUtil(){
    }

    public static boolean isBitSet(final int bitIndex, final int value) {
        return (bitMap[bitIndex] & value) != 0;
    }

    public static int withBitSet(final int value, final int bitIndex) {
        return value | bitMap[bitIndex];
    }
}
