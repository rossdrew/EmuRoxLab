package com.rox.cpu.mos6502.assembler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps label names to their resolved addresses, built during assembly's first pass. */
final class SymbolTable {
    private final Map<String, Integer> addresses = new LinkedHashMap<>();

    void define(final String label, final int address, final int lineNumber) {
        if (addresses.containsKey(label)) {
            throw new AssemblyException(lineNumber, "Duplicate label: " + label);
        }
        addresses.put(label, address);
    }

    int resolve(final String label, final int lineNumber) {
        final Integer address = addresses.get(label);
        if (address == null) {
            throw new AssemblyException(lineNumber, "Undefined label: " + label);
        }
        return address;
    }

    Map<String, Integer> asMap() {
        return Map.copyOf(addresses);
    }
}
