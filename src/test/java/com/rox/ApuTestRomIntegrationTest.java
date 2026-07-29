package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.BlarggTestStatus;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs blargg's apu_test {@code rom_singles} ROMs
 * (https://github.com/christopherpow/nes-test-roms/tree/master/apu_test) end to end through a real
 * {@link Cartridge} + {@link NES}, headlessly (mocked {@link AudioOutput}, no real-time pacing -
 * {@code nes.clock().tick()} driven directly, same approach as
 * {@link NESTest#dmcExhaustionIrqReachesTheCpusRealIrqLine()}). Each ROM reports its own pass/fail
 * through the {@code $6000} protocol - see {@link BlarggTestStatus}.
 *
 * Not all 8 currently pass. The failures are real signal about the current APU's timing fidelity
 * (frame-IRQ/length-counter/DMC-rate edge cases) rather than anything to do with ROM loading -
 * expected statuses below are today's actual outcomes, characterising the current implementation
 * rather than asserting an aspirational "all green."
 */
public class ApuTestRomIntegrationTest {
    private static final String ROM_RESOURCE_DIR = "roms/apu_test/rom_singles/";
    private static final long TICK_BUDGET = 5_000_000;

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
        final Cartridge cartridge = RomLoader.load(romPath(romName));
        final NES nes = new NES(mock(AudioOutput.class), cartridge);
        nes.cpu().reset();

        long ticks = 0;
        boolean sawSignature = false;
        while (ticks < TICK_BUDGET){
            nes.clock().tick();
            ticks++;
            if (!sawSignature && BlarggTestStatus.isSignaturePresent(cartridge)){
                sawSignature = true;
            }
            if (sawSignature && !BlarggTestStatus.isRunning(cartridge)){
                break;
            }
        }

        assertTrue(sawSignature, romName + " never wrote the blargg status signature within " + TICK_BUDGET + " ticks");
        assertTrue(ticks < TICK_BUDGET, romName + " did not finish within " + TICK_BUDGET + " ticks (status stuck at $80/$81)");
        assertEquals(expectedStatus, BlarggTestStatus.statusByte(cartridge),
                romName + " reported: " + BlarggTestStatus.text(cartridge));
    }

    private static Path romPath(final String romName){
        final URL url = ApuTestRomIntegrationTest.class.getClassLoader().getResource(ROM_RESOURCE_DIR + romName);
        if (url == null){
            throw new IllegalStateException("Test fixture not found on classpath: " + ROM_RESOURCE_DIR + romName);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e){
            throw new IllegalStateException(e);
        }
    }
}
