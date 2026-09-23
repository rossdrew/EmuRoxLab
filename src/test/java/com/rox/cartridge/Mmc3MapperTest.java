package com.rox.cartridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Mmc3MapperTest {

    /** Builds an MMC3 ROM whose first byte of each 8KB PRG bank equals that bank's index (0, 1, 2, ...). */
    private static INesRom romWithBanks(final int prgBankCount8kb){
        final int prgSize = prgBankCount8kb * 0x2000;
        final byte[] fileBytes = new byte[16 + prgSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) (prgSize / 16384); //iNES counts PRG-ROM in 16KB units
        fileBytes[6] = 0x40; //mapper 4 low nibble
        for (int bank = 0; bank < prgBankCount8kb; bank++){
            fileBytes[16 + bank * 0x2000] = (byte) bank;
        }
        return INesRom.parse(fileBytes);
    }

    /** Every PRG-ROM byte set to its own low-order position within its 8KB bank - distinguishes a within-bank offset bug from a bank-selection bug, unlike {@link #romWithBanks}'s own always-read-offset-0 tests. */
    private static INesRom romWithPositionEncodedBanks(final int prgBankCount8kb){
        final int prgSize = prgBankCount8kb * 0x2000;
        final byte[] fileBytes = new byte[16 + prgSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) (prgSize / 16384);
        fileBytes[6] = 0x40;
        for (int i = 0; i < prgSize; i++){
            fileBytes[16 + i] = (byte) (i & 0xFF);
        }
        return INesRom.parse(fileBytes);
    }

    /** 4 PRG banks (32KB - the minimum realistic MMC3 size) plus the given number of 1KB CHR-ROM banks, each CHR bank's first byte equal to its own index. */
    private static INesRom romWithChrBanks(final int chr1kBankCount){
        final int prgSize = 4 * 0x2000;
        final int chrSize = chr1kBankCount * 0x400;
        final byte[] fileBytes = new byte[16 + prgSize + chrSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) (prgSize / 16384);
        fileBytes[5] = (byte) (chrSize / 8192);
        fileBytes[6] = 0x40;
        for (int bank = 0; bank < chr1kBankCount; bank++){
            fileBytes[16 + prgSize + bank * 0x400] = (byte) bank;
        }
        return INesRom.parse(fileBytes);
    }

    /** $8000 (select register + mode/inversion bits) followed by $8001 (data). */
    private static void selectAndLatch(final Mmc3Mapper mapper, final int bankSelectValue, final int dataValue){
        mapper.write(0x8000, bankSelectValue);
        mapper.write(0x8001, dataValue);
    }

    // --- PRG-RAM ---

    @Test
    public void prgRamReadsBackWhatWasWritten(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0x6000, 0x42);
        mapper.write(0x7FFF, 0x99);

        assertEquals(0x42, mapper.read(0x6000));
        assertEquals(0x99, mapper.read(0x7FFF));
    }

    @Test
    public void writeMasksValueTo8Bits(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0x6000, 0x1FF);

        assertEquals(0xFF, mapper.read(0x6000));
    }

    @Test
    public void prgRamRoundTripsThroughRestore(){
        final Mmc3Mapper source = new Mmc3Mapper(romWithBanks(4));
        source.write(0x6000, 0x42);
        source.write(0x7FFF, 0x99);

        final Mmc3Mapper destination = new Mmc3Mapper(romWithBanks(4));
        destination.restorePrgRam(source.prgRam());

        assertEquals(0x42, destination.read(0x6000));
        assertEquals(0x99, destination.read(0x7FFF));
    }

    @Test
    public void restorePrgRamRejectsTheWrongLength(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        assertThrows(IllegalArgumentException.class, () -> mapper.restorePrgRam(new int[1]));
    }

    // --- Construction ---

    @Test
    public void rejectsPrgRomSmallerThan16Kb(){
        assertThrows(IllegalArgumentException.class, () -> new Mmc3Mapper(romWithBanks(1)));
    }

    @Test
    public void acceptsPrgRomOfExactly16Kb(){
        //the precise boundary itself, not just "clearly too small" - romWithBanks(2) = exactly 2
        //8KB banks = 16KB, the minimum valid size
        assertDoesNotThrow(() -> new Mmc3Mapper(romWithBanks(2)));
    }

    // --- PRG banking ---

    @Test
    public void prgModeZeroPutsR6At8000AndFixesCDfffToSecondLast(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4)); //banks 0-3, mode 0 is the power-on default

        //R6 deliberately != the second-last bank (2) below, so a mutant that swapped which ternary
        //branch mode 0 takes can't hide behind both branches coincidentally producing the same value
        selectAndLatch(mapper, 6, 1); //R6 = bank 1

        assertEquals(1, mapper.read(0x8000), "$8000-$9FFF should follow R6");
        assertEquals(1, mapper.bankRegister(6));
        assertEquals(2, mapper.read(0xC000), "$C000-$DFFF is fixed to the second-last bank (index 2 of 4)");
        assertEquals(3, mapper.read(0xE000), "$E000-$FFFF is always fixed to the last bank");
    }

    @Test
    public void prgModeOnePutsR6AtC000AndFixes8000To9fffToSecondLast(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        //same deliberate R6 != 2 reasoning as the mode-0 test above, mirrored for mode 1's own ternary
        selectAndLatch(mapper, 0x40 | 6, 1); //bit6 set: PRG mode 1, R6 = bank 1

        assertEquals(1, mapper.read(0xC000), "$C000-$DFFF should follow R6 in mode 1");
        assertEquals(2, mapper.read(0x8000), "$8000-$9FFF is fixed to the second-last bank in mode 1");
        assertEquals(3, mapper.read(0xE000), "$E000-$FFFF is always fixed to the last bank");
    }

    @Test
    public void r7AlwaysDrivesA000RegardlessOfPrgMode(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        selectAndLatch(mapper, 7, 1); //R7 = bank 1, mode 0
        assertEquals(1, mapper.read(0xA000));

        selectAndLatch(mapper, 0x40 | 7, 3); //R7 = bank 3, mode 1 this time
        assertEquals(3, mapper.read(0xA000), "R7 -> $A000-$BFFF is unaffected by the PRG mode bit");
    }

    @Test
    public void r6AndR7IgnoreTheTopTwoBitsOfTheWrittenValue(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        selectAndLatch(mapper, 6, 0xFF);
        selectAndLatch(mapper, 7, 0xC2);

        assertEquals(0x3F, mapper.bankRegister(6), "MMC3 has only 6 real PRG address lines");
        assertEquals(0x02, mapper.bankRegister(7));
    }

    @Test
    public void bankSelectStoresTheRawWrittenValueIncludingModeAndInversionBits(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0x8000, 0xC6); //CHR inversion (bit7) + PRG mode (bit6) + register select 6

        assertEquals(0xC6, mapper.bankSelect());
    }

    @Test
    public void prgReadsRespectTheOffsetWithinTheSelectedBankNotJustItsFirstByte(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithPositionEncodedBanks(4));

        selectAndLatch(mapper, 6, 1); //R6 = bank 1

        assertEquals(0x00, mapper.read(0x8000), "offset 0 of bank 1");
        assertEquals(0x05, mapper.read(0x8005), "offset 5 of bank 1");
        assertEquals(0xFF, mapper.read(0x9FFF), "offset 0x1FFF (8191 & 0xFF) of bank 1, the last byte of the window");
    }

    @Test
    public void bankNumbersWrapModuloTheRomsActualBankCount(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4)); //only banks 0-3 actually exist

        selectAndLatch(mapper, 6, 5); //5 % 4 = 1

        assertEquals(1, mapper.read(0x8000));
    }

    // --- CHR banking ---

    @Test
    public void chrInversionZeroPutsTheTwoKbWindowsAt0000And0800(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));

        selectAndLatch(mapper, 0, 0); //R0 = bank 0 (2KB window at $0000-$07FF)
        selectAndLatch(mapper, 1, 2); //R1 = bank 2 (2KB window at $0800-$0FFF)
        selectAndLatch(mapper, 2, 4); //R2 = bank 4 (1KB window at $1000-$13FF)
        selectAndLatch(mapper, 3, 5); //R3 = bank 5 (1KB window at $1400-$17FF)
        selectAndLatch(mapper, 4, 6); //R4 = bank 6 (1KB window at $1800-$1BFF)
        selectAndLatch(mapper, 5, 7); //R5 = bank 7 (1KB window at $1C00-$1FFF)

        assertEquals(0, mapper.readChr(0x0000), "R0's first 1KB half");
        assertEquals(1, mapper.readChr(0x0400), "R0's second 1KB half (R0+1)");
        assertEquals(2, mapper.readChr(0x0800), "R1's first 1KB half");
        assertEquals(3, mapper.readChr(0x0C00), "R1's second 1KB half (R1+1)");
        assertEquals(4, mapper.readChr(0x1000));
        assertEquals(5, mapper.readChr(0x1400));
        assertEquals(6, mapper.readChr(0x1800));
        assertEquals(7, mapper.readChr(0x1C00));
    }

    @Test
    public void chrInversionOneSwapsWhichHalfHoldsTheTwoKbWindows(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));

        //bit7 set on the bank-select write: CHR A12 inversion
        mapper.write(0x8000, 0x80 | 0);
        mapper.write(0x8001, 0); //R0 = bank 0, now the 2KB window at $1000-$17FF
        mapper.write(0x8000, 0x80 | 2);
        mapper.write(0x8001, 4); //R2 = bank 4, now the 1KB window at $0000-$03FF

        assertEquals(4, mapper.readChr(0x0000), "R2 now maps to $0000-$03FF under inversion");
        assertEquals(0, mapper.readChr(0x1000), "R0's 2KB window now starts at $1000, not $0000");
    }

    @Test
    public void twoKbChrRegistersIgnoreTheBottomBit(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));

        selectAndLatch(mapper, 0, 5); //odd value written to R0

        assertEquals(4, mapper.bankRegister(0), "the lowest bit must be forced even");
    }

    @Test
    public void chrBankNumbersWrapModuloTheRomsActualBankCount(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8)); //only 1KB banks 0-7 actually exist

        selectAndLatch(mapper, 2, 10); //R2 = bank 10, 10 % 8 = 2

        assertEquals(2, mapper.readChr(0x1000));
    }

    @Test
    public void writesToChrRomAreNoOps(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));

        mapper.writeChr(0x0000, 0x55);

        assertEquals(0, mapper.readChr(0x0000), "CHR-ROM has nothing to write to");
    }

    @Test
    public void noChrBanksMeansWritableChrRam(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4)); //no CHR banks in the header -> CHR-RAM

        mapper.writeChr(0x0123, 0x77);

        assertEquals(0x77, mapper.readChr(0x0123));
    }

    // --- Mirroring ---

    @Test
    public void mirroringDefaultsFromTheHeaderBitBeforeAnyA000Write(){
        final byte[] fileBytes = new byte[16 + 4 * 0x2000];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) ((4 * 0x2000) / 16384);
        fileBytes[6] = (byte) (0x40 | 0x01); //mapper 4 low nibble, header vertical-mirroring bit set
        final Mmc3Mapper mapper = new Mmc3Mapper(INesRom.parse(fileBytes));

        assertEquals(Mirroring.VERTICAL, mapper.nametableMirroring(), "before any $A000 write, the header bit is a reasonable initial hint");
    }

    @Test
    public void mirroringBitZeroMeansHorizontal(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0xA000, 0);

        assertEquals(Mirroring.HORIZONTAL, mapper.nametableMirroring());
    }

    @Test
    public void mirroringBitOneMeansVertical(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0xA000, 1);

        assertEquals(Mirroring.VERTICAL, mapper.nametableMirroring());
    }

    // --- IRQ: register plumbing ---

    @Test
    public void irqLatchStoresTheWrittenValue(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0xC000, 0x2A);

        assertEquals(0x2A, mapper.irqLatch());
    }

    @Test
    public void irqEnableAndDisableToggleTheEnabledFlag(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4));

        mapper.write(0xE001, 0);
        assertTrue(mapper.irqEnabled());

        mapper.write(0xE000, 0);
        assertFalse(mapper.irqEnabled());
    }

    @Test
    public void irqDisableAcknowledgesAPendingInterrupt(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 0); //latch = 0
        mapper.write(0xE001, 0); //enable
        mapper.readChr(0x1000); //rising edge: counter 0 -> reload to 0 -> IRQ asserted
        assertTrue(mapper.isIrqAsserted(), "sanity check: IRQ should be pending before the disable write");

        mapper.write(0xE000, 0);

        assertFalse(mapper.isIrqAsserted());
    }

    // --- IRQ: scanline counter clocking ---

    @Test
    public void aRisingEdgeOnA12DecrementsANonZeroCounter(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 5); //latch = 5
        mapper.write(0xC001, 0); //request a reload
        mapper.readChr(0x1000); //rising edge #1: counter was 0/reload-requested -> reloads to 5

        mapper.readChr(0x0000); //fall low
        mapper.readChr(0x1000); //rising edge #2: counter is 5, no reload requested -> decrements to 4

        assertEquals(4, mapper.irqCounter());
    }

    @Test
    public void repeatedHighReadsWithoutFallingLowDoNotReClock(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 5);
        mapper.write(0xC001, 0);
        mapper.readChr(0x1000); //rising edge: reloads to 5

        mapper.readChr(0x1001); //still high - no edge
        mapper.readChr(0x1FFF); //still high - no edge

        assertEquals(5, mapper.irqCounter(), "the counter must only clock on a genuine low-to-high transition");
    }

    @Test
    public void aFallingEdgeDoesNotClockTheCounter(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 5);
        mapper.write(0xC001, 0);
        mapper.readChr(0x1000); //rising edge: reloads to 5

        mapper.readChr(0x0000); //falling edge only

        assertEquals(5, mapper.irqCounter());
    }

    @Test
    public void counterReloadsWhenClockedWhileAlreadyZero(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 9); //latch = 9 - counter itself starts at 0 (power-on default), no reload requested yet

        mapper.readChr(0x1000); //rising edge: counter is already 0 -> reloads to 9, not -1/underflow

        assertEquals(9, mapper.irqCounter());
    }

    @Test
    public void writingIrqReloadClearsTheCounterImmediatelyEvenBeforeTheNextEdge(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 9);
        mapper.write(0xC001, 0); //request reload
        mapper.readChr(0x1000);
        mapper.readChr(0x0000);
        mapper.readChr(0x1000); //counter is now 8 (decremented once)

        mapper.write(0xC001, 0); //request another reload

        assertEquals(0, mapper.irqCounter(), "$C001 clears the counter immediately, independent of any A12 edge");
    }

    @Test
    public void irqIsAssertedOnlyWhenTheCounterReachesZeroAndIrqsAreEnabled(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 1); //latch = 1
        mapper.write(0xC001, 0); //request reload
        mapper.write(0xE001, 0); //enable IRQs
        mapper.readChr(0x1000); //rising edge: reloads to 1 (nonzero) - no IRQ yet
        assertFalse(mapper.isIrqAsserted());

        mapper.readChr(0x0000);
        mapper.readChr(0x1000); //rising edge: decrements 1 -> 0 -> IRQ asserted

        assertTrue(mapper.isIrqAsserted());
    }

    @Test
    public void counterStillRunsWhileIrqsAreDisabledButDoesNotAssert(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithChrBanks(8));
        mapper.write(0xC000, 0); //latch = 0
        mapper.write(0xC001, 0); //request reload
        //IRQs never enabled

        mapper.readChr(0x1000); //rising edge: reloads to 0 - would assert if enabled

        assertEquals(0, mapper.irqCounter(), "the counter itself is never gated by the enable/disable registers");
        assertFalse(mapper.isIrqAsserted());
    }

    @Test
    public void writingChrDoesNotClockTheIrqCounter(){
        final Mmc3Mapper mapper = new Mmc3Mapper(romWithBanks(4)); //CHR-RAM board
        mapper.write(0xC000, 5);
        mapper.write(0xC001, 0);
        mapper.readChr(0x1000); //rising edge: reloads to 5

        mapper.writeChr(0x0000, 0x11); //a CPU-driven CHR-RAM write, not a rendering fetch
        mapper.writeChr(0x1000, 0x22);

        assertEquals(5, mapper.irqCounter(), "only readChr (real address-bus activity) clocks the counter");
    }
}
