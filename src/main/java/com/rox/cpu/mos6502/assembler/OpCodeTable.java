package com.rox.cpu.mos6502.assembler;

import com.rox.cpu.mos6502.MOS6502OpCode;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A mnemonic + {@link AddressingMode} -&gt; {@link MOS6502OpCode} lookup, built once from
 * {@link MOS6502OpCode#values()} by splitting each constant's name on its first {@code _}
 * (e.g. {@code LDA_ABS_X} -&gt; mnemonic {@code LDA}, suffix {@code ABS_X}) and mapping the
 * suffix to an {@link AddressingMode}.
 */
final class OpCodeTable {

    private static final Map<String, AddressingMode> MODE_BY_SUFFIX = Map.ofEntries(
            Map.entry("IMP", AddressingMode.IMPLIED),
            Map.entry("A", AddressingMode.ACCUMULATOR),
            Map.entry("I", AddressingMode.IMMEDIATE),
            Map.entry("Z", AddressingMode.ZERO_PAGE),
            Map.entry("Z_X", AddressingMode.ZERO_PAGE_X),
            Map.entry("Z_Y", AddressingMode.ZERO_PAGE_Y),
            Map.entry("ABS", AddressingMode.ABSOLUTE),
            Map.entry("ABS_X", AddressingMode.ABSOLUTE_X),
            Map.entry("ABS_Y", AddressingMode.ABSOLUTE_Y),
            Map.entry("IND", AddressingMode.INDIRECT),
            Map.entry("IND_X", AddressingMode.INDIRECT_X),
            Map.entry("IND_Y", AddressingMode.INDIRECT_Y),
            Map.entry("REL", AddressingMode.RELATIVE)
    );

    private static final Map<String, Map<AddressingMode, MOS6502OpCode>> BY_MNEMONIC_AND_MODE = buildTable();
    private static final Map<MOS6502OpCode, AddressingMode> MODE_BY_OPCODE = buildReverseTable();

    private OpCodeTable() {
    }

    private static Map<String, Map<AddressingMode, MOS6502OpCode>> buildTable() {
        final Map<String, Map<AddressingMode, MOS6502OpCode>> table = new HashMap<>();

        for (final MOS6502OpCode opcode : MOS6502OpCode.values()) {
            final String[] parts = opcode.name().split("_", 2);
            final AddressingMode mode = MODE_BY_SUFFIX.get(parts[1]);

            table.computeIfAbsent(parts[0], mnemonic -> new EnumMap<>(AddressingMode.class)).put(mode, opcode);
        }

        return table;
    }

    private static Map<MOS6502OpCode, AddressingMode> buildReverseTable() {
        final Map<MOS6502OpCode, AddressingMode> table = new EnumMap<>(MOS6502OpCode.class);

        for (final MOS6502OpCode opcode : MOS6502OpCode.values()) {
            final String[] parts = opcode.name().split("_", 2);
            table.put(opcode, MODE_BY_SUFFIX.get(parts[1]));
        }

        return table;
    }

    static boolean supports(final String mnemonic, final AddressingMode mode) {
        final Map<AddressingMode, MOS6502OpCode> modes = BY_MNEMONIC_AND_MODE.get(mnemonic);
        return modes != null && modes.containsKey(mode);
    }

    /** @throws IllegalStateException if the mnemonic/mode pair isn't supported - callers must check {@link #supports} first */
    static MOS6502OpCode resolve(final String mnemonic, final AddressingMode mode) {
        final Map<AddressingMode, MOS6502OpCode> modes = BY_MNEMONIC_AND_MODE.get(mnemonic);
        if (modes == null || !modes.containsKey(mode)) {
            throw new IllegalStateException(mnemonic + " does not support " + mode + " addressing");
        }
        return modes.get(mode);
    }

    /** @return the addressing modes {@code mnemonic} supports, or an empty set if it's not a known mnemonic */
    static Set<AddressingMode> supportedModes(final String mnemonic) {
        final Map<AddressingMode, MOS6502OpCode> modes = BY_MNEMONIC_AND_MODE.get(mnemonic);
        return modes == null ? Set.of() : modes.keySet();
    }

    static AddressingMode modeOf(final MOS6502OpCode opcode) {
        return MODE_BY_OPCODE.get(opcode);
    }
}
