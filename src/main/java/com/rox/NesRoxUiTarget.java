package com.rox;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.debug.labels.MemoryLabel;
import com.rox.ui.data.FrameTimes;
import com.rox.ui.data.MemoryRegion;
import com.rox.ui.data.MemorySource;
import com.rox.ui.debugger.DebugTarget;
import com.rox.ui.debugger.NesMemoryMap;

import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

/**
 * roxUI's {@link DebugTarget} over a live {@link NES} - reads through {@link NesMemoryDebugSource} (one RAM copy
 * per pass), run control and registers through {@link NesCpuDebugSource}, and any frame-timing streams given.
 */
final class NesRoxUiTarget implements DebugTarget, MemorySource {
    private static final int ADDRESS_SPACE = 0x10000;
    private static final int UNUSED_FLAG = 0x20;

    private final NesMemoryDebugSource memory;
    private final NesCpuDebugSource cpu;
    private final List<Label> labels;
    private final List<FrameTimes> frameTimings;

    NesRoxUiTarget(final NES nes, final List<MemoryLabel> labels){
        this(nes, labels, List.of());
    }

    NesRoxUiTarget(final NES nes, final List<MemoryLabel> labels, final List<FrameTimes> frameTimings){
        this.memory = new NesMemoryDebugSource(nes);
        this.cpu = new NesCpuDebugSource(nes);
        this.labels = labels.stream().map(l -> new Label(l.name(), l.start())).toList();
        this.frameTimings = List.copyOf(frameTimings);
    }

    @Override
    public MemorySource memory(){
        return this;
    }

    @Override
    public List<MemoryRegion> regions(){
        return NesMemoryMap.REGIONS;
    }

    @Override
    public List<Label> labels(){
        return labels;
    }

    @Override
    public List<FrameTimes> frameTimings(){
        return frameTimings;
    }

    @Override
    public CpuState cpu(){
        final MOS6502Snapshot s = cpu.state();
        return new CpuState(s.pc(), s.a(), s.x(), s.y(), s.stackPointer(), status(s));
    }

    /** The NV-BDIZC byte; bit 5 has no flag behind it and reads as 1, as {@code PHP} would push it. */
    static int status(final MOS6502Snapshot s){
        return (s.negative() ? 0x80 : 0) | (s.signedOverflow() ? 0x40 : 0) | UNUSED_FLAG
                | (s.breakFlag() ? 0x10 : 0) | (s.decimal() ? 0x08 : 0) | (s.interruptDisable() ? 0x04 : 0)
                | (s.zero() ? 0x02 : 0) | (s.carry() ? 0x01 : 0);
    }

    @Override
    public boolean isPaused(){
        return cpu.isPaused();
    }

    @Override
    public void pause(){
        cpu.pause();
    }

    @Override
    public void resume(){
        cpu.resume();
    }

    @Override
    public void stepFrame(){
        cpu.stepFrame();
    }

    @Override
    public void stepInstruction(){
        cpu.stepInstruction();
    }

    @Override
    public String status(){
        final double hz = cpu.measuredHz();
        return String.format(Locale.ROOT, "%,d instructions  ·  %.3f MHz (%d%%)", cpu.instructionCount(), hz / 1e6,
                Math.round(100 * hz / cpu.intendedHz()));
    }

    // ---- MemorySource ---------------------------------------------------------------------------

    @Override
    public int size(){
        return ADDRESS_SPACE;
    }

    /** One RAM copy per call - use {@link #reader()} for more than the odd byte. */
    @Override
    public int peek(final int address){
        return memory.peek().applyAsInt(address);
    }

    @Override
    public IntUnaryOperator reader(){
        return memory.peek();
    }

    @Override
    public boolean isEditable(final int address){
        return memory.isEditable(address);
    }

    @Override
    public void poke(final int address, final int value){
        memory.poke(address, value);
    }
}
