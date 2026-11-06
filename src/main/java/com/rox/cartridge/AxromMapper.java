package com.rox.cartridge;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.rox.ByteUtil.BYTE_MASK;

/**
 * iNES mapper 7 (AxROM - Rare's boards, e.g. Battletoads). One register, written anywhere in
 * {@code $8000-$FFFF} (nesdev, https://www.nesdev.org/wiki/AxROM):
 * <pre>
 * 7  bit  0
 * ---- ----
 * xxxM xPPP
 *    |  |||
 *    |  +++- Select 32 KB PRG ROM bank for CPU $8000-$FFFF
 *    +------ Select 1 KB VRAM page for all 4 nametables
 * </pre>
 * M = 0 is the lower page, M = 1 the upper. 8KB of CHR-RAM, unbanked; no PRG-RAM, so
 * {@code $6000-$7FFF} reads 0 and ignores writes.
 *
 * Bus conflicts aren't modeled - nesdev: "Submapper 0: Bus conflict behavior undefined, but emulators
 * shouldn't emulate bus conflicts". Bank 0 is mapped at power on; nesdev gives no initial state, and
 * Rare's games don't rely on one.
 */
public final class AxromMapper implements Mapper {
    private static final int PRG_ROM_START_ADDRESS = 0x8000;
    private static final int PRG_BANK_SIZE = 0x8000;
    private static final int PRG_BANK_MASK = 0x07;
    private static final int NAMETABLE_PAGE_BIT = 0x10;
    private static final int CHR_SIZE = 0x2000;
    private static final int[] NO_PRG_RAM = new int[0];

    private final byte[] prgRom;
    private final int prgBankCount;
    private final ChrMemory chr;

    /** last value written to $8000-$FFFF: PRG bank (bits 0-2), nametable page (bit 4) */
    private int bankSelect;

    public AxromMapper(final INesRom rom){
        this.prgRom = rom.prgRom();
        if (prgRom.length == 0 || prgRom.length % PRG_BANK_SIZE != 0){
            throw new IllegalArgumentException("AxROM expects PRG-ROM in whole 32KB banks, got " + prgRom.length + " bytes");
        }
        this.prgBankCount = prgRom.length / PRG_BANK_SIZE;
        this.chr = ChrMemory.of(rom, CHR_SIZE);
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see {@link Mapper}'s class doc). */
    @Override
    public int read(final int address){
        if (address < PRG_ROM_START_ADDRESS){
            return 0;
        }
        return prgRom[prgBank() * PRG_BANK_SIZE + (address - PRG_ROM_START_ADDRESS)] & BYTE_MASK;
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see {@link Mapper}'s class doc). */
    @Override
    public void write(final int address, final int value){
        if (address >= PRG_ROM_START_ADDRESS){
            bankSelect = value & BYTE_MASK;
        }
    }

    /** Wraps a bank number past the ROM's end, as the board's unconnected address lines do. */
    private int prgBank(){
        return (bankSelect & PRG_BANK_MASK) % prgBankCount;
    }

    @Override
    public int readChr(final int address){
        return chr.read(address & (CHR_SIZE - 1));
    }

    /** No-op on CHR-ROM boards - real hardware has nowhere to write CHR-ROM writes to. */
    @Override
    public void writeChr(final int address, final int value){
        chr.write(address & (CHR_SIZE - 1), value);
    }

    @Override
    public Mirroring nametableMirroring(){
        return (bankSelect & NAMETABLE_PAGE_BIT) == 0 ? Mirroring.SINGLE_SCREEN_LOWER : Mirroring.SINGLE_SCREEN_UPPER;
    }

    /** None - AxROM boards have no PRG-RAM. */
    @Override
    public int[] prgRam(){
        return NO_PRG_RAM.clone();
    }

    @Override
    public void restorePrgRam(final int[] prgRam){
        SnapshotArrays.requireSameSize(prgRam, null, "PRG-RAM");
    }

    @Override
    public Map<String, String> debugState(){
        final Map<String, String> state = new LinkedHashMap<>();
        state.put("Bank select", String.format("$%02X", bankSelect));
        state.put("PRG bank", String.valueOf(prgBank()));
        return state;
    }

    @Override
    public MapperSnapshot snapshot(){
        return new AxromMapperSnapshot(NO_PRG_RAM.clone(), chr.snapshot(), bankSelect);
    }

    @Override
    public void checkRestorable(final MapperSnapshot snapshot){
        final AxromMapperSnapshot axrom = SnapshotArrays.expect(snapshot, AxromMapperSnapshot.class);
        SnapshotArrays.requireSameSize(axrom.prgRam(), null, "PRG-RAM");
        chr.checkRestorable(axrom.chrRam());
    }

    @Override
    public void restore(final MapperSnapshot snapshot){
        checkRestorable(snapshot);
        final AxromMapperSnapshot axrom = (AxromMapperSnapshot) snapshot;
        chr.restore(axrom.chrRam());
        bankSelect = axrom.bankSelect();
    }
}
