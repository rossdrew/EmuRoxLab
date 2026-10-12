package com.rox;

import com.rox.debug.labels.LabelWatchTarget;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/** {@link LabelWatchTarget} over a live {@link NES}: its debugging edits, and {@link NesMemoryDebugSource}'s side-effect-free reads. */
final class NesLabelWatchTarget implements LabelWatchTarget {
    private final NES nes;
    private final NesMemoryDebugSource memory;

    NesLabelWatchTarget(final NES nes){
        this.nes = nes;
        this.memory = new NesMemoryDebugSource(nes);
    }

    @Override
    public IntUnaryOperator peek(){
        return memory.peek();
    }

    @Override
    public void poke(final int address, final int value){
        nes.pokeMemory(address, value);
    }

    @Override
    public void lock(final int address, final int value){
        nes.lockMemory(address, value);
    }

    @Override
    public void unlock(final int address){
        nes.unlockMemory(address);
    }

    @Override
    public Map<Integer, Integer> locks(){
        return nes.lockedMemory();
    }
}
