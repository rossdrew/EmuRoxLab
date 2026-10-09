package com.rox.cartridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.rox.RecordAssertions.assertRecordsEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot/restore for every {@link Mapper}: each round trip goes into a <em>fresh</em> board, with the
 * original put into a state where every field differs from a fresh board's, so a field either half of
 * the pair forgets shows up as a mismatch.
 */
public class MapperSnapshotTest {
    private static final int PRG_BANK_16KB = 0x4000;
    private static final int CHR_BANK_8KB = 0x2000;

    /** iNES file with {@code mapperNumber}, PRG-ROM bytes position-encoded (byte = offset / 256) so bank switches are visible. */
    private static INesRom rom(final int mapperNumber, final int prgBanks16kb, final int chrBanks8kb){
        final byte[] fileBytes = new byte[16 + prgBanks16kb * PRG_BANK_16KB + chrBanks8kb * CHR_BANK_8KB];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) prgBanks16kb;
        fileBytes[5] = (byte) chrBanks8kb;
        fileBytes[6] = (byte) ((mapperNumber & 0x0F) << 4);
        for (int i = 0; i < prgBanks16kb * PRG_BANK_16KB; i++){
            fileBytes[16 + i] = (byte) (i / 0x100);
        }
        return INesRom.parse(fileBytes);
    }

    private static void writeFiveBits(final Mmc1Mapper mapper, final int address, final int fiveBitValue){
        for (int i = 0; i < 5; i++){
            mapper.write(address, (fiveBitValue >> i) & 1);
        }
    }

    // --- NROM ---

    @Test
    public void nromRoundTripsItsRam(){
        final NromMapper original = new NromMapper(rom(0, 1, 0)); //CHR-RAM board
        original.write(0x6000, 0x12);
        original.writeChr(0x0010, 0x34);
        final NromMapper restored = new NromMapper(rom(0, 1, 0));

        restored.restore(original.snapshot());

        assertRecordsEqual(original.snapshot(), restored.snapshot());
        assertEquals(0x12, restored.read(0x6000));
        assertEquals(0x34, restored.readChr(0x0010));
    }

    @Test
    public void chrRomBoardSnapshotsAnEmptyChrRamAndRestoresFine(){
        final NromMapper original = new NromMapper(rom(0, 1, 1)); //CHR-ROM board
        final NromMapper restored = new NromMapper(rom(0, 1, 1));

        final MapperSnapshot snapshot = original.snapshot();
        restored.restore(snapshot);

        assertEquals(0, snapshot.chrRam().length);
    }

    @Test
    public void snapshotIsACopyNotALiveView(){
        final NromMapper mapper = new NromMapper(rom(0, 1, 0));
        final MapperSnapshot snapshot = mapper.snapshot();

        mapper.write(0x6000, 0x55);
        mapper.writeChr(0x0000, 0x66);

        assertEquals(0, snapshot.prgRam()[0]);
        assertEquals(0, snapshot.chrRam()[0]);
    }

    @Test
    public void restoreCopiesRatherThanKeepingTheSnapshotsArrays(){
        final NromMapper mapper = new NromMapper(rom(0, 1, 0));
        final MapperSnapshot snapshot = new NromMapperSnapshot(new int[0x2000], new int[0x2000]);

        mapper.restore(snapshot);
        snapshot.prgRam()[0] = 0x77;
        snapshot.chrRam()[0] = 0x88;

        assertEquals(0, mapper.read(0x6000));
        assertEquals(0, mapper.readChr(0x0000));
    }

    @Test
    public void restoreRejectsAChrRamSnapshotOnAChrRomBoard(){
        final NromMapper chrRamBoard = new NromMapper(rom(0, 1, 0));
        final NromMapper chrRomBoard = new NromMapper(rom(0, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> chrRomBoard.restore(chrRamBoard.snapshot()));
    }

    @Test
    public void restoreRejectsAChrRomSnapshotOnAChrRamBoard(){
        final NromMapper chrRamBoard = new NromMapper(rom(0, 1, 0));
        final NromMapper chrRomBoard = new NromMapper(rom(0, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> chrRamBoard.restore(chrRomBoard.snapshot()));
    }

    @Test
    public void restoreRejectsTheWrongPrgRamSize(){
        final NromMapper mapper = new NromMapper(rom(0, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> mapper.restore(new NromMapperSnapshot(new int[0x1000], new int[0])));
    }

    @Test
    public void restoreRejectsAnotherBoardsSnapshot(){
        final NromMapper nrom = new NromMapper(rom(0, 1, 0));
        final Mmc1Mapper mmc1 = new Mmc1Mapper(rom(1, 2, 0));
        final Mmc3Mapper mmc3 = new Mmc3Mapper(rom(4, 2, 0));

        assertThrows(IllegalArgumentException.class, () -> nrom.restore(mmc1.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> mmc1.restore(mmc3.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> mmc3.restore(nrom.snapshot()));
    }

    // --- MMC1 ---

    /** Every register non-default, and the shift register part-way through a 5-bit write. */
    private static Mmc1Mapper mmc1InADistinctiveState(){
        final Mmc1Mapper mapper = new Mmc1Mapper(rom(1, 4, 0));
        mapper.write(0x6000, 0x12);
        mapper.writeChr(0x0010, 0x34);
        writeFiveBits(mapper, 0x8000, 0x1F); //control: CHR 4KB mode, PRG mode 3, horizontal mirroring
        writeFiveBits(mapper, 0xA000, 0x03);
        writeFiveBits(mapper, 0xC000, 0x05);
        writeFiveBits(mapper, 0xE000, 0x02);
        mapper.write(0xE000, 1);
        mapper.write(0xE000, 1); //shift register now holds 0b11, 2 of 5 bits in
        return mapper;
    }

    @Test
    public void mmc1RoundTripsEveryRegisterIncludingAPartFilledShiftRegister(){
        final Mmc1Mapper original = mmc1InADistinctiveState();
        final Mmc1Mapper restored = new Mmc1Mapper(rom(1, 4, 0));

        restored.restore(original.snapshot());

        assertRecordsEqual(original.snapshot(), restored.snapshot());
    }

    @Test
    public void restoredMmc1FinishesThePartFilledWriteTheSameWayTheOriginalDoes(){
        final Mmc1Mapper original = mmc1InADistinctiveState();
        final Mmc1Mapper restored = new Mmc1Mapper(rom(1, 4, 0));
        restored.restore(original.snapshot());

        for (final Mmc1Mapper mapper : new Mmc1Mapper[]{original, restored}){
            mapper.write(0xE000, 0);
            mapper.write(0xE000, 0);
            mapper.write(0xE000, 0); //5th bit - latches PRG bank 3 (0b00011)
        }

        assertEquals(original.read(0x8000), restored.read(0x8000));
        assertRecordsEqual(original.snapshot(), restored.snapshot());
    }

    // --- MMC3 ---

    /** R0-R7, bank select (PRG mode 1 + CHR inversion) and mirroring all non-default, RAM written. */
    private static Mmc3Mapper mmc3WithBanksSet(){
        final Mmc3Mapper mapper = new Mmc3Mapper(rom(4, 4, 0));
        mapper.write(0x6000, 0x12);
        mapper.writeChr(0x0010, 0x34);
        for (int register = 0; register < 8; register++){
            mapper.write(0x8000, register);
            mapper.write(0x8001, 2 * register + 2);
        }
        mapper.write(0x8000, 0xC5);
        mapper.write(0xA000, 1); //vertical - a fresh board here starts horizontal
        return mapper;
    }

    @Test
    public void mmc3RoundTripsBanksAndACountingIrq(){
        final Mmc3Mapper original = mmc3WithBanksSet();
        original.write(0xC000, 0x20); //latch
        original.write(0xE001, 0); //enable
        original.readChr(0x1000); //A12 rising edge: counter reloads to $20, A12 left high
        final Mmc3Mapper restored = new Mmc3Mapper(rom(4, 4, 0));

        restored.restore(original.snapshot());

        assertRecordsEqual(original.snapshot(), restored.snapshot());
        assertEquals(original.nametableMirroring(), restored.nametableMirroring());
        for (int address = 0x8000; address < 0x10000; address += 0x2000){
            assertEquals(original.read(address), restored.read(address), String.format("PRG window at $%04X", address));
        }
    }

    @Test
    public void mmc3RoundTripsAPendingIrqAndAReloadRequest(){
        final Mmc3Mapper original = mmc3WithBanksSet();
        original.write(0xC000, 0x00); //latch 0: the next reload leaves the counter at 0, firing the IRQ
        original.write(0xE001, 0);
        original.readChr(0x1000);
        original.write(0xC001, 0); //reload requested (and counter cleared)
        assertTrue(original.isIrqAsserted(), "test setup: expected a pending IRQ");
        final Mmc3Mapper restored = new Mmc3Mapper(rom(4, 4, 0));

        restored.restore(original.snapshot());

        assertRecordsEqual(original.snapshot(), restored.snapshot());
        assertTrue(restored.isIrqAsserted());
    }

    @Test
    public void mmc3SnapshotsBankRegistersAsACopy(){
        final Mmc3Mapper mapper = mmc3WithBanksSet();
        final Mmc3MapperSnapshot snapshot = (Mmc3MapperSnapshot) mapper.snapshot();

        mapper.write(0x8000, 0);
        mapper.write(0x8001, 0x40);

        assertEquals(2, snapshot.bankRegisters()[0]);
    }

    @Test
    public void mmc3RejectsTheWrongNumberOfBankRegisters(){
        final Mmc3Mapper mapper = new Mmc3Mapper(rom(4, 4, 0));
        final Mmc3MapperSnapshot snapshot = (Mmc3MapperSnapshot) mapper.snapshot();
        final Mmc3MapperSnapshot broken = new Mmc3MapperSnapshot(snapshot.prgRam(), snapshot.chrRam(), new int[7],
                0, false, 0, 0, false, false, false, false);

        assertThrows(IllegalArgumentException.class, () -> mapper.restore(broken));
    }

    /** A snapshot of {@code mapper}'s own type with the named array one element too short and every other value changed. */
    private static MapperSnapshot withOneArrayTooShort(final MapperSnapshot snapshot, final String which){
        final int[] prgRam = which.equals("prgRam") ? new int[snapshot.prgRam().length - 1] : new int[snapshot.prgRam().length];
        final int[] chrRam = which.equals("chrRam") ? new int[snapshot.chrRam().length - 1] : new int[snapshot.chrRam().length];
        if (prgRam.length > 0){
            prgRam[0] = 0x99; //differs from the 0x12 each test mapper starts with
        }
        return switch (snapshot){
            case NromMapperSnapshot ignored -> new NromMapperSnapshot(prgRam, chrRam);
            case Mmc1MapperSnapshot ignored -> new Mmc1MapperSnapshot(prgRam, chrRam, 1, 2, 3, 4, 5, 6);
            case Mmc3MapperSnapshot ignored -> new Mmc3MapperSnapshot(prgRam, chrRam,
                    which.equals("bankRegisters") ? new int[7] : new int[8], 1, true, 2, 3, true, true, true, true);
        };
    }

    /**
     * CodeRabbit's PR #41 finding: whichever array is wrong, the restore is rejected before anything -
     * including PRG-RAM, copied first - is overwritten.
     */
    @ParameterizedTest
    @CsvSource({"nrom,prgRam", "nrom,chrRam", "mmc1,prgRam", "mmc1,chrRam", "mmc3,prgRam", "mmc3,chrRam", "mmc3,bankRegisters"})
    public void rejectedRestoreChangesNothingWhicheverArrayIsWrong(final String board, final String which){
        final Mapper mapper = switch (board){
            case "nrom" -> new NromMapper(rom(0, 1, 0));
            case "mmc1" -> new Mmc1Mapper(rom(1, 2, 0));
            default -> new Mmc3Mapper(rom(4, 2, 0));
        };
        mapper.write(0x6000, 0x12);
        final MapperSnapshot before = mapper.snapshot();

        assertThrows(IllegalArgumentException.class, () -> mapper.restore(withOneArrayTooShort(before, which)));
        assertRecordsEqual(before, mapper.snapshot());
    }

    @Test
    public void checkRestorableAcceptsAMatchingSnapshotAndRejectsAMismatchedOneWithoutChangingAnything(){
        for (final Mapper mapper : new Mapper[]{new NromMapper(rom(0, 1, 0)), new Mmc1Mapper(rom(1, 2, 0)), new Mmc3Mapper(rom(4, 2, 0))}){
            mapper.write(0x6000, 0x34);
            final MapperSnapshot before = mapper.snapshot();

            mapper.checkRestorable(before);
            assertThrows(IllegalArgumentException.class,
                    () -> mapper.checkRestorable(new NromMapperSnapshot(new int[0x1000], new int[0])), mapper.getClass().getSimpleName());

            assertRecordsEqual(before, mapper.snapshot());
        }
    }
}
