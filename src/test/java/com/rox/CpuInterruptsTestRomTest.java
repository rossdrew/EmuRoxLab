package com.rox;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * blargg's cpu_interrupts_v2 (https://github.com/christopherpow/nes-test-roms/tree/master/cpu_interrupts_v2):
 * when IRQ/NMI are taken relative to instructions, BRK and DMA. Expectations are today's results - see
 * {@link BlarggRomRunner}. A plain "Failed" (status 1) prints a table then a CRC of it, pinned.
 *
 * <ul>
 *     <li>1 #4: exactly one instruction after CLI should run before the IRQ is taken</li>
 *     <li>2, 3: NMI hijacking a BRK/IRQ in progress</li>
 *     <li>4: IRQ during sprite DMA</li>
 *     <li>5: branches delaying an IRQ</li>
 * </ul>
 */
@Tag("testRom")
class CpuInterruptsTestRomTest {
    private static final long TICK_BUDGET = 20_000_000;

    @ParameterizedTest(name = "{0} -> status {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "1-cli_latency.nes       | 4 | Exactly one instruction after CLI should execute before IRQ is taken",
            "2-nmi_and_brk.nes       | 1 | 24A85CA6",
            "3-nmi_and_irq.nes       | 1 | 55FEA461",
            "4-irq_and_dma.nes       | 1 | EB48410A",
            "5-branch_delays_irq.nes | 1 | C794ECB0"
    })
    void reportsTodaysResult(final String rom, final int status, final String text) throws Exception {
        BlarggRomRunner.assertReports("cpu_interrupts_v2/rom_singles/" + rom, status, text, TICK_BUDGET);
    }
}
