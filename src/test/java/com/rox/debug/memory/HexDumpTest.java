package com.rox.debug.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HexDumpTest {
    @Test
    public void rowsAreLabelledWithAbsoluteAddresses(){
        assertEquals("""
                8000: 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F
                8010: 10 11
                """, HexDump.format(address -> address & 0xFF, 0x8000, 0x8011, false));
    }

    @Test
    public void asciiColumnShowsPrintableBytesAndDotsForTheRest(){
        final int[] bytes = {'H', 'i', 0x1F, 0x20, 0x7E, 0x7F, 0xFF};

        assertEquals("0000: 48 69 1F 20 7E 7F FF  |Hi. ~..|\n", HexDump.format(address -> bytes[address], 0, 6, true));
    }

    @Test
    public void aSingleByteRangeIsOneShortRow(){
        assertEquals("01FF: AB\n", HexDump.format(address -> 0xAB, 0x01FF, 0x01FF, false));
    }

    @Test
    public void onlyReadsAddressesInTheRange(){
        final int[] reads = new int[0x10000];

        HexDump.format(address -> { reads[address]++; return 0; }, 0x0100, 0x011F, true);

        assertEquals(0, reads[0x00FF]);
        assertEquals(1, reads[0x0100]);
        assertEquals(1, reads[0x011F]);
        assertEquals(0, reads[0x0120]);
    }

    @Test
    public void eachRowHasItsOwnAsciiColumn(){
        assertEquals("""
                0000: 41 41 41 41 41 41 41 41 41 41 41 41 41 41 41 41  |AAAAAAAAAAAAAAAA|
                0010: 42  |B|
                """, HexDump.format(address -> address < 0x10 ? 'A' : 'B', 0x0000, 0x0010, true));
    }
}
