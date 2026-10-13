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
    private static final int BYTE = 0xFF;
    private static final int X_SHIFT = 8;
    private static final int Y_SHIFT = 16;
    private static final int SP_SHIFT = 24;
    private static final int N = 0x80;
    private static final int V = 0x40;
    private static final int B = 0x10;
    private static final int D = 0x08;
    private static final int I = 0x04;
    private static final int Z = 0x02;
    private static final int C = 0x01;
    private static final int IRQ_LINE = 0x100;
    private static final int NMI_PENDING = 0x200;

    /** A, X, Y and SP in one int - for recording every instruction without allocating. */
    public static int packRegisters(final int a, final int x, final int y, final int stackPointer){
        return (a & BYTE) | (x & BYTE) << X_SHIFT | (y & BYTE) << Y_SHIFT | (stackPointer & BYTE) << SP_SHIFT;
    }

    /** The status flags (as NV-BDIZC bits) plus the IRQ line and NMI latch, in one int. */
    public static int packFlags(final boolean negative, final boolean signedOverflow, final boolean breakFlag,
                                final boolean decimal, final boolean interruptDisable, final boolean zero,
                                final boolean carry, final boolean irqLineAsserted, final boolean nmiPending){
        return (negative ? N : 0) | (signedOverflow ? V : 0) | (breakFlag ? B : 0) | (decimal ? D : 0)
                | (interruptDisable ? I : 0) | (zero ? Z : 0) | (carry ? C : 0)
                | (irqLineAsserted ? IRQ_LINE : 0) | (nmiPending ? NMI_PENDING : 0);
    }

    /** Rebuilds a snapshot from {@link #packRegisters}/{@link #packFlags} - with no {@code ir}/{@code adl}/{@code adh}, which aren't recorded. */
    public static MOS6502Snapshot unpack(final int pc, final int registers, final int flags){
        return new MOS6502Snapshot(pc, registers & BYTE, registers >>> X_SHIFT & BYTE, registers >>> Y_SHIFT & BYTE,
                registers >>> SP_SHIFT & BYTE, 0, 0, 0,
                (flags & N) != 0, (flags & V) != 0, (flags & B) != 0, (flags & D) != 0, (flags & I) != 0,
                (flags & Z) != 0, (flags & C) != 0, (flags & IRQ_LINE) != 0, (flags & NMI_PENDING) != 0);
    }
}
