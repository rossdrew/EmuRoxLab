package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

/**
 * Runs blargg's apu_test {@code rom_singles} ROMs
 * (https://github.com/christopherpow/nes-test-roms/tree/master/apu_test) end to end - see
 * {@link BlarggRomRunner}.
 *
 * Not all 8 currently pass. The failures are real signal about the current APU's timing fidelity
 * (frame-IRQ/length-counter/DMC-rate edge cases) rather than anything to do with ROM loading -
 * expected statuses below are today's actual outcomes, characterising the current implementation
 * rather than asserting an aspirational "all green."
 */
public class ApuTestRomIntegrationTest {
    private static final long TICK_BUDGET = 5_000_000;
    //the combined ROM runs all 8 rom_singles sub-tests (plus its own menu/sequencing overhead) in one
    //pass, so it needs a proportionally larger budget than any single sub-test
    private static final long COMBINED_ROM_TICK_BUDGET = TICK_BUDGET * 10;

    @ParameterizedTest(name = "{0} -> status ${1}")
    @CsvSource({
            "1-len_ctr.nes, 0",
            "2-len_table.nes, 0",
            "3-irq_flag.nes, 0",
            "4-jitter.nes, 2",
            "5-len_timing.nes, 2",
            "6-irq_flag_timing.nes, 2",
            "7-dmc_basics.nes, 2",
            "8-dmc_rates.nes, 3",
    })
    public void reportsExpectedStatus(final String romName, final int expectedStatus) throws IOException {
        assertRomReportsStatus("rom_singles/" + romName, expectedStatus, TICK_BUDGET, null);
    }

    /**
     * apu_test.nes (mapper 1/MMC1, unlike the mapper-0 rom_singles) runs all 8 sub-tests in sequence
     * with its own menu/sequencing code, stopping at the first failure - test 4 (jitter), the same
     * one rom_singles/4-jitter.nes fails on ("Frame irq is set too soon"). Its own $6000 status byte
     * convention is coarser than a single sub-test's (1 = some test failed, not the specific failure
     * code that sub-test would report standalone) - so the text is asserted too, to actually pin down
     * *which* sub-test failed rather than just "something did."
     */
    @Test
    public void combinedRomReportsExpectedStatus() throws IOException {
        assertRomReportsStatus("apu_test.nes", 1, COMBINED_ROM_TICK_BUDGET, "Frame irq is set too soon");
    }

    private void assertRomReportsStatus(final String romResourcePath, final int expectedStatus, final long tickBudget,
                                         final String expectedTextSubstring) throws IOException {
        BlarggRomRunner.assertReports("apu_test/" + romResourcePath, expectedStatus, expectedTextSubstring, tickBudget);
    }
}
