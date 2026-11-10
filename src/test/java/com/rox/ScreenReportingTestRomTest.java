package com.rox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * blargg's older test ROMs, which report on screen rather than through $6000 - see {@link BlarggRomRunner}.
 * Expectations are today's results.
 *
 * <ul>
 *     <li>sprite_hit_tests (https://github.com/christopherpow/nes-test-roms/tree/master/sprite_hit_tests_2005.10.05):
 *     sprite-0 hit placement, clipping, flipping and timing - all pass</li>
 *     <li>vbl_nmi_timing (https://github.com/christopherpow/nes-test-roms/tree/master/vbl_nmi_timing): frame
 *     length, vblank flag and NMI timing - only 4 passes; the failure codes are explained in its readme</li>
 *     <li>cpu_timing_test6 (https://github.com/christopherpow/nes-test-roms/tree/master/cpu_timing_test6): every
 *     official instruction's cycle count, with and without page crossing - passes</li>
 * </ul>
 */
@Tag("testRom")
class ScreenReportingTestRomTest {
    private static final long TICK_BUDGET = 20_000_000;
    //runs for 16 emulated seconds
    private static final long CPU_TIMING_TICK_BUDGET = 40_000_000;

    @ParameterizedTest
    @ValueSource(strings = {"01.basics.nes", "02.alignment.nes", "03.corners.nes", "04.flip.nes", "05.left_clip.nes",
            "06.right_edge.nes", "07.screen_bottom.nes", "08.double_height.nes", "09.timing_basics.nes",
            "10.timing_order.nes", "11.edge_timing.nes"})
    void spriteHitTestsPass(final String rom) throws Exception {
        BlarggRomRunner.assertReports("sprite_hit_tests/" + rom, 0, "PASSED", TICK_BUDGET);
    }

    @ParameterizedTest(name = "{0} -> status {1}")
    @CsvSource(delimiter = '|', value = {
            "1.frame_basics.nes     | 4 | FAILED: # 4",
            "2.vbl_timing.nes       | 8 | FAILED #8",
            "3.even_odd_frames.nes  | 3 | FAILED #3",
            "4.vbl_clear_timing.nes | 0 | PASSED",
            "5.nmi_suppression.nes  | 3 | FAILED #3",
            "6.nmi_disable.nes      | 2 | FAILED #2",
            "7.nmi_timing.nes       | 2 | FAILED #2"
    })
    void vblNmiTimingReportsTodaysResult(final String rom, final int status, final String text) throws Exception {
        BlarggRomRunner.assertReports("vbl_nmi_timing/" + rom, status, text, TICK_BUDGET);
    }

    @Test
    void cpuTimingTestPasses() throws Exception {
        BlarggRomRunner.assertReports("cpu_timing_test6/cpu_timing_test.nes", 0, "OFFICIAL INSTRUCTIONS ONLY", CPU_TIMING_TICK_BUDGET);
    }
}
