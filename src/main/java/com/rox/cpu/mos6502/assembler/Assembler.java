package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles 6502 text source into bytes loadable into memory.
 * <p>
 * This first cut handles straight-line programs: labels may be defined, but not yet referenced as
 * operands (that requires the two-pass symbol resolution added in a later phase) - using one as an
 * operand throws {@link AssemblyException} rather than silently emitting a wrong address.
 */
public final class Assembler {

    private Assembler() {
    }

    public static AssembledProgram assemble(final String source) {
        return assemble(source, 0);
    }

    public static AssembledProgram assemble(final String source, final int startAddress) {
        final Map<String, Integer> programLabelReferences = new LinkedHashMap<>();
        final List<Integer> programAsBytes = new ArrayList<>();
        int address = startAddress;

        for (final SourceLine line : LineScanner.scan(source)) {
            if (line.isLabelled()) {
                if (programLabelReferences.containsKey(line.label())) {
                    throw new AssemblyException(line.lineNumber(), "Duplicate label: " + line.label());
                }
                programLabelReferences.put(line.label(), address);
            }

            if (!line.containsCode()) {
                continue;
            }

            final Operand operand = OperandParser.parse(line.lineNumber(), line.operandText());
            final MOS6502OpCode opcode = InstructionResolver.resolve(line.mnemonic(), operand, line.lineNumber());

            if (operand.isLabelReference()) {
                throw new AssemblyException(line.lineNumber(),
                        "Label operands are not yet supported: " + line.mnemonic() + " " + operand.label());
            }

            final AddressingMode mode = OpCodeTable.modeOf(opcode);
            programAsBytes.add(opcode.getId());
            appendOperandBytes(programAsBytes, mode, operand.value());

            address += 1 + mode.operandByteCount();
        }

        return new AssembledProgram(startAddress, programAsBytes.stream().mapToInt(Integer::intValue).toArray(), programLabelReferences);
    }

    private static void appendOperandBytes(final List<Integer> bytes, final AddressingMode mode, final int value) {
        switch (mode.operandByteCount()) {
            case 1 -> bytes.add(value & 0xFF);
            case 2 -> {
                bytes.add(value & 0xFF);
                bytes.add((value >> 8) & 0xFF);
            }
            default -> {
                // 0 operand bytes: IMPLIED / ACCUMULATOR
            }
        }
    }
}
