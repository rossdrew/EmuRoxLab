package com.rox.input;

/**
 * One physical controller port (what $4016 or $4017 reads/writes drive) - bit-accurate emulation of
 * the real shift-register/strobe protocol, including the "Four Score" adapter's extended protocol for
 * a 3rd/4th player sharing the same port.
 *
 * Strobe high ($4016 bit0 = 1): reads continuously reflect the primary controller's live A-button
 * state (no latch, no advance) - real hardware behaves the same way while held high. Strobe going low
 * (1→0) latches a snapshot of every button into a fixed 24-bit layout and resets the read cursor to
 * bit 0:
 * <ul>
 *     <li>bits 0-7: primary controller, one bit per {@link Button} (ordinal = bit position)</li>
 *     <li>bits 8-15: secondary controller if Four Score is enabled, else forced to 1</li>
 *     <li>bit 19: forced to 1 when Four Score is enabled (the signature software checks for) - 0
 *     otherwise; bits 16-18/20-23 stay 0 when Four Score is enabled, or forced to 1 otherwise</li>
 * </ul>
 * Each read after the latch returns the next bit in that layout; once past bit 23 (covering both the
 * standard "8 reads then 1 forever" case and the Four Score tail), every further read returns 1
 * without touching the latch again - matching real hardware's open-bus-style behaviour and avoiding
 * an unbounded read counter.
 */
public class ControllerPort {
    private static final int LATCHED_BIT_COUNT = 24;
    //bits 8-23 forced to 1 outside Four Score mode - a non-Four-Score pad has nothing there, and real
    //hardware's open bus reads as 1 past the 8 real buttons
    private static final int STANDARD_TAIL_MASK = 0xFFFF00;
    private static final int FOUR_SCORE_SIGNATURE_BIT = 1 << 19;
    private static final int SECONDARY_CONTROLLER_SHIFT = 8;

    private final Controller primary;
    private final Controller secondary;
    private final boolean fourScoreEnabled;

    private boolean strobeHigh;
    private int latchedBits;
    private int nextReadBit;

    public ControllerPort(final Controller primary, final Controller secondary, final boolean fourScoreEnabled){
        this.primary = primary;
        this.secondary = secondary;
        this.fourScoreEnabled = fourScoreEnabled;
    }

    /** @param high the new state of $4016 bit 0 - a 1→0 transition latches a fresh snapshot. */
    public void strobe(final boolean high){
        final boolean fallingEdge = strobeHigh && !high;
        strobeHigh = high;
        if (fallingEdge){
            latch();
        }
    }

    public int read(){
        if (strobeHigh){
            return primary.isPressed(Button.A) ? 1 : 0;
        }
        if (nextReadBit >= LATCHED_BIT_COUNT){
            return 1;
        }
        return (latchedBits >>> nextReadBit++) & 1;
    }

    private void latch(){
        int bits = buttonBits(primary);
        if (fourScoreEnabled){
            bits |= buttonBits(secondary) << SECONDARY_CONTROLLER_SHIFT;
            bits |= FOUR_SCORE_SIGNATURE_BIT;
        } else {
            bits |= STANDARD_TAIL_MASK;
        }
        latchedBits = bits;
        nextReadBit = 0;
    }

    private static int buttonBits(final Controller controller){
        int bits = 0;
        for (final Button button : Button.values()){
            if (controller.isPressed(button)){
                bits |= 1 << button.ordinal();
            }
        }
        return bits;
    }
}
