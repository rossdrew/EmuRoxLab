package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegisterChangesTest {
    private static final MOS6502Snapshot BASE = new MOS6502Snapshot(0x8000, 1, 2, 3, 0xFD, 0, 0, 0,
            false, false, false, false, false, false, false, false, false);

    private static MOS6502Snapshot with(final int pc, final int a, final int x, final int y, final int sp,
                                        final boolean... flags){ //N V B D I Z C
        return new MOS6502Snapshot(pc, a, x, y, sp, 0, 0, 0,
                flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], false, false);
    }

    private static final boolean[] NO_FLAGS = {false, false, false, false, false, false, false};

    @Test
    public void nothingHasChangedOnTheFirstLook(){
        assertTrue(new RegisterChanges().update(BASE).isEmpty());
    }

    @Test
    public void reportsEachChangedRegister(){
        final RegisterChanges changes = new RegisterChanges();
        changes.update(BASE);

        assertEquals(Set.of("PC", "A", "X", "Y", "SP"), changes.update(with(0x8001, 9, 9, 9, 0xFC, NO_FLAGS)));
    }

    @Test
    public void reportsEachChangedFlag(){
        final RegisterChanges changes = new RegisterChanges();
        changes.update(BASE);

        assertEquals(Set.of("N", "V", "B", "D", "I", "Z", "C"),
                changes.update(with(0x8000, 1, 2, 3, 0xFD, true, true, true, true, true, true, true)));
    }

    @Test
    public void onlyTheRegistersThatChanged(){
        final RegisterChanges changes = new RegisterChanges();
        changes.update(BASE);

        assertEquals(Set.of("A", "Z"), changes.update(with(0x8000, 0, 2, 3, 0xFD, false, false, false, false, false, true, false)));
    }

    /** Paused: every refresh reads the same state, and the last step's changes must stay visible. */
    @Test
    public void theLastChangesStayUntilSomethingElseChanges(){
        final RegisterChanges changes = new RegisterChanges();
        changes.update(BASE);
        final MOS6502Snapshot stepped = with(0x8001, 5, 2, 3, 0xFD, NO_FLAGS);
        changes.update(stepped);

        assertEquals(Set.of("PC", "A"), changes.update(stepped));
        assertEquals(Set.of("PC", "A"), changes.update(stepped));

        assertEquals(Set.of("PC", "X"), changes.update(with(0x8002, 5, 7, 3, 0xFD, NO_FLAGS)));
    }

    /** CodeRabbit's PR #52 finding: a change to something untracked mustn't wipe the last highlights. */
    @Test
    public void anUntrackedChangeAloneKeepsTheLastHighlights(){
        final RegisterChanges changes = new RegisterChanges();
        changes.update(BASE);
        final MOS6502Snapshot stepped = with(0x8001, 5, 2, 3, 0xFD, NO_FLAGS);
        changes.update(stepped);
        final MOS6502Snapshot nmiLatched = new MOS6502Snapshot(0x8001, 5, 2, 3, 0xFD, 0, 0, 0,
                false, false, false, false, false, false, false, true, true);

        assertEquals(Set.of("PC", "A"), changes.update(nmiLatched));
    }
}
