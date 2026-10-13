package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JumpFlashTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final JumpFlash flash = new JumpFlash(now::get);

    private static MOS6502Snapshot at(final int pc){
        return new MOS6502Snapshot(pc, 0, 0, 0, 0xFD, 0, 0, 0, false, false, false, false, false, false, false, false, false);
    }

    private static List<DisassembledInstruction> lastRan(final int address, final int length){
        return List.of(new DisassembledInstruction(address, length, "X", new int[0], "X"));
    }

    @Test
    public void aStepThatJumpedFlashesForAWhileThenStops(){
        flash.update(at(0x9000), true, List.of(), 0x9000);

        assertTrue(flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000), "JMP $8000 from $9000");
        now.addAndGet(JumpFlash.FLASH_MILLIS - 1);
        assertTrue(flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000), "still flashing");
        now.addAndGet(1);
        assertFalse(flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000), "flash over, and no new step since");
    }

    @Test
    public void aStepToTheNextInstructionDoesNotFlash(){
        flash.update(at(0x9000), true, List.of(), 0x9000);

        assertFalse(flash.update(at(0x9002), true, lastRan(0x9000, 2), 0x9002));
    }

    @Test
    public void runningNeverFlashes(){
        flash.update(at(0x9000), false, List.of(), 0x9000);

        assertFalse(flash.update(at(0x8000), false, lastRan(0x9000, 3), 0x8000));
    }

    @Test
    public void theFirstLookNeverFlashes(){
        assertFalse(flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000));
    }

    @Test
    public void aJumpBackToTheSameStateDoesNotRetriggerWithoutAStep(){
        flash.update(at(0x9000), true, List.of(), 0x9000);
        flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000);
        now.addAndGet(JumpFlash.FLASH_MILLIS);

        assertFalse(flash.update(at(0x8000), true, lastRan(0x9000, 3), 0x8000), "same state re-read - not a new step");
    }

    @Test
    public void landedByJumpIsFalseWithNoHistoryOrASequentialStepAndHandlesWrapAround(){
        assertFalse(JumpFlash.landedByJump(List.of(), 0x1234));
        assertFalse(JumpFlash.landedByJump(lastRan(0x1234, 1), 0x1235));
        assertFalse(JumpFlash.landedByJump(lastRan(0xFFFF, 1), 0x0000), "the next instruction after $FFFF is $0000");
        assertTrue(JumpFlash.landedByJump(lastRan(0x1234, 1), 0x1236));
        assertTrue(JumpFlash.landedByJump(lastRan(0x1234, 3), 0x1234), "JMP to itself");
    }
}
