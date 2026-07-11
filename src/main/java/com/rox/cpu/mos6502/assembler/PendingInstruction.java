package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

/** An instruction resolved in pass 1, awaiting operand byte emission in pass 2 once all labels are known. */
record PendingInstruction(int address, MOS6502OpCode opcode, Operand operand, int lineNumber) {
}
