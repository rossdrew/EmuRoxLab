package com.rox.debug.labels;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/** The memory a watch list reads and edits - implemented next to {@code NES}, which keeps its components package-private. */
public interface LabelWatchTarget {
    /** A side-effect-free reader over memory as of this call - see {@code MemoryDebugSource.peek()}. */
    IntUnaryOperator peek();

    /** See {@code NES.pokeMemory}. */
    void poke(int address, int value);

    /** See {@code NES.lockMemory}. */
    void lock(int address, int value);

    void unlock(int address);

    /** Locked bytes - keyed by {@code MemoryEditor.canonical} address. */
    Map<Integer, Integer> locks();
}
