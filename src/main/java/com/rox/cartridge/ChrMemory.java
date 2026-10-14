package com.rox.cartridge;

import com.rox.ByteUtil;

import static com.rox.ByteUtil.BYTE_MASK;

/**
 * A board's CHR memory, by offset:
 * <ul>
 * <li>CHR-ROM if the ROM file has any - read-only, writes are dropped as on real hardware</li>
 * <li>otherwise CHR-RAM, which games fill with their own tiles</li>
 * </ul>
 * Mapping a PPU address to an offset (banking) is left to each mapper.
 */
final class ChrMemory {
    private static final String WHAT = "CHR-RAM";

    private final int[] bytes;
    private final boolean ram;

    private ChrMemory(final int[] bytes, final boolean ram){
        this.bytes = bytes;
        this.ram = ram;
    }

    /** @param ramSize how much CHR-RAM a board gets when the ROM file has no CHR-ROM */
    static ChrMemory of(final INesRom rom, final int ramSize){
        final byte[] romChr = rom.chrRom();
        return romChr.length > 0
                ? new ChrMemory(ByteUtil.toUnsigned(romChr), false)
                : new ChrMemory(new int[ramSize], true);
    }

    int read(final int offset){
        return bytes[offset];
    }

    /** A no-op on CHR-ROM. */
    void write(final int offset, final int value){
        if (ram){
            bytes[offset] = value & BYTE_MASK;
        }
    }

    boolean isRam(){
        return ram;
    }

    int size(){
        return bytes.length;
    }

    /** The CHR-RAM, or empty for CHR-ROM - it comes back from the ROM file, so there's nothing to save. */
    int[] snapshot(){
        return SnapshotArrays.copyOf(ramOrNull());
    }

    /** @throws IllegalArgumentException if {@code saved} doesn't fit, i.e. it's from a different board */
    void checkRestorable(final int[] saved){
        SnapshotArrays.requireSameSize(saved, ramOrNull(), WHAT);
    }

    void restore(final int[] saved){
        SnapshotArrays.copy(saved, ramOrNull());
    }

    /** {@link SnapshotArrays}' "this board has none". */
    private int[] ramOrNull(){
        return ram ? bytes : null;
    }
}
