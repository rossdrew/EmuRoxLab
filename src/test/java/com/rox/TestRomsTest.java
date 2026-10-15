package com.rox;

import com.rox.cartridge.INesRom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRomsTest {
    @Test
    public void nromIsOneBlankPrgBankAndNoChr(){
        final byte[] file = TestRoms.nrom().bytes();

        assertArrayEquals(new byte[]{'N', 'E', 'S', 0x1A, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, java.util.Arrays.copyOf(file, 16));
        assertEquals(16 + 0x4000, file.length);
    }

    @Test
    public void headerCarriesMapperBothNibblesSizesAndFlags(){
        final INesRom rom = TestRoms.mapper(0x14).prgSize(0x8000).chrSize(0x4000).verticalMirroring(true).battery().rom();

        assertEquals(0x14, rom.mapperNumber());
        assertEquals(0x8000, rom.prgRom().length);
        assertEquals(0x4000, rom.chrRom().length);
        assertTrue(rom.isVerticalMirroring());
        assertTrue(rom.hasBattery());
    }

    @Test
    public void flagsAreOffUnlessAsked(){
        final INesRom rom = TestRoms.nrom().rom();

        assertFalse(rom.isVerticalMirroring());
        assertFalse(rom.hasBattery());
    }

    @Test
    public void undersizedPrgIsHeadedInWholeBanksRoundingDown(){
        final byte[] file = TestRoms.mapper(4).prgSize(0x2000).bytes();

        assertEquals(0, file[4]);
        assertEquals(16 + 0x2000, file.length);
    }

    @Test
    public void atPlacesBytesByCpuAddressMirroringASmallRom(){
        final byte[] prg = TestRoms.nrom().at(0x9000, 0x4C, 0x00, 0x90).resetVector(0x9123).nmiVector(0x9456).rom().prgRom();

        assertEquals(0x4C, prg[0x1000] & 0xFF);
        assertEquals(0x90, prg[0x1002] & 0xFF);
        assertEquals(0x23, prg[0x3FFC] & 0xFF, "$FFFC mirrors into a 16KB bank's last bytes");
        assertEquals(0x91, prg[0x3FFD] & 0xFF);
        assertEquals(0x56, prg[0x3FFA] & 0xFF);
        assertEquals(0x94, prg[0x3FFB] & 0xFF);
    }

    @Test
    public void prgChrAndFillsWriteWhereTheySay(){
        final INesRom rom = TestRoms.nrom().chrSize(0x2000).fillPrg(offset -> offset).prg(5, 0xAA).fillChr(offset -> offset >> 8).chr(1, 0xBB).rom();

        assertEquals(0x04, rom.prgRom()[4] & 0xFF);
        assertEquals(0xAA, rom.prgRom()[5] & 0xFF);
        assertEquals(0x01, rom.prgRom()[0x101] & 0xFF, "low byte of the offset");
        assertEquals(0x10, rom.chrRom()[0x1000] & 0xFF);
        assertEquals(0xBB, rom.chrRom()[1] & 0xFF);
    }

    @Test
    public void markingBanksSetsOnlyEachBanksFirstByteToItsIndex(){
        final INesRom rom = TestRoms.nrom().prgSize(0x8000).markPrgBanks(0x2000).chrSize(0x2000).markChrBanks(0x400).rom();

        assertEquals(3, rom.prgRom()[3 * 0x2000]);
        assertEquals(0, rom.prgRom()[3 * 0x2000 + 1]);
        assertEquals(7, rom.chrRom()[7 * 0x400]);
        assertEquals(0, rom.chrRom()[7 * 0x400 - 1]);
    }

    @Test
    public void resizingKeepsWhatFits(){
        final INesRom rom = TestRoms.nrom().prg(0, 0x11).prgSize(0x8000).rom();

        assertEquals(0x11, rom.prgRom()[0]);
    }

    @Test
    public void cartridgeLoadsThroughTheRealLoader(){
        assertEquals(0x4C, TestRoms.nrom().at(0x8000, 0x4C).cartridge().read(0x8000));
    }
}
