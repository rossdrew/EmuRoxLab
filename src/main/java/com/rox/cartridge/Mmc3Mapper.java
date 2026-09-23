package com.rox.cartridge;

import static com.rox.ByteUtil.BYTE_MASK;

/**
 * iNES mapper 4 (MMC3). Four register pairs at {@code $8000-$FFFF}, even address selects the "low"
 * register of the pair, odd selects the "high" one:
 * <ul>
 *     <li>{@code $8000}/{@code $8001} - bank select / bank data: {@code $8000} picks which of 8
 *     internal bank registers (R0-R7) the next {@code $8001} write latches into, plus the PRG bank
 *     mode (bit 6) and CHR A12 inversion (bit 7) bits.</li>
 *     <li>{@code $A000}/{@code $A001} - nametable mirroring / PRG-RAM write-protect. The protect bits
 *     are deliberately not modeled - real MMC3 boards use them to write-protect save RAM during
 *     power-off, not something a running emulator needs, and modeling them risks the same MMC6
 *     register-meaning incompatibility real emulators avoid by skipping this too.</li>
 *     <li>{@code $C000}/{@code $C001} - IRQ latch / IRQ reload.</li>
 *     <li>{@code $E000}/{@code $E001} - IRQ disable(+acknowledge) / IRQ enable.</li>
 * </ul>
 *
 * PRG-ROM: four 8KB windows ({@code $8000-$9FFF}, {@code $A000-$BFFF}, {@code $C000-$DFFF},
 * {@code $E000-$FFFF}). R7 always drives {@code $A000-$BFFF} and the last bank is always fixed at
 * {@code $E000-$FFFF}; the bank-mode bit swaps which of {@code $8000-$9FFF}/{@code $C000-$DFFF} is
 * R6-switchable and which is fixed to the second-last bank. 8KB PRG-RAM at {@code $6000-$7FFF},
 * always readable/writable - this codebase doesn't model the PRG-RAM chip-enable bit (see above),
 * matching {@link Mmc1Mapper}'s own equivalent simplification.
 *
 * CHR: two 2KB windows (R0, R1) and four 1KB windows (R2-R5) tiling {@code $0000-$1FFF}; the CHR A12
 * inversion bit swaps which half ({@code $0000-$0FFF} or {@code $1000-$1FFF}) holds the 2KB windows
 * versus the 1KB ones - verified against nesdev.org's own bulleted "PPU $0000-$07FF (or $1000-$17FF)"
 * listing (not its separately-rendered summary table, which garbles this exact mapping - re-checked
 * directly against the page's raw HTML after that discrepancy surfaced). CHR-RAM (a single fixed 8KB
 * bank, banking registers simply ignored) on boards with no CHR-ROM in the file, same convention as
 * {@link NromMapper}/{@link Mmc1Mapper}.
 *
 * IRQ: a scanline counter clocked by a rising edge on the PPU address bus's A12 line (bit 12 going
 * 0→1) - on real hardware, filtered to require the line was low for a few PPU cycles first, to reject
 * spurious mid-fetch toggles. That filter is deliberately not modeled here: this codebase's PPU
 * ({@code PPU.readMemory}) drives every pattern-table fetch - background and sprite alike - through
 * {@link #readChr}, at the same real per-dot cadence real hardware uses, so a plain, unfiltered
 * 0→1 edge on {@link #readChr}'s own address already reproduces the intended "once per scanline"
 * signal for the standard (and by far most common) configuration nesdev itself documents as required
 * for the counter to work at all: background and sprites drawn from different pattern-table halves.
 * Counter semantics (verified against nesdev.org's "Counter operation"/"Important points" sections):
 * clocked on that edge, the counter reloads from the IRQ latch if it's currently zero or a reload was
 * requested (via {@code $C001}), otherwise it decrements; an IRQ is asserted whenever the
 * (post-clock) counter is zero and IRQs are enabled. The counter itself is never gated by the
 * enable/disable registers - only whether reaching zero actually asserts the IRQ line is.
 *
 * <p><b>Known limitation</b> (flagged by CodeRabbit review on PR #37, not yet fixed): the "no filter
 * needed" reasoning above only holds when every sprite fetched on a scanline reads from the same
 * pattern-table half - true for the standard background-at-{@code $0000}/sprites-at-{@code $1000}
 * (or vice versa) configuration nesdev documents as required for the counter to work at all, but not
 * for 8x16 sprites, where each sprite's own tile index (not a single shared PPUCTRL bit) picks its
 * pattern-table half. A frame mixing even- and odd-indexed 8x16 sprites can toggle A12 more than once
 * within one scanline's sprite-fetch phase, which this unfiltered edge detector would (incorrectly)
 * clock as multiple rises. Compounding this, {@code PPU.fetchSpritesForNextScanline()} still fetches
 * all 8 sprite slots (real and dummy) in one collapsed step at dot 257 rather than real hardware's
 * spread across dots 257-320, so even a correctly-filtered edge could land at the wrong simulated
 * instant relative to the scanline. Properly fixing this needs two coordinated changes neither made
 * here: PPU-cycle-aware low-time qualification in this class (real hardware requires A12 low for a
 * few PPU cycles before counting a rise), and un-collapsing the PPU's sprite fetch back into its own
 * per-slot dots so that timing information actually exists to qualify against - a bigger change than
 * this simplification was worth making speculatively, since no ROM this codebase currently runs
 * exercises 8x16-sprite MMC3 IRQ timing (Shadowgate's own mapper-4 conversion doesn't use the IRQ
 * mechanism at all, verified by instrumenting every {@code $8000-$FFFF} register write across full
 * playthroughs). Revisit if/when a ROM actually depends on this.
 */
public final class Mmc3Mapper implements Mapper {
    private static final int PRG_RAM_SIZE = 0x2000;
    private static final int PRG_ROM_START_ADDRESS = 0x8000;
    private static final int PRG_BANK_SIZE = 0x2000;
    private static final int CHR_WINDOW_SIZE = 0x2000;
    private static final int CHR_BANK_1KB = 0x400;
    private static final int CHR_A12_BIT = 0x1000;

    private static final int BANK_SELECT_REGISTER_MASK = 0x07;
    private static final int PRG_MODE_BIT = 0x40;
    private static final int CHR_INVERSION_BIT = 0x80;
    private static final int PRG_BANK_NUMBER_MASK = 0x3F; //MMC3 has only 6 PRG ROM address lines
    private static final int MIRRORING_REGISTER_START = 0xA000;
    private static final int IRQ_LATCH_REGISTER_START = 0xC000;
    private static final int IRQ_ENABLE_REGISTER_START = 0xE000;

    private final byte[] prgRom;
    private final int[] prgRam = new int[PRG_RAM_SIZE];
    private final byte[] chrRom;
    private final int[] chrRam;

    private final int[] bankRegister = new int[8]; //R0-R7 - unspecified at power-on, same as real hardware
    private int bankSelect; //last value written to $8000: register-select bits, PRG mode bit, CHR inversion bit
    private boolean horizontalMirroring;

    private int irqLatch;
    private int irqCounter;
    private boolean irqReloadRequested;
    private boolean irqEnabled;
    private boolean irqPending;
    private boolean lastChrAddressA12High;

    public Mmc3Mapper(final INesRom rom){
        this.prgRom = rom.prgRom();
        if (prgRom.length < PRG_BANK_SIZE * 2){
            throw new IllegalArgumentException("MMC3 requires at least 16KB of PRG-ROM, got " + prgRom.length + " bytes");
        }
        final byte[] romChr = rom.chrRom();
        this.chrRom = romChr.length > 0 ? romChr : null;
        this.chrRam = romChr.length > 0 ? null : new int[CHR_WINDOW_SIZE];
        //a reasonable initial hint, immediately overridden by the game's own $A000 write - real MMC3
        //hardware has no defined power-on mirroring state of its own (see class doc's "$8000... is
        //unspecified at power on" quote, equally true here)
        this.horizontalMirroring = !rom.isVerticalMirroring();
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see {@link Mapper}'s class doc). */
    @Override
    public int read(final int address){
        if (address >= PRG_ROM_START_ADDRESS){
            return prgRom[prgRomOffset(address)] & BYTE_MASK;
        }
        return prgRam[address & (PRG_RAM_SIZE - 1)];
    }

    /** Contract: only ever called with {@code address} in {@code $6000-$FFFF} (see {@link Mapper}'s class doc). */
    @Override
    public void write(final int address, final int value){
        if (address < PRG_ROM_START_ADDRESS){
            prgRam[address & (PRG_RAM_SIZE - 1)] = value & BYTE_MASK;
            return;
        }
        final boolean even = (address & 1) == 0;
        if (address < MIRRORING_REGISTER_START){
            if (even){
                bankSelect = value & BYTE_MASK;
            } else {
                writeBankData(value);
            }
        } else if (address < IRQ_LATCH_REGISTER_START){
            if (even){
                horizontalMirroring = (value & 1) == 0;
            }
            //odd ($A001): PRG-RAM write-protect - deliberately not modeled, see class doc
        } else if (address < IRQ_ENABLE_REGISTER_START){
            if (even){
                irqLatch = value & BYTE_MASK;
            } else {
                //"clears the MMC3 IRQ counter immediately, and then reloads it at the NEXT rising edge"
                irqCounter = 0;
                irqReloadRequested = true;
            }
        } else {
            if (even){
                irqEnabled = false;
                irqPending = false; //"acknowledge any pending interrupts"
            } else {
                irqEnabled = true; //"the counter remains unaffected" - only future IRQ assertion is gated
            }
        }
    }

    private void writeBankData(final int value){
        final int register = bankSelect & BANK_SELECT_REGISTER_MASK;
        final int masked = switch (register){
            case 0, 1 -> value & ~1; //R0/R1: 2KB CHR banks - bottom bit ignored, always an even bank number
            case 6, 7 -> value & PRG_BANK_NUMBER_MASK;
            default -> value & BYTE_MASK;
        };
        bankRegister[register] = masked;
    }

    private int prgRomOffset(final int address){
        final int windowOffset = address - PRG_ROM_START_ADDRESS; //0..0x7FFF
        final int window = windowOffset / PRG_BANK_SIZE; //0-3
        final int totalBanks = prgRom.length / PRG_BANK_SIZE;
        final boolean prgMode1 = (bankSelect & PRG_MODE_BIT) != 0;
        final int bank = switch (window){
            case 0 -> prgMode1 ? totalBanks - 2 : bankRegister[6] % totalBanks;
            case 1 -> bankRegister[7] % totalBanks;
            case 2 -> prgMode1 ? bankRegister[6] % totalBanks : totalBanks - 2;
            default -> totalBanks - 1; //$E000-$FFFF: always the last bank
        };
        return bank * PRG_BANK_SIZE + (windowOffset & (PRG_BANK_SIZE - 1));
    }

    @Override
    public int readChr(final int address){
        clockScanlineCounterOnA12RisingEdge(address);
        final int offset = chrOffset(address);
        return chrRom != null ? chrRom[offset] & BYTE_MASK : chrRam[offset];
    }

    @Override
    public void writeChr(final int address, final int value){
        if (chrRam != null){
            chrRam[chrOffset(address)] = value & BYTE_MASK;
        }
        //writes to CHR-ROM are no-ops, same as PRG-ROM
    }

    private void clockScanlineCounterOnA12RisingEdge(final int address){
        final boolean a12High = (address & CHR_A12_BIT) != 0;
        if (a12High && !lastChrAddressA12High){
            if (irqCounter == 0 || irqReloadRequested){
                irqCounter = irqLatch;
            } else {
                irqCounter--;
            }
            irqReloadRequested = false;
            if (irqCounter == 0 && irqEnabled){
                irqPending = true;
            }
        }
        lastChrAddressA12High = a12High;
    }

    private int chrOffset(final int address){
        final int a = address & (CHR_WINDOW_SIZE - 1);
        final boolean inverted = (bankSelect & CHR_INVERSION_BIT) != 0;
        final int effective = inverted ? a ^ CHR_A12_BIT : a;
        final int region = effective / CHR_BANK_1KB; //0-7
        final int bank1k = switch (region){
            case 0 -> bankRegister[0];
            case 1 -> bankRegister[0] + 1;
            case 2 -> bankRegister[1];
            case 3 -> bankRegister[1] + 1;
            case 4 -> bankRegister[2];
            case 5 -> bankRegister[3];
            case 6 -> bankRegister[4];
            default -> bankRegister[5]; //region 7
        };
        //no separate "bank1k % totalKbBanks" wraparound step needed here: chrSize is always an exact
        //multiple of CHR_BANK_1KB, so the trailing "% chrSize" below already produces the identical
        //result on its own - (bank1k % n)*k % (n*k) == bank1k*k % (n*k) for any non-negative bank1k
        final int chrSize = chrRom != null ? chrRom.length : chrRam.length;
        final int offsetWithinBank = effective & (CHR_BANK_1KB - 1);
        return (bank1k * CHR_BANK_1KB + offsetWithinBank) % chrSize;
    }

    @Override
    public Mirroring nametableMirroring(){
        return horizontalMirroring ? Mirroring.HORIZONTAL : Mirroring.VERTICAL;
    }

    @Override
    public boolean isIrqAsserted(){
        return irqPending;
    }

    @Override
    public int[] prgRam(){
        return prgRam.clone();
    }

    @Override
    public void restorePrgRam(final int[] prgRam){
        if (prgRam.length != this.prgRam.length){
            throw new IllegalArgumentException("Expected " + this.prgRam.length + " bytes of PRG-RAM, got " + prgRam.length);
        }
        System.arraycopy(prgRam, 0, this.prgRam, 0, prgRam.length);
    }

    int bankRegister(final int index){
        return bankRegister[index];
    }

    int bankSelect(){
        return bankSelect;
    }

    int irqCounter(){
        return irqCounter;
    }

    int irqLatch(){
        return irqLatch;
    }

    boolean irqEnabled(){
        return irqEnabled;
    }
}
