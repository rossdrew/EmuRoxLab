package com.rox.debug.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    // --- addressAt ---

    @Test
    public void addressAtFindsTheByteUnderAnyOfItsHexColumnsAndItsAsciiColumn(){
        //row 0 of "8000: 00 01 ..  |................|": byte $8001's hex is " 01" at columns 8-10, ASCII at column 57
        for (final int column : new int[]{8, 9, 10, 57}){
            assertEquals(OptionalInt.of(0x8001), HexDump.addressAt(column, 0x8000, 0x801F, true), "column " + column);
        }
    }

    @ParameterizedTest
    @CsvSource({"0", "4", "53", "55", "72", "73"}) //address, ':', between hex and ASCII, the closing '|', newline
    public void addressAtIsEmptyOffTheBytes(final int column){
        assertEquals(OptionalInt.empty(), HexDump.addressAt(column, 0x8000, 0x801F, true));
    }

    @Test
    public void addressAtCountsWholeRowsDown(){
        final int rowLength = 74; //"8000:" + 16 x " 00" + "  |" + 16 + "|" + newline

        assertEquals(OptionalInt.of(0x8010), HexDump.addressAt(rowLength + 5, 0x8000, 0x801F, true));
        assertEquals(OptionalInt.of(0x801F), HexDump.addressAt(rowLength + 56 + 15, 0x8000, 0x801F, true));
    }

    @Test
    public void addressAtWithoutAsciiHasShorterRowsAndNoAsciiColumn(){
        final int rowLength = 54; //"8000:" + 16 x " 00" + newline

        assertEquals(OptionalInt.of(0x8011), HexDump.addressAt(rowLength + 8, 0x8000, 0x801F, false));
        assertEquals(OptionalInt.empty(), HexDump.addressAt(57, 0x8000, 0x801F, false), "no ASCII column to land in");
    }

    @Test
    public void addressAtStopsAtAShortLastRowsEnd(){
        //last row "8010: 10 11  |..|" - 2 bytes, so its ASCII column starts right after them
        final int lastRow = 74;

        assertEquals(OptionalInt.of(0x8011), HexDump.addressAt(lastRow + 10, 0x8000, 0x8011, true));
        assertEquals(OptionalInt.empty(), HexDump.addressAt(lastRow + 11, 0x8000, 0x8011, true), "past the last hex byte");
        assertEquals(OptionalInt.of(0x8010), HexDump.addressAt(lastRow + 14, 0x8000, 0x8011, true), "first ASCII char");
        assertEquals(OptionalInt.of(0x8011), HexDump.addressAt(lastRow + 15, 0x8000, 0x8011, true));
        assertEquals(OptionalInt.empty(), HexDump.addressAt(lastRow + 16, 0x8000, 0x8011, true), "its closing '|'");
    }

    @Test
    public void addressAtFindsALastRowOfExactlyOneByte(){
        assertEquals(OptionalInt.of(0x8010), HexDump.addressAt(74 + 5, 0x8000, 0x8010, true));
    }

    @Test
    public void addressAtIsEmptyBeforeAndAfterTheText(){
        assertEquals(OptionalInt.empty(), HexDump.addressAt(-1, 0x8000, 0x801F, true));
        assertEquals(OptionalInt.empty(), HexDump.addressAt(2 * 74, 0x8000, 0x801F, true), "a row past the end");
    }

    /** Every byte, everywhere in the text: its 3 hex columns and its ASCII column map to it, and nothing else does. */
    @Test
    public void addressAtAgreesWithFormatEverywhere(){
        for (final boolean ascii : new boolean[]{true, false}){
            final String text = HexDump.format(address -> address & 0xFF, 0x0123, 0x0167, ascii);
            final Map<Integer, Integer> hits = new HashMap<>();
            for (int offset = 0; offset < text.length(); offset++){
                final OptionalInt address = HexDump.addressAt(offset, 0x0123, 0x0167, ascii);
                if (address.isPresent()){
                    final char shown = text.charAt(offset);
                    final String hex = String.format("%02X", address.getAsInt() & 0xFF);
                    final boolean plausible = shown == ' ' || hex.indexOf(shown) >= 0 || shown == '.' || shown == (char) (address.getAsInt() & 0xFF);
                    assertEquals(true, plausible, "offset " + offset + " ('" + shown + "') -> " + address);
                    hits.merge(address.getAsInt(), 1, Integer::sum);
                }
            }
            for (int address = 0x0123; address <= 0x0167; address++){
                assertEquals(ascii ? 4 : 3, hits.get(address), String.format("$%04X (ascii=%s)", address, ascii));
            }
            assertEquals(0x0167 - 0x0123 + 1, hits.size());
        }
    }

    // --- parseByte ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1F|31", "$1F|31", "0x1F|31", "0X1f|31", "10|16", "0|0", "ff|255", " $A0 |160"})
    public void parseByteReadsHexWithOrWithoutAPrefix(final String text, final int expected){
        assertEquals(expected, HexDump.parseByte(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "$", "0x", "100", "1G", "+1", "-1", "$$1", "1 2"})
    public void parseByteRejectsAnythingElse(final String text){
        assertThrows(IllegalArgumentException.class, () -> HexDump.parseByte(text));
    }
}
