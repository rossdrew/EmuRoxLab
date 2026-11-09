package com.rox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * blargg's sprite-memory (OAM) tests - see {@link BlarggRomRunner}; expectations are today's results.
 *
 * <ul>
 *     <li>oam_read (https://github.com/christopherpow/nes-test-roms/tree/master/oam_read): reading OAM back
 *     through $2004 - passes</li>
 *     <li>sprdma_and_dmc_dma (https://github.com/christopherpow/nes-test-roms/tree/master/sprdma_and_dmc_dma):
 *     sprite DMA and DMC sample DMA colliding - never finishes</li>
 * </ul>
 */
@Tag("testRom")
class SpriteDmaTestRomTest {
    private static final long TICK_BUDGET = 20_000_000;

    @Test
    void oamReadPasses() throws Exception {
        BlarggRomRunner.assertReports("oam_read/oam_read.nes", 0, "Passed", TICK_BUDGET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sprdma_and_dmc_dma.nes", "sprdma_and_dmc_dma_512.nes"})
    void spriteAndDmcDmaNeverFinishes(final String rom) throws Exception {
        BlarggRomRunner.assertStillRunningAfter("sprdma_and_dmc_dma/" + rom, TICK_BUDGET);
    }
}
