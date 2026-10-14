package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which registers and flags changed at the most recent change - kept until the next one, so while paused
 * a step's changes stay visible across refreshes instead of clearing on the next identical read.
 * Names: {@code PC}, {@code A}, {@code X}, {@code Y}, {@code SP}, and flag letters {@code N V B D I Z C}.
 */
public final class RegisterChanges {
    private MOS6502Snapshot previous;
    private Set<String> lastChanged = Set.of();

    /** @return what differs from the last different state seen (nothing on the first call) */
    public Set<String> update(final MOS6502Snapshot current){
        if (previous != null){
            final Set<String> changed = diff(previous, current);
            //an untracked change (IRQ line, NMI latch) alone keeps the last highlights rather than clearing them
            if (!changed.isEmpty()){
                lastChanged = Set.copyOf(changed);
            }
        }
        previous = current;
        return lastChanged;
    }

    private static Set<String> diff(final MOS6502Snapshot before, final MOS6502Snapshot after){
        final Set<String> changed = new LinkedHashSet<>();
        addIf(changed, before.pc() != after.pc(), "PC");
        addIf(changed, before.a() != after.a(), "A");
        addIf(changed, before.x() != after.x(), "X");
        addIf(changed, before.y() != after.y(), "Y");
        addIf(changed, before.stackPointer() != after.stackPointer(), "SP");
        addIf(changed, before.negative() != after.negative(), "N");
        addIf(changed, before.signedOverflow() != after.signedOverflow(), "V");
        addIf(changed, before.breakFlag() != after.breakFlag(), "B");
        addIf(changed, before.decimal() != after.decimal(), "D");
        addIf(changed, before.interruptDisable() != after.interruptDisable(), "I");
        addIf(changed, before.zero() != after.zero(), "Z");
        addIf(changed, before.carry() != after.carry(), "C");
        return changed;
    }

    private static void addIf(final Set<String> changed, final boolean differs, final String name){
        if (differs){
            changed.add(name);
        }
    }
}
