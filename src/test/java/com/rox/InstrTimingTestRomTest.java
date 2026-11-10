package com.rox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * blargg's instr_timing (https://github.com/christopherpow/nes-test-roms/tree/master/instr_timing):
 * instruction and branch cycle counts. Expectations are today's results - see {@link BlarggRomRunner}.
 *
 * <ul>
 *     <li>1-instr_timing: also times unofficial opcodes; the CPU throws on the first ($04)</li>
 *     <li>2-branch_timing: passes</li>
 * </ul>
 */
@Tag("testRom")
class InstrTimingTestRomTest {
    private static final long TICK_BUDGET = 20_000_000;

    @Test
    void instrTimingStopsAtTheFirstUnofficialOpcode(){
        final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BlarggRomRunner.run("instr_timing/rom_singles/1-instr_timing.nes", TICK_BUDGET));

        assertTrue(thrown.getMessage().contains("Unknown opcode: 0x04"), thrown.getMessage());
    }

    @Test
    void branchTimingPasses() throws Exception {
        BlarggRomRunner.assertReports("instr_timing/rom_singles/2-branch_timing.nes", 0, "Passed", TICK_BUDGET);
    }
}
