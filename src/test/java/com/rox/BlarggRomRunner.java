package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.BlarggTestStatus;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs one of blargg's test ROMs (https://github.com/christopherpow/nes-test-roms) headlessly through a
 * real {@link Cartridge} + {@link NES} - no real-time pacing, {@code nes.clock().tick()} driven directly -
 * until it reports through the {@code $6000} protocol (see {@link BlarggTestStatus}).
 */
final class BlarggRomRunner {
    private static final String ROM_RESOURCE_DIR = "roms/";
    /** Silent and keeps nothing - a mock would record every sample, millions per emulated second. */
    private static final AudioOutput SILENT = new AudioOutput(){
        @Override
        public void start(){
        }

        @Override
        public void write(final double sample){
        }

        @Override
        public void stop(){
        }
    };

    private BlarggRomRunner(){
    }

    /** What the ROM reported. {@code status} and {@code text} are only meaningful once {@code finished}. */
    record Result(boolean sawSignature, boolean finished, int status, String text) {
    }

    /** @param romResourcePath under {@code src/test/resources/roms/} */
    static Result run(final String romResourcePath, final long tickBudget) throws IOException {
        final Cartridge cartridge = RomLoader.load(romPath(romResourcePath));
        final NES nes = new NES(SILENT, cartridge);
        nes.cpu().reset();

        boolean sawSignature = false;
        for (long ticks = 0; ticks < tickBudget; ticks++){
            nes.clock().tick();
            if (!sawSignature && BlarggTestStatus.isSignaturePresent(cartridge)){
                sawSignature = true;
            }
            if (sawSignature && BlarggTestStatus.needsReset(cartridge)){
                nes.cpu().reset();
                continue;
            }
            if (sawSignature && !BlarggTestStatus.isRunning(cartridge)){
                return new Result(true, true, BlarggTestStatus.statusByte(cartridge), BlarggTestStatus.text(cartridge));
            }
        }
        return new Result(sawSignature, false, BlarggTestStatus.statusByte(cartridge), BlarggTestStatus.text(cartridge));
    }

    /**
     * Asserts the ROM finishes within {@code tickBudget} reporting {@code expectedStatus} - and, unless
     * {@code null}, text containing {@code expectedTextSubstring}.
     */
    static void assertReports(final String romResourcePath, final int expectedStatus, final String expectedTextSubstring,
                              final long tickBudget) throws IOException {
        final Result result = run(romResourcePath, tickBudget);

        assertTrue(result.sawSignature(), romResourcePath + " never wrote the blargg status signature within " + tickBudget + " ticks");
        assertTrue(result.finished(), romResourcePath + " did not finish within " + tickBudget + " ticks (status stuck at $80/$81)");
        assertEquals(expectedStatus, result.status(), romResourcePath + " reported: " + result.text());
        if (expectedTextSubstring != null){
            assertTrue(result.text().contains(expectedTextSubstring),
                    romResourcePath + " expected text to contain \"" + expectedTextSubstring + "\" but was: " + result.text());
        }
    }

    /** Asserts the ROM starts the protocol but is still running after {@code tickBudget} - e.g. stuck waiting on something unemulated. */
    static void assertStillRunningAfter(final String romResourcePath, final long tickBudget) throws IOException {
        final Result result = run(romResourcePath, tickBudget);

        assertTrue(result.sawSignature(), romResourcePath + " never wrote the blargg status signature within " + tickBudget + " ticks");
        assertFalse(result.finished(), romResourcePath + " finished, reporting: " + result.text());
    }

    private static Path romPath(final String romResourcePath){
        final URL url = BlarggRomRunner.class.getClassLoader().getResource(ROM_RESOURCE_DIR + romResourcePath);
        if (url == null){
            throw new IllegalStateException("Test fixture not found on classpath: " + ROM_RESOURCE_DIR + romResourcePath);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e){
            throw new IllegalStateException(e);
        }
    }
}
