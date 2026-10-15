package com.rox.cartridge;

import com.rox.TestRoms;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChrMemoryTest {
    private static final int RAM_SIZE = 0x2000;

    /** One 16KB PRG bank, plus 8KB of CHR-ROM (first byte $FF) if {@code withChrRom}. */
    private static INesRom rom(final boolean withChrRom){
        return withChrRom ? TestRoms.nrom().chrSize(0x2000).chr(0, 0xFF).rom() : TestRoms.nrom().rom();
    }

    @Test
    public void chrRomIsReadUnsignedAndIgnoresWrites(){
        final ChrMemory chr = ChrMemory.of(rom(true), RAM_SIZE);

        chr.write(0, 0x12);

        assertFalse(chr.isRam());
        assertEquals(0xFF, chr.read(0));
    }

    @Test
    public void withoutChrRomItsRamOfTheGivenSize(){
        final ChrMemory chr = ChrMemory.of(rom(false), 0x1000);

        chr.write(0x0FFF, 0x1AB);

        assertTrue(chr.isRam());
        assertEquals(0x1000, chr.size());
        assertEquals(0xAB, chr.read(0x0FFF), "kept to a byte");
    }

    @Test
    public void chrRomHasNothingToSnapshotOrRestore(){
        final ChrMemory chr = ChrMemory.of(rom(true), RAM_SIZE);

        assertArrayEquals(new int[0], chr.snapshot());
        chr.restore(new int[0]);
        assertEquals(0xFF, chr.read(0));
        assertThrows(IllegalArgumentException.class, () -> chr.checkRestorable(new int[RAM_SIZE]));
    }

    @Test
    public void chrRamSnapshotsAreCopiesThatRestore(){
        final ChrMemory chr = ChrMemory.of(rom(false), RAM_SIZE);
        chr.write(5, 0x42);
        final int[] saved = chr.snapshot();
        chr.write(5, 0x00);

        chr.checkRestorable(saved);
        chr.restore(saved);

        assertEquals(0x42, chr.read(5));
        assertThrows(IllegalArgumentException.class, () -> chr.checkRestorable(new int[0]));
    }
}
