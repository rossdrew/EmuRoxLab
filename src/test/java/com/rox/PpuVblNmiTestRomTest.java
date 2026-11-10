package com.rox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * blargg's ppu_vbl_nmi (https://github.com/christopherpow/nes-test-roms/tree/master/ppu_vbl_nmi):
 * when the vblank flag sets/clears and NMI fires, to the PPU clock. Expectations are today's results -
 * see {@link BlarggRomRunner}. A plain "Failed" (status 1) prints a table then a CRC of it; the CRC is
 * pinned, so any timing change shows up.
 *
 * <ul>
 *     <li>02, 05-08: vblank flag / NMI timing off by a clock or more</li>
 *     <li>04 #11: an NMI enabled during vblank should fire after the NEXT instruction</li>
 *     <li>09, 10: odd-frame dot skip isn't modeled</li>
 * </ul>
 */
@Tag("testRom")
class PpuVblNmiTestRomTest {
    private static final long TICK_BUDGET = 20_000_000;

    @ParameterizedTest(name = "{0} -> status {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "01-vbl_basics.nes      | 0  | Passed",
            "02-vbl_set_time.nes    | 1  | 4103C340",
            "03-vbl_clear_time.nes  | 0  | Passed",
            "04-nmi_control.nes     | 11 | Immediate occurence should be after NEXT instruction",
            "05-nmi_timing.nes      | 1  | 1B41CAA0",
            "06-suppression.nes     | 1  | 3FE15516",
            "07-nmi_on_timing.nes   | 1  | 2B1F5269",
            "08-nmi_off_timing.nes  | 1  | 6CB785AD",
            "09-even_odd_frames.nes | 3  | Pattern ---BB should skip 1 clock",
            "10-even_odd_timing.nes | 2  | Clock is skipped too soon, relative to enabling BG"
    })
    void reportsTodaysResult(final String rom, final int status, final String text) throws Exception {
        BlarggRomRunner.assertReports("ppu_vbl_nmi/rom_singles/" + rom, status, text, TICK_BUDGET);
    }
}
