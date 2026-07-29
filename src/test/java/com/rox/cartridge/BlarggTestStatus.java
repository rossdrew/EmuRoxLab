package com.rox.cartridge;

import com.rox.mem.MemoryBus;

/**
 * Reads blargg's test-ROM status protocol from a {@link MemoryBus} (documented at
 * https://github.com/christopherpow/nes-test-roms): a status byte at $6000 ($80 = running,
 * $81 = needs a reset, $00-$7F = the result code once finished, 0 = passed), a signature
 * {@code $DE $B0 $61} at $6001-$6003 confirming the protocol is actually in use, and a
 * null-terminated text message at $6004+.
 */
public final class BlarggTestStatus {
    private static final int STATUS_ADDRESS = 0x6000;
    private static final int SIGNATURE_ADDRESS = 0x6001;
    private static final int TEXT_ADDRESS = 0x6004;
    private static final int[] SIGNATURE = {0xDE, 0xB0, 0x61};
    private static final int RUNNING_STATUS = 0x80;
    private static final int NEEDS_RESET_STATUS = 0x81;
    private static final int MAX_TEXT_LENGTH = 4096;

    private BlarggTestStatus(){
    }

    /** Whether the $6001-$6003 signature is present - confirms $6000+ is actually this protocol. */
    public static boolean isSignaturePresent(final MemoryBus bus){
        for (int i = 0; i < SIGNATURE.length; i++){
            if (bus.read(SIGNATURE_ADDRESS + i) != SIGNATURE[i]){
                return false;
            }
        }
        return true;
    }

    public static int statusByte(final MemoryBus bus){
        return bus.read(STATUS_ADDRESS);
    }

    /** True while the test is still running or waiting for a reset - false once it has a final result. */
    public static boolean isRunning(final MemoryBus bus){
        final int status = statusByte(bus);
        return status == RUNNING_STATUS || status == NEEDS_RESET_STATUS;
    }

    /** True when the ROM is asking the harness to press reset (delayed by &gt;=100ms) before it can continue. */
    public static boolean needsReset(final MemoryBus bus){
        return statusByte(bus) == NEEDS_RESET_STATUS;
    }

    /** The null-terminated text message at $6004+. */
    public static String text(final MemoryBus bus){
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < MAX_TEXT_LENGTH; i++){
            final int value = bus.read(TEXT_ADDRESS + i);
            if (value == 0){
                break;
            }
            text.append((char) value);
        }
        return text.toString();
    }
}
