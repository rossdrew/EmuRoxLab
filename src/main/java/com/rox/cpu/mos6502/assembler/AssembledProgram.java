package com.rox.cpu.mos6502.assembler;

import java.util.Map;

/**
 * The result of assembling 6502 source: the bytes to load into memory starting at {@code startAddress},
 * and the resolved label -&gt; address symbol table (useful for debugging/test assertions).
 */
public record AssembledProgram(int startAddress, int[] bytes, Map<String, Integer> labels) {
    public AssembledProgram {
        bytes = bytes.clone();
        labels = Map.copyOf(labels);
    }

    public int[] bytes() {
        return bytes.clone();
    }

    public int length() {
        return bytes.length;
    }

    public int endAddress() {
        return startAddress + bytes.length;
    }
}
