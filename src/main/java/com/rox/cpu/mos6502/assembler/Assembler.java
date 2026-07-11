package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles 6502 text source into bytes loadable into memory.
 * <p>
 * Two passes: pass 1 walks the source, defines labels against their address, and resolves each
 * instruction's mnemonic + operand to a concrete opcode. This never needs a label's actual value -
 * label operands resolve to RELATIVE or ABSOLUTE purely from what the mnemonic supports, so
 * instruction length - and therefore every address - is fully known by the end of pass 1. Pass 2
 * emits bytes, resolving label references against the now-complete {@link SymbolTable}.
 */
public final class Assembler {

    private Assembler() {
    }

    public static AssembledProgram assemble(final String source) {
        return assemble(source, 0);
    }

    public static AssembledProgram assemble(final String source, final int startAddress) {
        final SymbolTable symbolTable = new SymbolTable();
        final List<PendingInstruction> pendingInstructions = new ArrayList<>();
        int address = startAddress;

        for (final SourceLine line : LineScanner.scan(source)) {
            if (line.isLabelled()) {
                symbolTable.define(line.label(), address, line.lineNumber());
            }

            if (!line.containsCode()) {
                continue;
            }

            final Operand operand = OperandParser.parse(line.lineNumber(), line.operandText());
            final MOS6502OpCode opcode = InstructionResolver.resolve(line.mnemonic(), operand, line.lineNumber());

            pendingInstructions.add(new PendingInstruction(address, opcode, operand, line.lineNumber()));

            address += 1 + OpCodeTable.modeOf(opcode).operandByteCount();
        }

        final List<Integer> programAsBytes = new ArrayList<>();
        for (final PendingInstruction instruction : pendingInstructions) {
            emit(programAsBytes, instruction, symbolTable);
        }

        return new AssembledProgram(startAddress, programAsBytes.stream().mapToInt(Integer::intValue).toArray(), symbolTable.asMap());
    }

    private static void emit(final List<Integer> programBytes, final PendingInstruction instruction, final SymbolTable symbolTable) {
        final AddressingMode mode = OpCodeTable.modeOf(instruction.opcode());
        programBytes.add(instruction.opcode().getId());

        if (instruction.operand().isLabelReference()) {
            final int target = symbolTable.resolve(instruction.operand().label(), instruction.lineNumber());

            if (mode == AddressingMode.RELATIVE) {
                appendRelativeOffset(programBytes, target, instruction.address(), instruction.lineNumber());
            } else {
                appendOperandBytes(programBytes, mode, target);
            }
            return;
        }

        appendOperandBytes(programBytes, mode, instruction.operand().value());
    }

    private static void appendRelativeOffset(final List<Integer> bytes, final int target, final int instructionAddress, final int lineNumber) {
        final int offset = target - (instructionAddress + 2);

        if (offset < -128 || offset > 127) {
            throw new AssemblyException(lineNumber, "Branch target out of range: offset " + offset);
        }

        bytes.add(offset & 0xFF);
    }

    private static void appendOperandBytes(final List<Integer> programBytes, final AddressingMode mode, final int value) {
        switch (mode.operandByteCount()) {
            case 1 -> programBytes.add(value & 0xFF);
            case 2 -> {
                programBytes.add(value & 0xFF);
                programBytes.add((value >> 8) & 0xFF);
            }
            default -> {
                // 0 operand bytes: IMPLIED / ACCUMULATOR
            }
        }
    }
}
