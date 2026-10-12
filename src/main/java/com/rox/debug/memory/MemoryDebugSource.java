package com.rox.debug.memory;

import java.util.function.IntUnaryOperator;

/**
 * What a memory view reads - implemented next to {@code NES}, which keeps its components
 * package-private. Reads may come from a UI thread while the emulation runs, so they're best-effort
 * live values; exact while paused.
 */
public interface MemoryDebugSource {
    /**
     * A side-effect-free reader over CPU address space as of this call - take one per refresh, so a
     * whole view reads one consistent copy of RAM. {@code $2000-$4017} read as a placeholder.
     */
    IntUnaryOperator peek();
}
