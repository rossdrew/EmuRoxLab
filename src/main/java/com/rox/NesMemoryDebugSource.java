package com.rox;

import com.rox.debug.memory.MemoryDebugSource;
import com.rox.debug.memory.MemoryEditor;

import java.util.function.IntUnaryOperator;

/** {@link MemoryDebugSource} over a live {@link NES}: {@link DebugCapture}'s side-effect-free peek, and {@link NES#pokeMemory} edits. */
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

    @Override
    public boolean isEditable(final int address){
        return MemoryEditor.isEditable(address);
    }

    @Override
    public void poke(final int address, final int value){
        nes.pokeMemory(address, value);
    }
}
