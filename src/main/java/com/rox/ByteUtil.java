package com.rox;

public final class ByteUtil {
    /** Mask to remove any digits that don't represent a byte, i.e. the 8 least significant bits */
    public static final int BYTE_MASK = 0xFF;
    /** Mask to remove any digits that don't represent a word, i.e. the 16 least significant bits */
    public static final int WORD_MASK = 0xFFFF;

    private ByteUtil(){
    }
}
