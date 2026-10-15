package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.debug.labels.LabelWatchModel;
import com.rox.debug.labels.LabelStore;
import com.rox.debug.labels.MemoryLabel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class NesLabelWatchTargetTest {
    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000 (8 cycles per loop). */
    private static NES nes(){
        final Cartridge cartridge = TestRoms.nrom().at(0x9000, 0xE8, 0x86, 0x10, 0x4C, 0x00, 0x90).resetVector(0x9000).cartridge();
        final NES nes = new NES(mock(AudioOutput.class), cartridge, new ManuallyTickedClock());
        nes.cpu().reset();
        return nes;
    }

    @Test
    public void readsAndEditsTheNesMemory(){
        final NES nes = nes();
        final NesLabelWatchTarget target = new NesLabelWatchTarget(nes);

        target.poke(0x0300, 0x12);
        target.poke(0x6000, 0x34);

        assertEquals(0x12, target.peek().applyAsInt(0x0300));
        assertEquals(0x34, target.peek().applyAsInt(0x6000));
    }

    @Test
    public void locksGoThroughTheNes(){
        final NES nes = nes();
        final NesLabelWatchTarget target = new NesLabelWatchTarget(nes);

        target.lock(0x0810, 0x55);
        assertEquals(Map.of(0x0010, 0x55), target.locks());
        assertEquals(nes.lockedMemory(), target.locks());

        target.unlock(0x0010);
        assertTrue(target.locks().isEmpty());
    }

    /** End to end without Swing: a watch model over a real NES, holding a byte the program keeps writing. */
    @Test
    public void aWatchModelOverARealNesHoldsALockedLabelAgainstTheProgram(){
        final NES nes = nes();
        final List<MemoryLabel> saved = new ArrayList<>(List.of(MemoryLabel.at("counter", 0x0010)));
        final LabelStore store = new LabelStore(){
            @Override public List<MemoryLabel> load(){ return List.copyOf(saved); }
            @Override public void save(final List<MemoryLabel> labels){ }
        };
        final LabelWatchModel model = new LabelWatchModel(store, new NesLabelWatchTarget(nes));

        model.setValue(0, "$40");
        model.setLocked(0, true);
        for (int i = 0; i < 200; i++){
            nes.clock().tick(); //the program stores X to $10 every 8 cycles
        }

        assertEquals(new LabelWatchModel.Row("counter", "$0010", "$40", "64", true, true), model.rows().get(0));
    }
}
