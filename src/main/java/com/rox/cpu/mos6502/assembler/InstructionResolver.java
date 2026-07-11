package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

/**
 * Resolves a mnemonic + {@link Operand} to a concrete {@link MOS6502OpCode}, disambiguating the
 * two sentinel {@link Operand} cases ({@link Operand#isEmpty()}, {@link Operand#isLabelReference()})
 * against whichever addressing modes the mnemonic actually supports, and falling back from a zero-page
 * mode to its absolute-family equivalent when the mnemonic has no zero-page variant.
 */
final class InstructionResolver {

    private InstructionResolver() {
    }

    static MOS6502OpCode resolve(final String mnemonic, final Operand operand, final int lineNumber) {
        if (OpCodeTable.supportedModes(mnemonic).isEmpty()) {
            throw new AssemblyException(lineNumber, "Unknown mnemonic: " + mnemonic);
        }

        if (operand.isEmpty()) {
            return resolveNoOperand(mnemonic, lineNumber);
        }

        if (operand.isLabelReference()) {
            return resolveLabelReference(mnemonic, lineNumber);
        }

        return resolveDirect(mnemonic, operand.mode(), lineNumber);
    }

    private static MOS6502OpCode resolveNoOperand(final String mnemonic, final int lineNumber) {
        if (OpCodeTable.supports(mnemonic, AddressingMode.ACCUMULATOR)) {
            return OpCodeTable.resolve(mnemonic, AddressingMode.ACCUMULATOR);
        }
        if (OpCodeTable.supports(mnemonic, AddressingMode.IMPLIED)) {
            return OpCodeTable.resolve(mnemonic, AddressingMode.IMPLIED);
        }
        throw new AssemblyException(lineNumber, mnemonic + " requires an operand");
    }

    private static MOS6502OpCode resolveLabelReference(final String mnemonic, final int lineNumber) {
        if (OpCodeTable.supports(mnemonic, AddressingMode.RELATIVE)) {
            return OpCodeTable.resolve(mnemonic, AddressingMode.RELATIVE);
        }
        if (OpCodeTable.supports(mnemonic, AddressingMode.ABSOLUTE)) {
            return OpCodeTable.resolve(mnemonic, AddressingMode.ABSOLUTE);
        }
        throw new AssemblyException(lineNumber, mnemonic + " cannot take a label operand");
    }

    private static MOS6502OpCode resolveDirect(final String mnemonic, final AddressingMode mode, final int lineNumber) {
        if (OpCodeTable.supports(mnemonic, mode)) {
            return OpCodeTable.resolve(mnemonic, mode);
        }

        final AddressingMode widened = widenZeroPageToAbsolute(mode);
        if (widened != null && OpCodeTable.supports(mnemonic, widened)) {
            return OpCodeTable.resolve(mnemonic, widened);
        }

        throw new AssemblyException(lineNumber, mnemonic + " does not support " + mode
                + " addressing (supported: " + OpCodeTable.supportedModes(mnemonic) + ")");
    }

    private static AddressingMode widenZeroPageToAbsolute(final AddressingMode mode) {
        return switch (mode) {
            case ZERO_PAGE -> AddressingMode.ABSOLUTE;
            // ZERO_PAGE_X -> ABSOLUTE_X is unreachable for the current official opcode set: every
            // mnemonic with an ABSOLUTE_X form also has a ZERO_PAGE_X form, unlike ABSOLUTE_Y (below),
            // whose mnemonics never have a ZERO_PAGE_Y form. Re-enable if that ever stops holding -
            // e.g. a future opcode adds ABSOLUTE_X support without a matching ZERO_PAGE_X.
            // case ZERO_PAGE_X -> AddressingMode.ABSOLUTE_X;
            case ZERO_PAGE_Y -> AddressingMode.ABSOLUTE_Y;
            default -> null;
        };
    }
}
