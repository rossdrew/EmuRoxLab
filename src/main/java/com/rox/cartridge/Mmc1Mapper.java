package com.rox.cartridge;

/**
 * iNES mapper 1 (MMC1). Every write to {@code $8000-$FFFF} feeds one bit (the value's bit 0) into a
 * 5-bit serial shift register, LSB first; on the 5th write, the assembled 5-bit value latches into
 * one of four internal registers, chosen by which address range that 5th write landed in:
 * {@code $8000-$9FFF} control, {@code $A000-$BFFF} CHR bank 0, {@code $C000-$DFFF} CHR bank 1,
 * {@code $E000-$FFFF} PRG bank. A write with bit 7 set doesn't shift at all - it immediately resets
 * the shift register and forces the control register's PRG bank mode to 3 (switch $8000, fix the
 * last bank at $C000), matching real hardware's power-on/reset state.
 *
 * PRG-ROM bank modes (control register bits 2-3): 0 and 1 both mean "switch a 32KB bank at $8000"
 * (the PRG bank register's bit 0 is ignored - the whole window moves as one 32KB unit); 2 fixes bank
 * 0 at $8000 and switches a 16KB bank at $C000; 3 switches a 16KB bank at $8000 and fixes the last
 * bank at $C000 (the reset state). 8KB PRG-RAM at $6000-$7FFF, always writable - this codebase
 * doesn't model the PRG-RAM chip-enable bit some MMC1 revisions (B2+) add to the PRG bank register.
 *
 * CHR (control register bit 4, {@link #CHR_MODE_BIT}): 0 = 8KB mode, one bank switched by
 * {@link #chrBank0Register()} with its bit 0 ignored (the whole 8KB window moves as one unit,
 * mirroring how PRG bank modes 0/1 ignore the PRG bank register's own bit 0); 1 = 4KB mode, two
 * independently-switched 4KB banks, one per CHR bank register. CHR-RAM boards (no CHR-ROM in the
 * file) get a single fixed 8KB RAM bank instead - real MMC1+CHR-RAM boards don't bank CHR-RAM either,
 * so the bank registers are simply ignored in that case. Mirroring is read dynamically from the
 * control register's low 2 bits on every call (0/1 = fixed single-screen lower/upper, 2 = vertical,
 * 3 = horizontal) - this overrides the iNES header's mirroring bit, real MMC1 hardware behaviour.
 *
 * Simplifications: the extended &gt;256KB SUROM/SOROM addressing scheme, which repurposes CHR bank
 * register bits as extra PRG bank bits, isn't modeled - not needed for any ROM this codebase
 * currently loads.
 */
public final class Mmc1Mapper implements Mapper {
    private static final int PRG_RAM_SIZE = 0x2000;
    private static final int PRG_ROM_START_ADDRESS = 0x8000;
    private static final int PRG_BANK_SIZE = 0x4000;
    private static final int CHR_BANK_0_START_ADDRESS = 0xA000;
    private static final int CHR_BANK_1_START_ADDRESS = 0xC000;
    private static final int PRG_BANK_REGISTER_START_ADDRESS = 0xE000;
    private static final int CHR_WINDOW_SIZE = 0x2000;
    private static final int CHR_BANK_4KB_SIZE = 0x1000;
    private static final int CHR_MODE_BIT = 0x10;
    private static final int CHR_BANK_NUMBER_MASK = 0x1F;
    private static final int MIRRORING_MODE_MASK = 0x03;

    private static final int RESET_BIT = 0x80;
    private static final int SHIFTS_PER_LATCH = 5;
    private static final int RESET_PRG_BANK_MODE_BITS = 0x0C;

    private static final int PRG_BANK_MODE_SHIFT = 2;
    private static final int PRG_BANK_MODE_MASK = 0x03;
    private static final int PRG_BANK_MODE_SWITCH_32KB_HIGH = 1;
    private static final int PRG_BANK_MODE_FIX_FIRST = 2;
    private static final int PRG_BANK_NUMBER_MASK = 0x0F;
    private static final int BYTE_MASK = 0xFF;

    private final byte[] prgRom;
    private final int[] prgRam = new int[PRG_RAM_SIZE];
    private final byte[] chrRom;
    private final int[] chrRam;

    private int shiftRegister;
    private int shiftCount;

    private int controlRegister = RESET_PRG_BANK_MODE_BITS; //power-on: PRG mode 3
    private int chrBank0Register;
    private int chrBank1Register;
    private int prgBankRegister;

    public Mmc1Mapper(final INesRom rom){
        this.prgRom = rom.prgRom();
        if (prgRom.length == 0){
            throw new IllegalArgumentException("MMC1 requires at least one 16KB PRG-ROM bank");
        }
        final byte[] romChr = rom.chrRom();
        this.chrRom = romChr.length > 0 ? romChr : null;
        this.chrRam = romChr.length > 0 ? null : new int[CHR_WINDOW_SIZE];
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see class Javadoc). */
    @Override
    public int read(final int address){
        if (address >= PRG_ROM_START_ADDRESS){
            return prgRom[prgRomOffset(address)] & BYTE_MASK;
        }
        return prgRam[address & (PRG_RAM_SIZE - 1)];
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see class Javadoc). */
    @Override
    public void write(final int address, final int value){
        if (address < PRG_ROM_START_ADDRESS){
            prgRam[address & (PRG_RAM_SIZE - 1)] = value & BYTE_MASK;
            return;
        }
        if ((value & RESET_BIT) != 0){
            shiftRegister = 0;
            shiftCount = 0;
            controlRegister |= RESET_PRG_BANK_MODE_BITS;
            return;
        }
        shiftRegister |= (value & 1) << shiftCount;
        shiftCount++;
        if (shiftCount == SHIFTS_PER_LATCH){
            latch(address, shiftRegister);
            shiftRegister = 0;
            shiftCount = 0;
        }
    }

    private void latch(final int address, final int value){
        if (address < CHR_BANK_0_START_ADDRESS){
            controlRegister = value;
        } else if (address < CHR_BANK_1_START_ADDRESS){
            chrBank0Register = value;
        } else if (address < PRG_BANK_REGISTER_START_ADDRESS){
            chrBank1Register = value;
        } else {
            prgBankRegister = value;
        }
    }

    private int prgRomOffset(final int address){
        final int windowOffset = address - PRG_ROM_START_ADDRESS; //0..0x7FFF
        final int totalBanks = prgRom.length / PRG_BANK_SIZE;
        final int prgBankMode = (controlRegister >> PRG_BANK_MODE_SHIFT) & PRG_BANK_MODE_MASK;
        final int bankNumber = prgBankRegister & PRG_BANK_NUMBER_MASK;

        if (prgBankMode <= PRG_BANK_MODE_SWITCH_32KB_HIGH){
            final int bank32 = (bankNumber >> 1) % Math.max(1, totalBanks / 2);
            //mirrors a single-bank (16KB) ROM across the 32KB window, same as NromMapper's own
            //16KB-bank mirroring - a lone-bank MMC1 image is nonstandard but shouldn't crash
            return (bank32 * (PRG_BANK_SIZE * 2) + windowOffset) % prgRom.length;
        }
        if (windowOffset < PRG_BANK_SIZE){
            final int bank = prgBankMode == PRG_BANK_MODE_FIX_FIRST ? 0 : bankNumber % totalBanks;
            return bank * PRG_BANK_SIZE + windowOffset;
        }
        final int bank = prgBankMode == PRG_BANK_MODE_FIX_FIRST ? bankNumber % totalBanks : totalBanks - 1;
        return bank * PRG_BANK_SIZE + (windowOffset - PRG_BANK_SIZE);
    }

    @Override
    public int readChr(final int address){
        if (chrRam != null){
            return chrRam[address & (CHR_WINDOW_SIZE - 1)];
        }
        return chrRom[chrRomOffset(address)] & BYTE_MASK;
    }

    @Override
    public void writeChr(final int address, final int value){
        if (chrRam != null){
            chrRam[address & (CHR_WINDOW_SIZE - 1)] = value & BYTE_MASK;
        }
        //writes to CHR-ROM are no-ops, same as PRG-ROM
    }

    private int chrRomOffset(final int address){
        final boolean fourKbMode = (controlRegister & CHR_MODE_BIT) != 0;
        final int totalFourKbBanks = chrRom.length / CHR_BANK_4KB_SIZE;
        if (!fourKbMode){
            final int bank8kb = (chrBank0Register >> 1) % Math.max(1, totalFourKbBanks / 2);
            return (bank8kb * CHR_WINDOW_SIZE + address) % chrRom.length;
        }
        final int bankRegister = address < CHR_BANK_4KB_SIZE ? chrBank0Register : chrBank1Register;
        final int bank = (bankRegister & CHR_BANK_NUMBER_MASK) % totalFourKbBanks;
        return (bank * CHR_BANK_4KB_SIZE + (address & (CHR_BANK_4KB_SIZE - 1))) % chrRom.length;
    }

    @Override
    public Mirroring nametableMirroring(){
        return switch (controlRegister & MIRRORING_MODE_MASK){
            case 0 -> Mirroring.SINGLE_SCREEN_LOWER;
            case 1 -> Mirroring.SINGLE_SCREEN_UPPER;
            case 2 -> Mirroring.VERTICAL;
            default -> Mirroring.HORIZONTAL;
        };
    }

    int controlRegister(){
        return controlRegister;
    }

    int chrBank0Register(){
        return chrBank0Register;
    }

    int chrBank1Register(){
        return chrBank1Register;
    }

    int prgBankRegister(){
        return prgBankRegister;
    }
}
