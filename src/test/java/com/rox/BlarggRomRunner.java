package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.BlarggTestStatus;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.save.SystemSnapshot;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs one of blargg's test ROMs (https://github.com/christopherpow/nes-test-roms) headlessly through a
 * real {@link Cartridge} + {@link NES} - no real-time pacing, {@code nes.clock().tick()} driven directly -
 * until it reports. Two protocols:
 * <ul>
 *     <li>{@code $6000} (newer ROMs) - see {@link BlarggTestStatus}</li>
 *     <li>on screen (older ROMs, e.g. sprite_hit_tests, vbl_nmi_timing, cpu_timing_test6) - they print
 *     "PASSED" / "FAILED #n" (tile number = ASCII) then park in {@code exit: jmp exit} with interrupts
 *     and NMI off; status is read from that text</li>
 * </ul>
 */
final class BlarggRomRunner {
    private static final String ROM_RESOURCE_DIR = "roms/";
    /** How often, in ticks, to look for an on-screen ROM's final loop - cheap enough, and a finished ROM loops forever. */
    private static final int SCREEN_CHECK_INTERVAL = 4096;
    private static final int JMP_ABSOLUTE = 0x4C;
    private static final int NMI_ENABLE_BIT = 0x80;
    private static final int NAMETABLE_TEXT_BYTES = 0x3C0;
    //vbl_nmi_timing's 1.frame_basics prints "FAILED: # 4"
    private static final Pattern FAILED_CODE = Pattern.compile("FAILED\\W*#\\s*(\\d+)");
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

    /**
     * What the ROM reported. {@code status} and {@code text} are only meaningful once {@code finished}.
     *
     * @param sawSignature it reported through either protocol - the {@code $6000} signature, or parking on screen
     */
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
            if (!sawSignature && ticks % SCREEN_CHECK_INTERVAL == 0 && parkedForGood(nes)){
                final String text = screenText(nes);
                return new Result(true, true, screenStatus(text), text);
            }
        }
        return sawSignature
                ? new Result(true, false, BlarggTestStatus.statusByte(cartridge), BlarggTestStatus.text(cartridge))
                : new Result(false, false, -1, screenText(nes));
    }

    /** In a JMP to itself with interrupts disabled and NMI off - nothing can ever get it out. */
    private static boolean parkedForGood(final NES nes){
        if (!nes.cpu().isAtInstructionBoundary()){
            return false;
        }
        final int pc = nes.cpu().programCounter();
        final Cartridge cartridge = nes.cartridge();
        if (pc < 0x8000 || cartridge.read(pc) != JMP_ABSOLUTE
                || (cartridge.read(pc + 1) | cartridge.read(pc + 2) << 8) != pc){
            return false;
        }
        final SystemSnapshot snapshot = nes.captureSnapshot();
        return snapshot.cpu().interruptDisable() && (snapshot.ppu().registers().control() & NMI_ENABLE_BIT) == 0;
    }

    /** The first nametable's tiles as ASCII, one line per row, trailing blanks dropped. */
    static String screenText(final NES nes){
        return screenText(nes.captureSnapshot().ppu().nametableRam());
    }

    static String screenText(final int[] nametables){
        final StringBuilder text = new StringBuilder();
        for (int row = 0; row < NAMETABLE_TEXT_BYTES / 32; row++){
            final StringBuilder line = new StringBuilder();
            for (int column = 0; column < 32; column++){
                final int tile = nametables[row * 32 + column];
                line.append(tile >= 0x20 && tile < 0x7F ? (char) tile : ' ');
            }
            text.append(line.toString().stripTrailing()).append('\n');
        }
        return text.toString().strip();
    }

    /** 0 for "PASSED", n for "FAILED #n" (or "FAILED: # n"), 1 for any other end - e.g. cpu_timing_test6's "FAIL OP". */
    static int screenStatus(final String text){
        if (text.contains("PASSED")){
            return 0;
        }
        final Matcher code = FAILED_CODE.matcher(text);
        return code.find() ? Integer.parseInt(code.group(1)) : 1;
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
