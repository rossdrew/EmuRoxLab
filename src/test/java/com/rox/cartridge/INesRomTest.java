package com.rox.cartridge;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class INesRomTest {

    private static byte[] buildRom(final int prgBanks, final int chrBanks, final int flags6, final int flags7,
                                    final boolean fillWithRecognisablePattern){
        final int trainerSize = (flags6 & 0x04) != 0 ? 512 : 0;
        final int prgSize = prgBanks * 16384;
        final int chrSize = chrBanks * 8192;
        final byte[] fileBytes = new byte[16 + trainerSize + prgSize + chrSize];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = (byte) prgBanks;
        fileBytes[5] = (byte) chrBanks;
        fileBytes[6] = (byte) flags6;
        fileBytes[7] = (byte) flags7;
        if (fillWithRecognisablePattern){
            for (int i = 16 + trainerSize; i < fileBytes.length; i++){
                fileBytes[i] = (byte) (i & 0xFF);
            }
        }
        return fileBytes;
    }

    @Test
    public void parsesMapperNumberFromLowNibbleOfFlags6(){
        final byte[] rom = buildRom(1, 1, 0x10, 0x00, false); //mapper low nibble = 1, high nibble = 0
        assertEquals(1, INesRom.parse(rom).mapperNumber());
    }

    @Test
    public void parsesMapperNumberFromHighNibbleOfFlags7(){
        final byte[] rom = buildRom(1, 1, 0x00, 0x10, false); //mapper high nibble = 1<<4, low = 0
        assertEquals(16, INesRom.parse(rom).mapperNumber());
    }

    @Test
    public void combinesBothNibblesForMapper1(){
        final byte[] rom = buildRom(8, 0, 0x10, 0x00, false); //MMC1-shaped header: mapper 1
        assertEquals(1, INesRom.parse(rom).mapperNumber());
    }

    @Test
    public void verticalMirroringBitIsRead(){
        assertTrue(INesRom.parse(buildRom(1, 1, 0x01, 0x00, false)).isVerticalMirroring());
        assertFalse(INesRom.parse(buildRom(1, 1, 0x00, 0x00, false)).isVerticalMirroring());
    }

    @Test
    public void extractsPrgRomAtCorrectOffsetAndLength(){
        final byte[] rom = buildRom(2, 0, 0x00, 0x00, true);
        final byte[] prg = INesRom.parse(rom).prgRom();

        assertEquals(2 * 16384, prg.length);
        assertArrayEquals(Arrays.copyOfRange(rom, 16, 16 + prg.length), prg);
    }

    @Test
    public void extractsChrRomAtCorrectOffsetAndLength(){
        final byte[] rom = buildRom(1, 2, 0x00, 0x00, true);
        final byte[] chr = INesRom.parse(rom).chrRom();

        assertEquals(2 * 8192, chr.length);
        assertArrayEquals(Arrays.copyOfRange(rom, 16 + 16384, 16 + 16384 + chr.length), chr);
    }

    @Test
    public void prgRomGetterReturnsADefensiveCopy(){
        final INesRom rom = INesRom.parse(buildRom(1, 0, 0x00, 0x00, true));

        final byte[] firstCall = rom.prgRom();
        firstCall[0] = (byte) ~firstCall[0];

        assertNotEquals(firstCall[0], rom.prgRom()[0], "mutating a returned array must not affect the ROM's own content");
    }

    @Test
    public void chrRomGetterReturnsADefensiveCopy(){
        final INesRom rom = INesRom.parse(buildRom(1, 1, 0x00, 0x00, true));

        final byte[] firstCall = rom.chrRom();
        firstCall[0] = (byte) ~firstCall[0];

        assertNotEquals(firstCall[0], rom.chrRom()[0], "mutating a returned array must not affect the ROM's own content");
    }

    @Test
    public void zeroChrBanksProducesEmptyChrRom(){
        assertEquals(0, INesRom.parse(buildRom(1, 0, 0x00, 0x00, false)).chrRom().length);
    }

    @Test
    public void trainerShiftsPrgRomOffsetBy512Bytes(){
        final byte[] rom = buildRom(1, 0, 0x04, 0x00, true); //trainer bit set
        final byte[] prg = INesRom.parse(rom).prgRom();

        assertArrayEquals(Arrays.copyOfRange(rom, 16 + 512, 16 + 512 + prg.length), prg);
    }

    @Test
    public void rejectsFileMissingMagic(){
        final byte[] rom = buildRom(1, 1, 0x00, 0x00, false);
        rom[0] = 'X';

        assertThrows(IllegalArgumentException.class, () -> INesRom.parse(rom));
    }

    @Test
    public void rejectsFileShorterThanHeader(){
        assertThrows(IllegalArgumentException.class, () -> INesRom.parse(new byte[8]));
    }

    @Test
    public void rejectsNes2FormatHeaders(){
        //flags7 bits 2-3 = 0b10 identifies NES 2.0 - here also carrying a mapper high nibble (0x10)
        //that iNES 1.0 parsing would otherwise fold straight into a truncated, wrong mapper number
        final byte[] rom = buildRom(1, 1, 0x00, 0x18, false);

        assertThrows(IllegalArgumentException.class, () -> INesRom.parse(rom));
    }

    @Test
    public void acceptsFileExactlyTheHeaderSizeWithNoPrgOrChrBanks(){
        final byte[] rom = buildRom(0, 0, 0x00, 0x00, false);

        assertEquals(16, rom.length, "sanity check: this ROM should be exactly the header size");
        assertEquals(0, INesRom.parse(rom).prgRom().length);
    }

    @Test
    public void rejectsTruncatedPrgData(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x02, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] truncated = Arrays.copyOf(header, 16 + 100); //claims 2 PRG banks but only has 100 bytes

        assertThrows(IllegalArgumentException.class, () -> INesRom.parse(truncated));
    }

    @Test
    public void rejectsPrgTruncationEvenWhenChrIsAlsoDeclared(){
        //1 PRG bank (16384) + 1 CHR bank (8192) declared, but the file is only 16000 bytes - short
        //enough that PRG alone doesn't fit, but not so short that the subsequent CHR-ROM offset
        //(16400) would *also* land past the end of the file in a way that masks a truncation check
        //bug specifically in the PRG-ROM copy - see git history for why this one needed care
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x01, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] truncated = Arrays.copyOf(header, 16_000);

        assertThrows(IllegalArgumentException.class, () -> INesRom.parse(truncated));
    }
}
