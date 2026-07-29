package com.rox.cartridge;

import java.util.Arrays;

/**
 * A parsed iNES (".nes") ROM file: the 16-byte header plus the PRG-ROM/CHR-ROM bytes it describes.
 * Doesn't know anything about mapper banking behaviour - that's {@link Mapper}'s job, built from the
 * data here.
 */
public final class INesRom {
    private static final int HEADER_SIZE = 16;
    private static final int TRAINER_SIZE = 512;
    private static final int PRG_BANK_SIZE = 16384;
    private static final int CHR_BANK_SIZE = 8192;

    private static final int PRG_BANKS_OFFSET = 4;
    private static final int CHR_BANKS_OFFSET = 5;
    private static final int FLAGS_6_OFFSET = 6;
    private static final int FLAGS_7_OFFSET = 7;

    private static final int TRAINER_BIT = 0x04;
    private static final int VERTICAL_MIRRORING_BIT = 0x01;
    private static final int MAPPER_LOW_NIBBLE_SHIFT = 4;
    private static final int MAPPER_HIGH_NIBBLE_MASK = 0xF0;
    private static final int NES2_IDENTIFICATION_MASK = 0x0C;
    private static final int NES2_IDENTIFICATION_VALUE = 0x08;

    private final int mapperNumber;
    private final boolean verticalMirroring;
    private final byte[] prgRom;
    private final byte[] chrRom;

    private INesRom(final int mapperNumber, final boolean verticalMirroring, final byte[] prgRom, final byte[] chrRom){
        this.mapperNumber = mapperNumber;
        this.verticalMirroring = verticalMirroring;
        this.prgRom = prgRom;
        this.chrRom = chrRom;
    }

    /** Parse the raw bytes of a ".nes" file. */
    public static INesRom parse(final byte[] fileBytes){
        if (fileBytes.length < HEADER_SIZE || !hasMagic(fileBytes)){
            throw new IllegalArgumentException("Not an iNES ROM (missing 'NES' + $1A header magic)");
        }

        final int flags7 = fileBytes[FLAGS_7_OFFSET] & 0xFF;
        if ((flags7 & NES2_IDENTIFICATION_MASK) == NES2_IDENTIFICATION_VALUE){
            throw new IllegalArgumentException("NES 2.0 ROMs are not supported yet - flags7 identifies this as NES 2.0");
        }

        final int prgBanks = fileBytes[PRG_BANKS_OFFSET] & 0xFF;
        final int chrBanks = fileBytes[CHR_BANKS_OFFSET] & 0xFF;
        final int flags6 = fileBytes[FLAGS_6_OFFSET] & 0xFF;

        final int mapperNumber = (flags7 & MAPPER_HIGH_NIBBLE_MASK) | (flags6 >> MAPPER_LOW_NIBBLE_SHIFT);
        final boolean verticalMirroring = (flags6 & VERTICAL_MIRRORING_BIT) != 0;
        final boolean hasTrainer = (flags6 & TRAINER_BIT) != 0;

        int offset = HEADER_SIZE + (hasTrainer ? TRAINER_SIZE : 0);

        final int prgSize = prgBanks * PRG_BANK_SIZE;
        final byte[] prgRom = copyRange(fileBytes, offset, prgSize, "PRG-ROM");
        offset += prgSize;

        final int chrSize = chrBanks * CHR_BANK_SIZE;
        final byte[] chrRom = copyRange(fileBytes, offset, chrSize, "CHR-ROM");

        return new INesRom(mapperNumber, verticalMirroring, prgRom, chrRom);
    }

    private static boolean hasMagic(final byte[] fileBytes){
        return fileBytes[0] == 'N' && fileBytes[1] == 'E' && fileBytes[2] == 'S' && fileBytes[3] == 0x1A;
    }

    private static byte[] copyRange(final byte[] fileBytes, final int offset, final int length, final String label){
        if (offset + length > fileBytes.length){
            throw new IllegalArgumentException("Truncated iNES ROM: expected " + length + " bytes of " + label
                    + " at offset " + offset + ", file is only " + fileBytes.length + " bytes");
        }
        return Arrays.copyOfRange(fileBytes, offset, offset + length);
    }

    public int mapperNumber(){
        return mapperNumber;
    }

    public boolean isVerticalMirroring(){
        return verticalMirroring;
    }

    /** Defensive copy - callers must not be able to mutate ROM content behind PRG-ROM's back. */
    public byte[] prgRom(){
        return Arrays.copyOf(prgRom, prgRom.length);
    }

    /** Defensive copy - callers must not be able to mutate ROM content behind CHR-ROM's back. */
    public byte[] chrRom(){
        return Arrays.copyOf(chrRom, chrRom.length);
    }
}
