package com.rox.cartridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NromMapperTest {

    /** Builds a ROM with each PRG-ROM byte set to its own low-order position, so offset bugs show up. */
    private static INesRom romWithPositionEncodedPrg(final int prgBanks){
        final byte[] fileBytes = new byte[16 + prgBanks * 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) prgBanks;
        for (int i = 16; i < fileBytes.length; i++){
            fileBytes[i] = (byte) ((i - 16) & 0xFF);
        }
        return INesRom.parse(fileBytes);
    }

    /** Builds a ROM with 1 PRG bank and the given number of 8KB CHR-ROM banks (0 = CHR-RAM), mirroring bit as given. */
    private static INesRom romWithChr(final int chrBanks, final boolean verticalMirroring){
        final byte[] fileBytes = new byte[16 + 16384 + chrBanks * 8192];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 1;
        fileBytes[5] = (byte) chrBanks;
        fileBytes[6] = (byte) (verticalMirroring ? 0x01 : 0x00);
        for (int i = 0; i < chrBanks * 8192; i++){
            fileBytes[16 + 16384 + i] = (byte) (i & 0xFF);
        }
        return INesRom.parse(fileBytes);
    }

    /** Builds a 32KB ROM whose two 16KB halves are filled with distinct, uniform marker bytes. */
    private static INesRom romWithDistinctHalves(final int firstHalfByte, final int secondHalfByte){
        final byte[] fileBytes = new byte[16 + 2 * 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 2;
        for (int i = 16; i < 16 + 16384; i++){
            fileBytes[i] = (byte) firstHalfByte;
        }
        for (int i = 16 + 16384; i < fileBytes.length; i++){
            fileBytes[i] = (byte) secondHalfByte;
        }
        return INesRom.parse(fileBytes);
    }

    @Test
    public void sixteenKbRomIsMirroredIntoBothHalvesOfTheWindow(){
        final NromMapper mapper = new NromMapper(romWithPositionEncodedPrg(1));

        assertEquals(0x00, mapper.read(0x8000), "PRG offset 0");
        assertEquals(0xFF, mapper.read(0xBFFF), "PRG offset 0x3FFF (16383 & 0xFF)");
        assertEquals(mapper.read(0x8000), mapper.read(0xC000), "$C000 should mirror $8000's bank");
        assertEquals(mapper.read(0xBFFF), mapper.read(0xFFFF), "$FFFF should mirror $BFFF's bank");
    }

    @Test
    public void thirtyTwoKbRomFillsTheWholeWindowWithoutMirroring(){
        final NromMapper mapper = new NromMapper(romWithDistinctHalves(0x11, 0x22));

        assertEquals(0x11, mapper.read(0x8000), "$8000 should read the first 16KB half");
        assertEquals(0x11, mapper.read(0xBFFF), "$BFFF is still within the first half");
        assertEquals(0x22, mapper.read(0xC000), "$C000 should read the second 16KB half, not mirror the first");
        assertEquals(0x22, mapper.read(0xFFFF), "$FFFF is the last byte of the second half");
    }

    @Test
    public void rejectsPrgRomThatIsNeither16Nor32KbBanks(){
        assertThrows(IllegalArgumentException.class, () -> new NromMapper(romWithPositionEncodedPrg(3)));
    }

    @Test
    public void prgRamReadsBackWhatWasWritten(){
        final NromMapper mapper = new NromMapper(romWithPositionEncodedPrg(1));

        mapper.write(0x6000, 0x42);
        mapper.write(0x7FFF, 0x99);

        assertEquals(0x42, mapper.read(0x6000));
        assertEquals(0x99, mapper.read(0x7FFF));
    }

    @Test
    public void prgRamStartsAtZero(){
        final NromMapper mapper = new NromMapper(romWithPositionEncodedPrg(1));

        assertEquals(0, mapper.read(0x6000));
    }

    @Test
    public void writesToPrgRomAreNoOps(){
        final NromMapper mapper = new NromMapper(romWithPositionEncodedPrg(1));
        final int original = mapper.read(0x8000);

        mapper.write(0x8000, original == 0x55 ? 0x66 : 0x55);

        assertEquals(original, mapper.read(0x8000), "NROM has no bank registers - writes to $8000+ must be no-ops");
        assertEquals(0, mapper.read(0x6000), "a no-op write at $8000 must not leak into PRG-RAM at $6000");
    }

    @Test
    public void writeMasksValueTo8Bits(){
        final NromMapper mapper = new NromMapper(romWithPositionEncodedPrg(1));

        mapper.write(0x6000, 0x1FF);

        assertEquals(0xFF, mapper.read(0x6000));
    }

    @Test
    public void chrRomReadsBackTheRomBytes(){
        final NromMapper mapper = new NromMapper(romWithChr(1, false));

        assertEquals(0x00, mapper.readChr(0x0000));
        assertEquals(0x42, mapper.readChr(0x0042));
    }

    @Test
    public void writesToChrRomAreNoOps(){
        final NromMapper mapper = new NromMapper(romWithChr(1, false));

        mapper.writeChr(0x0000, 0x55);

        assertEquals(0x00, mapper.readChr(0x0000), "CHR-ROM has nothing to write to");
    }

    @Test
    public void noChrBanksMeansWritableChrRam(){
        final NromMapper mapper = new NromMapper(romWithChr(0, false));

        mapper.writeChr(0x0123, 0x77);

        assertEquals(0x77, mapper.readChr(0x0123));
    }

    @Test
    public void chrRamStartsAtZero(){
        final NromMapper mapper = new NromMapper(romWithChr(0, false));

        assertEquals(0, mapper.readChr(0x0000));
    }

    @Test
    public void mirroringReflectsTheHeaderBitAtConstructionTime(){
        assertEquals(Mirroring.HORIZONTAL, new NromMapper(romWithChr(0, false)).nametableMirroring());
        assertEquals(Mirroring.VERTICAL, new NromMapper(romWithChr(0, true)).nametableMirroring());
    }
}
