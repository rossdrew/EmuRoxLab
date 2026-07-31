package com.rox.cartridge;

/**
 * iNES mapper 0 (NROM): PRG-ROM is fixed at {@code $8000-$FFFF} with no bank registers - a 16KB ROM
 * is mirrored into both halves of that window, a 32KB ROM fills it directly. Writes to {@code $8000+}
 * are no-ops (real NROM boards have nothing to switch). Always backed by 8KB of PRG-RAM at
 * {@code $6000-$7FFF} - not universal on real NROM boards, but needed here since that's exactly
 * where blargg's test ROMs report their pass/fail status.
 *
 * CHR is a single fixed 8KB bank, no banking registers to switch it (same "nothing to switch"
 * simplicity as PRG) - CHR-ROM if the ROM has any, else CHR-RAM (writable, for games that generate
 * their own tiles). Mirroring is whatever the iNES header says and never changes - NROM boards have
 * no register to override it, unlike MMC1.
 */
public final class NromMapper implements Mapper {
    private static final int PRG_RAM_SIZE = 0x2000;
    private static final int PRG_ROM_START_ADDRESS = 0x8000;
    private static final int SINGLE_BANK_SIZE = 0x4000;
    private static final int DOUBLE_BANK_SIZE = SINGLE_BANK_SIZE * 2;
    private static final int CHR_SIZE = 0x2000;
    private static final int BYTE_MASK = 0xFF;

    private final byte[] prgRom;
    private final int[] prgRam = new int[PRG_RAM_SIZE];
    private final byte[] chrRom;
    private final int[] chrRam;
    private final Mirroring mirroring;

    public NromMapper(final INesRom rom){
        this.prgRom = rom.prgRom();
        if (prgRom.length != SINGLE_BANK_SIZE && prgRom.length != DOUBLE_BANK_SIZE){
            throw new IllegalArgumentException(
                    "NROM expects 16KB or 32KB PRG-ROM, got " + prgRom.length + " bytes");
        }
        final byte[] romChr = rom.chrRom();
        this.chrRom = romChr.length > 0 ? romChr : null;
        this.chrRam = romChr.length > 0 ? null : new int[CHR_SIZE];
        this.mirroring = rom.isVerticalMirroring() ? Mirroring.VERTICAL : Mirroring.HORIZONTAL;
    }

    /**
     * Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see class Javadoc).
     * PRG-ROM mirroring (16KB banks repeating to fill the 32KB window) falls out for free from
     * masking to {@code prgRom.length - 1} - both bank sizes are powers of two, same trick
     * {@link com.rox.mem.RAM} uses for its own address wraparound.
     */
    @Override
    public int read(final int address){
        if (address >= PRG_ROM_START_ADDRESS){
            return prgRom[address & (prgRom.length - 1)] & BYTE_MASK;
        }
        return prgRam[address & (PRG_RAM_SIZE - 1)];
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see class Javadoc). */
    @Override
    public void write(final int address, final int value){
        if (address < PRG_ROM_START_ADDRESS){
            prgRam[address & (PRG_RAM_SIZE - 1)] = value & BYTE_MASK;
        }
    }

    @Override
    public int readChr(final int address){
        final int offset = address & (CHR_SIZE - 1);
        return chrRom != null ? chrRom[offset] & BYTE_MASK : chrRam[offset];
    }

    /** No-op on CHR-ROM boards - real hardware has nowhere to write CHR-ROM writes to. */
    @Override
    public void writeChr(final int address, final int value){
        if (chrRam != null){
            chrRam[address & (CHR_SIZE - 1)] = value & BYTE_MASK;
        }
    }

    @Override
    public Mirroring nametableMirroring(){
        return mirroring;
    }
}
