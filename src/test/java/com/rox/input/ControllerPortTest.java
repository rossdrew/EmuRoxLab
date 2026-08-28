package com.rox.input;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import static net.jqwik.api.Arbitraries.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ControllerPortTest {

    @Provide
    Arbitrary<Button> buttons(){
        return of(Button.values());
    }

    private static Controller onlyPressed(final Button pressed){
        return button -> button == pressed;
    }

    private static void strobeLatch(final ControllerPort port){
        port.strobe(true);
        port.strobe(false);
    }

    private static int[] drain(final ControllerPort port, final int count){
        final int[] bits = new int[count];
        for (int i = 0; i < count; i++){
            bits[i] = port.read();
        }
        return bits;
    }

    @Property
    public void eachButtonLatchesToItsOwnShiftRegisterBitPosition(@ForAll("buttons") final Button pressed){
        final ControllerPort port = new ControllerPort(onlyPressed(pressed), Controller.NONE, false, 0); //signature bit unused - Four Score disabled
        strobeLatch(port);

        final int[] bits = drain(port, 8);
        for (final Button button : Button.values()){
            assertEquals(button == pressed ? 1 : 0, bits[button.ordinal()], button + " at bit " + button.ordinal());
        }
    }

    @Test
    public void eightReadsDrainInAbSelectStartUpDownLeftRightOrder(){
        final Controller controller = button -> button == Button.A || button == Button.START;
        final ControllerPort port = new ControllerPort(controller, Controller.NONE, false, 0); //signature bit unused - Four Score disabled
        strobeLatch(port);

        assertEquals(1, port.read()); //A
        assertEquals(0, port.read()); //B
        assertEquals(0, port.read()); //Select
        assertEquals(1, port.read()); //Start
        assertEquals(0, port.read()); //Up
        assertEquals(0, port.read()); //Down
        assertEquals(0, port.read()); //Left
        assertEquals(0, port.read()); //Right
    }

    @Test
    public void readsPastTheEighthAlwaysReturnOneInStandardMode(){
        final ControllerPort port = new ControllerPort(Controller.NONE, Controller.NONE, false, 0); //signature bit unused - Four Score disabled
        strobeLatch(port);
        drain(port, 8);

        for (int i = 0; i < 40; i++){
            assertEquals(1, port.read(), "read #" + (i + 9));
        }
    }

    @Test
    public void strobeHeldHighContinuouslyReflectsLiveAStateWithoutLatching(){
        final boolean[] pressed = { false };
        final Controller controller = button -> button == Button.A && pressed[0];
        final ControllerPort port = new ControllerPort(controller, Controller.NONE, false, 0); //signature bit unused - Four Score disabled

        port.strobe(true);
        assertEquals(0, port.read());

        pressed[0] = true;
        assertEquals(1, port.read());

        pressed[0] = false;
        assertEquals(0, port.read());
    }

    @Test
    public void aButtonChangeAfterStrobeLowDoesNotPerturbTheAlreadyLatchedSnapshot(){
        final boolean[] pressed = { false };
        final Controller controller = button -> button == Button.A && pressed[0];
        final ControllerPort port = new ControllerPort(controller, Controller.NONE, false, 0); //signature bit unused - Four Score disabled

        strobeLatch(port); //latches A=0

        //changes after the latch, before the corresponding read is consumed - must not affect it
        pressed[0] = true;

        assertEquals(0, port.read());
    }

    @Test
    public void redundantStrobeHighCallsDoNotTriggerASpuriousLatch(){
        final boolean[] pressed = { false };
        final Controller controller = button -> button == Button.A && pressed[0];
        final ControllerPort port = new ControllerPort(controller, Controller.NONE, false, 0); //signature bit unused - Four Score disabled

        port.strobe(true);
        port.strobe(true); //redundant - already high, must not be treated as a falling edge

        pressed[0] = true; //still strobed high - read() must reflect this live, proving no latch happened
        assertEquals(1, port.read());
    }

    @Test
    public void reStrobingWithoutGoingHighFirstDoesNotReLatch(){
        final boolean[] pressed = { false };
        final Controller controller = button -> button == Button.A && pressed[0];
        final ControllerPort port = new ControllerPort(controller, Controller.NONE, false, 0); //signature bit unused - Four Score disabled

        strobeLatch(port); //latches A=0
        drain(port, 1); //consume the A bit

        pressed[0] = true;
        port.strobe(false); //already low - not a falling edge, must not re-latch

        assertEquals(0, port.read()); //B, unaffected either way, but proves no re-latch reset the cursor to A
    }

    /** Port 1 ($4016, controllers 1+3) signature is bit 19 - NOT the same bit port 2 uses, per nesdev.org's Four Score page. */
    @Test
    public void fourScorePort1LatchesSecondaryControllerAndItsOwnSignatureBit(){
        assertFourScoreSignature(ControllerPort.FOUR_SCORE_PORT_1_SIGNATURE_BIT, 19);
    }

    /** Port 2 ($4017, controllers 2+4) signature is bit 18 - NOT the same bit port 1 uses, per nesdev.org's Four Score page. */
    @Test
    public void fourScorePort2LatchesSecondaryControllerAndItsOwnSignatureBit(){
        assertFourScoreSignature(ControllerPort.FOUR_SCORE_PORT_2_SIGNATURE_BIT, 18);
    }

    private void assertFourScoreSignature(final int signatureBit, final int expectedSignatureBitPosition){
        final Controller primary = onlyPressed(Button.A);
        final Controller secondary = onlyPressed(Button.B);
        final ControllerPort port = new ControllerPort(primary, secondary, true, signatureBit);
        strobeLatch(port);

        final int[] expected = new int[24];
        expected[Button.A.ordinal()] = 1; //bits0-7: primary (A pressed)
        expected[8 + Button.B.ordinal()] = 1; //bits8-15: secondary (B pressed)
        expected[expectedSignatureBitPosition] = 1; //bits16-23: every bit 0 except the port's own signature bit

        final int[] actual = drain(port, expected.length);
        for (int bit = 0; bit < expected.length; bit++){
            assertEquals(expected[bit], actual[bit], "bit " + bit);
        }

        for (int i = 0; i < 10; i++){
            assertEquals(1, port.read(), "read past bit 24 (#" + i + ")");
        }
    }
}
