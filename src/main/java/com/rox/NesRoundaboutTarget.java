package com.rox;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.debug.labels.MemoryLabel;
import com.rox.roundabout.data.AddressFormat;
import com.rox.roundabout.data.FrameTimes;
import com.rox.roundabout.data.MemoryRegion;
import com.rox.roundabout.data.MemorySource;
import com.rox.roundabout.debugger.DebugTarget;

import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_I;

/**
 * Roundabout's {@link DebugTarget} over a live {@link NES} - reads through {@link NesMemoryDebugSource} (one RAM copy
 * per pass), run control and registers through {@link NesCpuDebugSource}, and any frame-timing streams given.
 */
final class NesRoundaboutTarget implements DebugTarget, MemorySource {
    private static final int ADDRESS_SPACE = 0x10000;
    private static final int UNUSED_FLAG = 0x20;

    private final NesMemoryDebugSource memory;
    private final NesCpuDebugSource cpu;
    private final List<Label> labels;
    private final List<FrameTimes> frameTimings;
    private final List<Step> steps;

    NesRoundaboutTarget(final NES nes, final List<MemoryLabel> labels){
        this(nes, labels, List.of());
    }

    NesRoundaboutTarget(final NES nes, final List<MemoryLabel> labels, final List<FrameTimes> frameTimings){
        this.memory = new NesMemoryDebugSource(nes);
        this.cpu = new NesCpuDebugSource(nes);
        this.labels = labels.stream().map(l -> new Label(l.name(), l.start())).toList();
        this.frameTimings = List.copyOf(frameTimings);
        this.steps = List.of(new Step("Step frame", GLFW_KEY_F, cpu::stepFrame),
                new Step("Step instr", GLFW_KEY_I, cpu::stepInstruction));
    }

    @Override
    public MemorySource memory(){
        return this;
    }

    @Override
    public List<MemoryRegion> regions(){
        return NesMemoryMap.REGIONS;
    }

    /** {@code $}/{@code %} prefixes, as 6502 assemblers write them. */
    @Override
    public AddressFormat.Style numberStyle(){
        return AddressFormat.Style.MOTOROLA;
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
        final int p = status(s);
        return new CpuState(List.of(new Register("PC", s.pc(), 16), new Register("A", s.a(), 8),
                new Register("X", s.x(), 8), new Register("Y", s.y(), 8), new Register("SP", s.stackPointer(), 8),
                new Register("P", p, 8)), Flag.fromBits("NV-BDIZC", p));
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
    public List<Step> steps(){
        return steps;
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
