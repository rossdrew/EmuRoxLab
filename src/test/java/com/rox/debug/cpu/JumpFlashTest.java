package com.rox.debug.cpu;

import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JumpFlashTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final JumpFlash flash = new JumpFlash(now::get);

    private static List<DisassembledInstruction> lastRan(final int address, final int length){
        return List.of(new DisassembledInstruction(address, length, "X", new int[0], "X"));
    }

    @Test
    public void aStepThatJumpedFlashesForAWhileThenStops(){
        flash.update(0, true, List.of(), 0x9000);

        assertTrue(flash.update(1, true, lastRan(0x9000, 3), 0x8000), "JMP $8000 from $9000");
        now.addAndGet(JumpFlash.FLASH_MILLIS - 1);
        assertTrue(flash.update(1, true, lastRan(0x9000, 3), 0x8000), "still flashing");
        now.addAndGet(1);
        assertFalse(flash.update(1, true, lastRan(0x9000, 3), 0x8000), "flash over, and no new step since");
    }

    @Test
    public void aStepToTheNextInstructionDoesNotFlash(){
        flash.update(0, true, List.of(), 0x9000);

        assertFalse(flash.update(1, true, lastRan(0x9000, 2), 0x9002));
    }

    @Test
    public void runningNeverFlashes(){
        flash.update(0, false, List.of(), 0x9000);

        assertFalse(flash.update(1, false, lastRan(0x9000, 3), 0x8000));
    }

    @Test
    public void theFirstLookNeverFlashes(){
        assertFalse(flash.update(5, true, lastRan(0x9000, 3), 0x8000));
    }

    @Test
    public void anUnchangedRefreshDoesNotRetriggerTheFlash(){
        flash.update(0, true, List.of(), 0x9000);
        flash.update(1, true, lastRan(0x9000, 3), 0x8000);
        now.addAndGet(JumpFlash.FLASH_MILLIS);

        assertFalse(flash.update(1, true, lastRan(0x9000, 3), 0x8000), "re-read with no instruction run - not a new step");
    }

    /** CodeRabbit's PR #52 finding: {@code JMP *} leaves the CPU state identical, but each step is still a jump. */
    @Test
    public void everyStepOfAJumpToItselfFlashes(){
        flash.update(0, true, List.of(), 0x8000);
        assertTrue(flash.update(1, true, lastRan(0x8000, 3), 0x8000), "first JMP $8000");
        now.addAndGet(JumpFlash.FLASH_MILLIS);

        assertTrue(flash.update(2, true, lastRan(0x8000, 3), 0x8000), "the same JMP again");
    }

    @Test
    public void landedByJumpIsFalseWithNoHistoryOrASequentialStepAndHandlesWrapAround(){
        assertFalse(JumpFlash.landedByJump(List.of(), 0x1234));
        assertFalse(JumpFlash.landedByJump(lastRan(0x1234, 1), 0x1235));
        assertFalse(JumpFlash.landedByJump(lastRan(0xFFFF, 1), 0x0000), "the next instruction after $FFFF is $0000");
        assertTrue(JumpFlash.landedByJump(lastRan(0x1234, 1), 0x1236));
        assertTrue(JumpFlash.landedByJump(lastRan(0x1234, 3), 0x1234), "JMP to itself");
    }

    /** CodeRabbit's PR #52 finding: a quick sequential step mustn't inherit the previous jump's flash. */
    @Test
    public void aSequentialStepEndsAFlashStillShowingForTheInstructionBefore(){
        flash.update(0, true, List.of(), 0x9000);
        flash.update(1, true, lastRan(0x9000, 3), 0x8000); //jumped
        now.addAndGet(100);

        assertFalse(flash.update(2, true, lastRan(0x8000, 1), 0x8001), "stepped straight on to $8001");
    }

    @Test
    public void resumingEndsTheFlash(){
        flash.update(0, true, List.of(), 0x9000);
        flash.update(1, true, lastRan(0x9000, 3), 0x8000);
        now.addAndGet(100);

        assertFalse(flash.update(1, false, List.of(), 0x8000));
    }

    @Test
    public void pausingIsNotAStep(){
        flash.update(0, false, List.of(), 0x9000); //running

        assertFalse(flash.update(1000, true, lastRan(0x9000, 3), 0x8000), "paused where a jump had just landed");
        assertTrue(flash.update(1001, true, lastRan(0x7000, 3), 0x8003), "but the next step that jumps does flash");
    }
}
