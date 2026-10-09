package com.rox.cpu.mos6502;

/**
 * Everything needed to put a {@link MOS6502} back exactly where it was at an instruction boundary (see
 * {@link MOS6502#snapshot()}) - plain values only, so a save format never needs the mutable, partly
 * package-private {@link MOS6502Environment} itself. {@code ir}/{@code adl}/{@code adh} are just the
 * last instruction's leftovers at a boundary, but are kept so a restore is exact rather than "close enough".
 */
public record MOS6502Snapshot(int pc, int a, int x, int y, int stackPointer, int ir, int adl, int adh,
                              boolean negative, boolean signedOverflow, boolean breakFlag, boolean decimal,
                              boolean interruptDisable, boolean zero, boolean carry,
                              boolean irqLineAsserted, boolean nmiPending) {
}
