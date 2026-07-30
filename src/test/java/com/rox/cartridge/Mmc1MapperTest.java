package com.rox.cartridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Mmc1MapperTest {

    /** Builds an MMC1 ROM whose first byte of each 16KB bank equals that bank's index (0, 1, 2, ...). */
    private static INesRom romWithBanks(final int bankCount){
        final int prgSize = bankCount * 0x4000;
        final byte[] fileBytes = new byte[16 + prgSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) bankCount;
        fileBytes[6] = 0x10; //mapper 1 low nibble
        for (int bank = 0; bank < bankCount; bank++){
            fileBytes[16 + bank * 0x4000] = (byte) bank;
        }
        return INesRom.parse(fileBytes);
    }

    /** Performs the 5 shift-register writes (LSB first) that latch {@code fiveBitValue} at {@code address}. */
    private static void writeFiveBits(final Mmc1Mapper mapper, final int address, final int fiveBitValue){
        for (int i = 0; i < 5; i++){
            mapper.write(address, (fiveBitValue >> i) & 1);
        }
    }

    /** Every PRG-ROM byte set to its own low-order global offset, so off-by-one/off-by-bank bugs show up. */
    private static INesRom romWithPositionEncodedBanks(final int bankCount){
        final int prgSize = bankCount * 0x4000;
        final byte[] fileBytes = new byte[16 + prgSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) bankCount;
        fileBytes[6] = 0x10;
        for (int i = 0; i < prgSize; i++){
            fileBytes[16 + i] = (byte) (i & 0xFF);
        }
        return INesRom.parse(fileBytes);
    }

    /**
     * 1 PRG bank plus the given number of 4KB CHR-ROM banks, every byte within a bank set to that
     * bank's own index - unlike position-encoded data (which is invariant to which bank you're in
     * whenever the bank size is a multiple of 256, as 4KB/8KB both are), this lets a test tell *which*
     * bank got selected, not just whether some byte within it round-trips correctly.
     */
    private static INesRom romWithIndexedChrBanks(final int chr4kBankCount){
        final int chrSize = chr4kBankCount * 0x1000;
        final byte[] fileBytes = new byte[16 + 0x4000 + chrSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 1;
        fileBytes[5] = (byte) (chrSize / 8192);
        fileBytes[6] = 0x10;
        for (int bank = 0; bank < chr4kBankCount; bank++){
            for (int i = 0; i < 0x1000; i++){
                fileBytes[16 + 0x4000 + bank * 0x1000 + i] = (byte) bank;
            }
        }
        return INesRom.parse(fileBytes);
    }

    /** 1 PRG bank plus the given number of 4KB CHR-ROM banks, each byte set to its own low-order global offset. */
    private static INesRom romWithPositionEncodedChrBanks(final int chr4kBankCount){
        final int chrSize = chr4kBankCount * 0x1000;
        final byte[] fileBytes = new byte[16 + 0x4000 + chrSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 1;
        fileBytes[5] = (byte) (chrSize / 8192); //8KB units - rounds down, tests below use even 4KB-bank counts
        fileBytes[6] = 0x10;
        for (int i = 0; i < chrSize; i++){
            fileBytes[16 + 0x4000 + i] = (byte) (i & 0xFF);
        }
        return INesRom.parse(fileBytes);
    }

    @Test
    public void fiveWritesToLowRangeLatchTheControlRegister(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0x8000, 0x15); //10101, LSB first

        assertEquals(0x15, mapper.controlRegister());
    }

    @Test
    public void fiveWritesToChrBank0RangeLatchChrBank0Register(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0xA000, 0x0B);

        assertEquals(0x0B, mapper.chrBank0Register());
        assertEquals(0, mapper.chrBank1Register(), "only chr bank 0 should have latched");
    }

    @Test
    public void fiveWritesToChrBank1RangeLatchChrBank1Register(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0xC000, 0x1D);

        assertEquals(0x1D, mapper.chrBank1Register());
        assertEquals(0, mapper.chrBank0Register(), "only chr bank 1 should have latched");
    }

    @Test
    public void fiveWritesToHighRangeLatchThePrgBankRegister(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0xE000, 0x07);

        assertEquals(0x07, mapper.prgBankRegister());
    }

    @Test
    public void firstWriteIsTheLowestBitOfTheLatchedValue(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        mapper.write(0x8000, 1); //bit 0
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);

        assertEquals(0x01, mapper.controlRegister());
    }

    @Test
    public void fifthWriteIsTheHighestBitOfTheLatchedValue(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 0);
        mapper.write(0x8000, 1); //bit 4

        assertEquals(0x10, mapper.controlRegister());
    }

    @Test
    public void resetBitAbortsAnInProgressSequenceAndForcesPrgMode3(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));
        mapper.write(0x8000, 1);
        mapper.write(0x8000, 1); //2 of 5 shifted in - would corrupt the next latch if not reset

        mapper.write(0x8000, 0x80); //reset bit

        writeFiveBits(mapper, 0xE000, 0x03); //fresh 5-write sequence for the PRG bank register
        assertEquals(0x03, mapper.prgBankRegister(), "shift register should have been cleared by the reset write");
        assertEquals(0x0C, mapper.controlRegister() & 0x0C, "reset must force PRG bank mode to 3 (both mode bits set)");
    }

    @Test
    public void resetSetsPrgBankModeBitsEvenWhenTheyWerePreviouslyClear(){
        //latch a control value with the PRG-mode bits (2-3) explicitly clear first, so a reset that
        //incorrectly ANDs (instead of ORs) them in would leave them clear instead of setting them
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));
        writeFiveBits(mapper, 0x8000, 0x00); //mode bits clear
        assertEquals(0, mapper.controlRegister() & 0x0C, "sanity: mode bits really are clear before reset");

        mapper.write(0x8000, 0x80); //reset bit

        assertEquals(0x0C, mapper.controlRegister() & 0x0C, "reset must set (OR in), not AND, the mode bits");
    }

    @Test
    public void resetBitDoesNotCountAsAShiftItself(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        mapper.write(0x8000, 0x80); //reset - must not itself be treated as write 1 of 5
        writeFiveBits(mapper, 0xE000, 0x0A);

        assertEquals(0x0A, mapper.prgBankRegister());
    }

    @Test
    public void prgBankMode3SwitchesFirstWindowAndFixesLastBankAtSecondWindow(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8)); //128KB, like apu_test.nes
        writeFiveBits(mapper, 0x8000, 0x0C); //control: mode 3 (reset default too, but be explicit)
        writeFiveBits(mapper, 0xE000, 0x03); //select bank 3 for the switchable window

        assertEquals(3, mapper.read(0x8000), "switchable $8000 window should read bank 3");
        assertEquals(7, mapper.read(0xC000), "fixed $C000 window should always read the last bank (7)");
    }

    @Test
    public void prgBankMode2FixesFirstBankAndSwitchesSecondWindow(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8));
        writeFiveBits(mapper, 0x8000, 0x08); //control: mode 2 (bits 2-3 = 10)
        writeFiveBits(mapper, 0xE000, 0x05); //select bank 5 for the switchable window

        assertEquals(0, mapper.read(0x8000), "fixed $8000 window should always read bank 0");
        assertEquals(5, mapper.read(0xC000), "switchable $C000 window should read bank 5");
    }

    @Test
    public void prgBankMode0Selects32KbBankIgnoringLowBitOfBankNumber(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8));
        writeFiveBits(mapper, 0x8000, 0x00); //control: mode 0 (32KB switch)
        writeFiveBits(mapper, 0xE000, 0x05); //bank number 5 -> bit 0 ignored -> 32KB bank index 2

        //32KB bank index 2 spans 16KB banks 4 and 5
        assertEquals(4, mapper.read(0x8000), "start of the 32KB window should read the even 16KB bank (4)");
        assertEquals(5, mapper.read(0xC000), "second half of the 32KB window should read the odd 16KB bank (5)");
    }

    @Test
    public void prgBankMode1AlsoSelects32KbBank(){
        //control bits 2-3 = 01 (mode 1) - the other "switch 32KB" encoding besides mode 0
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8));
        writeFiveBits(mapper, 0x8000, 0x04);
        writeFiveBits(mapper, 0xE000, 0x05); //bank number 5 -> bit 0 ignored -> 32KB bank index 2

        assertEquals(4, mapper.read(0x8000), "mode 1 should behave like mode 0: 32KB switch");
        assertEquals(5, mapper.read(0xC000));
    }

    @Test
    public void prgBankMode0PicksTheCorrect32KbBankForABankNumberAboveHalfTheTotal(){
        //bankNumber=15 (max 4-bit value) with 8 total 16KB banks (4 possible 32KB banks, 0-3):
        //(15>>1)%4 = 3 - chosen specifically so a >>/<< or /2 vs *2 mistake would pick a different,
        //observably-different bank (a smaller test value can coincidentally survive such a mutation)
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8));
        writeFiveBits(mapper, 0x8000, 0x00);
        writeFiveBits(mapper, 0xE000, 0x0F);

        assertEquals(6, mapper.read(0x8000), "32KB bank index 3 spans 16KB banks 6-7");
        assertEquals(7, mapper.read(0xC000));
    }

    @Test
    public void prgBankMode0DoesNotCrashOnASingleBankRomAndMirrorsIt(){
        //a lone 16KB bank in mode 0/1 (32KB switch) used to compute a $C000 offset (0x4000-0x7FFF)
        //past the end of a 16384-byte prgRom array, throwing ArrayIndexOutOfBoundsException - note
        //the reset-default mode (3) already handles a single bank fine, so mode 0 must be latched
        //explicitly for this test to actually reach the vulnerable branch
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedBanks(1));
        writeFiveBits(mapper, 0x8000, 0x00); //control: mode 0

        assertEquals(1, mapper.read(0x8001));
        assertEquals(1, mapper.read(0xC001), "the single bank's byte 1 should mirror into the second half of the window");
    }

    @Test
    public void prgBankMode3PreservesNonZeroOffsetWithinTheSwitchableWindow(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedBanks(8));
        writeFiveBits(mapper, 0x8000, 0x0C); //mode 3
        writeFiveBits(mapper, 0xE000, 0x02); //bank 2 switchable at $8000

        //bank 2 starts at global PRG offset 0x8000 (32768); offset+1 = 32769, &0xFF = 1
        assertEquals(1, mapper.read(0x8001));
    }

    @Test
    public void prgBankMode3PreservesNonZeroOffsetWithinTheFixedWindow(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedBanks(8));
        writeFiveBits(mapper, 0x8000, 0x0C); //mode 3: $C000 fixed to the last bank (7)

        //bank 7 starts at global PRG offset 7*0x4000 (114688); offset+1 = 114689, &0xFF = 1
        assertEquals(1, mapper.read(0xC001));
    }

    @Test
    public void prgRamReadsBackWhatWasWritten(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        mapper.write(0x6000, 0x42);
        mapper.write(0x7FFF, 0x99);

        assertEquals(0x42, mapper.read(0x6000));
        assertEquals(0x99, mapper.read(0x7FFF));
    }

    @Test
    public void prgRamIsIndependentOfPrgRomBanking(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(8));
        mapper.write(0x6000, 0x55);

        writeFiveBits(mapper, 0x8000, 0x0C);
        writeFiveBits(mapper, 0xE000, 0x07); //switch PRG banks around

        assertEquals(0x55, mapper.read(0x6000), "PRG-RAM must be unaffected by PRG-ROM bank switching");
    }

    @Test
    public void rejectsZeroLengthPrgRom(){
        assertThrows(IllegalArgumentException.class, () -> new Mmc1Mapper(romWithBanks(0)));
    }

    @Test
    public void chrEightKbModeSwitchesTheWholeWindowIgnoringTheBankRegistersLowBit(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedChrBanks(4)); //4 x 4KB = 16KB CHR-ROM
        writeFiveBits(mapper, 0x8000, 0x00); //control: bit4 clear -> 8KB mode
        writeFiveBits(mapper, 0xA000, 0x03); //bank number 3 -> bit0 ignored -> 8KB bank index 1

        //8KB bank 1 starts at global CHR offset 0x2000; offset+1 = 0x2001, &0xFF = 1
        assertEquals(1, mapper.readChr(0x0001));
        assertEquals(1, mapper.readChr(0x1001), "second half of the 8KB window should still read the same bank");
    }

    @Test
    public void chrFourKbModeSwitchesEachHalfIndependently(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedChrBanks(4));
        writeFiveBits(mapper, 0x8000, 0x10); //control: bit4 set -> 4KB mode
        writeFiveBits(mapper, 0xA000, 0x01); //4KB bank 1 at $0000-$0FFF
        writeFiveBits(mapper, 0xC000, 0x03); //4KB bank 3 at $1000-$1FFF

        //bank 1 starts at global CHR offset 0x1000; bank 3 starts at 0x3000
        assertEquals(0, mapper.readChr(0x0000), "bank 1's first byte, offset 0x1000 & 0xFF = 0");
        assertEquals(0, mapper.readChr(0x1000), "bank 3's first byte, offset 0x3000 & 0xFF = 0");
        assertEquals(1, mapper.readChr(0x1001), "bank 3's second byte, offset 0x3001 & 0xFF = 1");
    }

    @Test
    public void chrEightKbModeSelectsTheCorrectEightKbBankAmongSeveral(){
        //8 x 4KB banks = 4 x 8KB banks (indices 0-3, each spanning two 4KB banks)
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithIndexedChrBanks(8));
        writeFiveBits(mapper, 0x8000, 0x00); //control: bit4 clear -> 8KB mode
        writeFiveBits(mapper, 0xA000, 0x07); //bank number 7 -> bit0 ignored -> 8KB bank index 3

        assertEquals(6, mapper.readChr(0x0000), "8KB bank 3's first half is 4KB bank 6");
        assertEquals(7, mapper.readChr(0x1000), "8KB bank 3's second half is 4KB bank 7");
    }

    @Test
    public void chrFourKbModeSelectsIndependentBanksForEachHalf(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithIndexedChrBanks(8));
        writeFiveBits(mapper, 0x8000, 0x10); //control: bit4 set -> 4KB mode
        writeFiveBits(mapper, 0xA000, 0x05); //bank 5 at $0000-$0FFF
        writeFiveBits(mapper, 0xC000, 0x02); //bank 2 at $1000-$1FFF

        assertEquals(5, mapper.readChr(0x0000));
        assertEquals(2, mapper.readChr(0x1000));
    }

    @Test
    public void chrFourKbModeWrapsTheBankNumberModuloTheTotalBankCount(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithIndexedChrBanks(4)); //banks 0-3 only
        writeFiveBits(mapper, 0x8000, 0x10); //4KB mode
        writeFiveBits(mapper, 0xA000, 0x06); //bank register 6 -> 6 % 4 = 2

        assertEquals(2, mapper.readChr(0x0000), "bank number must wrap modulo the total bank count");
    }

    @Test
    public void noChrBanksMeansWritableChrRamIgnoringBankRegisters(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2)); //romWithBanks has no CHR data

        mapper.writeChr(0x0100, 0x42);

        assertEquals(0x42, mapper.readChr(0x0100));
    }

    @Test
    public void writesToChrRomAreNoOps(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithPositionEncodedChrBanks(2));

        mapper.writeChr(0x0000, 0x99);

        assertEquals(0, mapper.readChr(0x0000), "CHR-ROM has nothing to write to");
    }

    @Test
    public void mirroringControlBitsZeroAndOneMeanFixedSingleScreens(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0x8000, 0x00);
        assertEquals(Mirroring.SINGLE_SCREEN_LOWER, mapper.nametableMirroring());

        writeFiveBits(mapper, 0x8000, 0x01);
        assertEquals(Mirroring.SINGLE_SCREEN_UPPER, mapper.nametableMirroring());
    }

    @Test
    public void mirroringControlBitsTwoAndThreeMeanVerticalAndHorizontal(){
        final Mmc1Mapper mapper = new Mmc1Mapper(romWithBanks(2));

        writeFiveBits(mapper, 0x8000, 0x02);
        assertEquals(Mirroring.VERTICAL, mapper.nametableMirroring());

        writeFiveBits(mapper, 0x8000, 0x03);
        assertEquals(Mirroring.HORIZONTAL, mapper.nametableMirroring());
    }
}
