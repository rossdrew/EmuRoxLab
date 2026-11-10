package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link BlarggRomRunner}'s reading of on-screen results - the ROM runs themselves are the testRom suites. */
class BlarggRomRunnerTest {
    private static int[] nametables(final String... rows){
        final int[] nametables = new int[0x800];
        java.util.Arrays.fill(nametables, ' ');
        for (int row = 0; row < rows.length; row++){
            for (int column = 0; column < rows[row].length(); column++){
                nametables[row * 32 + column] = rows[row].charAt(column);
            }
        }
        return nametables;
    }

    @Test
    void screenTextIsTheFirstNametableAsAsciiRows(){
        assertEquals("SPRITE HIT BASICS\n\n  PASSED", BlarggRomRunner.screenText(nametables("SPRITE HIT BASICS", "", "  PASSED")));
    }

    @Test
    void tilesThatArentPrintableReadAsSpaces(){
        final int[] nametables = nametables("AB");
        nametables[1] = 0x05;
        nametables[2] = 0x7F;
        nametables[3] = 'C';

        assertEquals("A  C", BlarggRomRunner.screenText(nametables));
    }

    @Test
    void theSecondNametableIsntRead(){
        final int[] nametables = nametables();
        nametables[0x400] = 'X';

        assertEquals("", BlarggRomRunner.screenText(nametables));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "SPRITE HIT BASICS  PASSED                   | 0",
            "VBL TIMING  FAILED #8                       | 8",
            "PPU FRAME BASICS  FAILED: # 4               | 4",
            "NMI  FAILED #12                             | 12",
            "6502 TIMING TEST  FAIL OP :21 CLOCKS: 2 3   | 1",
            "nothing recognisable                        | 1"
    })
    void statusComesFromWhatWasPrinted(final String text, final int status){
        assertEquals(status, BlarggRomRunner.screenStatus(text));
    }
}
