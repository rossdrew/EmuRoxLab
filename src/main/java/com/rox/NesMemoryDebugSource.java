package com.rox;

import com.rox.debug.memory.MemoryDebugSource;

import java.util.function.IntUnaryOperator;

/** {@link MemoryDebugSource} over a live {@link NES}, via {@link DebugCapture}'s side-effect-free peek. */
final class NesMemoryDebugSource implements MemoryDebugSource {
    private final NES nes;

    NesMemoryDebugSource(final NES nes){
        this.nes = nes;
    }

    /** Over a copy of RAM taken now - one copy per call, not per byte read. */
    @Override
    public IntUnaryOperator peek(){
        return DebugCapture.sideEffectFreePeek(nes.ram().snapshot(), nes.cartridge());
    }
}
