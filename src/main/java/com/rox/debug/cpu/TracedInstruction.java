package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

/**
 * An executed instruction and the processor state it started with. {@code before}'s {@code ir}/{@code adl}/{@code adh}
 * aren't recorded, so are always 0.
 */
public record TracedInstruction(DisassembledInstruction instruction, MOS6502Snapshot before) {
}
