package com.rox;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.cpu.mos6502.assembler.Disassembler;
import com.rox.debug.cpu.CpuDebugSource;
import com.rox.debug.cpu.InstructionTrace;
import com.rox.debug.cpu.TracedInstruction;

import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/** {@link CpuDebugSource} over a live {@link NES}, reading memory through {@link DebugCapture}'s side-effect-free peek. */
final class NesCpuDebugSource implements CpuDebugSource {
    private final NES nes;
    private final NesMemoryDebugSource memory;

    NesCpuDebugSource(final NES nes){
        this.nes = nes;
        this.memory = new NesMemoryDebugSource(nes);
    }

    @Override
    public MOS6502Snapshot state(){
        return nes.cpu().liveState();
    }

    /**
     * Starts at the current instruction - the trace's newest entry, not the raw PC: read live, the CPU is
     * usually part-way through an instruction, with the PC already pointing into its operand bytes
     * (e.g. at the {@code $5B} of {@code JMP $E45B}), which would disassemble as nonsense.
     */
    @Override
    public List<DisassembledInstruction> upcomingInstructions(final int count){
        final int[] trace = nes.instructionTrace().recent();
        final int current = trace.length > 0 ? trace[trace.length - 1] : nes.cpu().programCounter();
        return Disassembler.disassembleForward(peek(), current, count);
    }

    /** Decoded from the bytes recorded as each instruction ran, not today's memory - see {@link InstructionTrace}. */
    @Override
    public List<TracedInstruction> recentInstructions(){
        final List<TracedInstruction> history = nes.instructionTrace().history();
        //the newest entry is the current instruction (about to start, or part-way through) - not history yet
        return history.isEmpty() ? history : history.subList(0, history.size() - 1);
    }

    @Override
    public Optional<MOS6502Snapshot> currentInstructionState(){
        final List<TracedInstruction> history = nes.instructionTrace().history();
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1).before());
    }

    @Override
    public double measuredHz(){
        return nes.tickRateMonitor().measuredHz();
    }

    @Override
    public long intendedHz(){
        return NES.intendedCpuHz();
    }

    @Override
    public boolean pause(){
        return nes.pause();
    }

    @Override
    public void resume(){
        nes.resume();
    }

    @Override
    public boolean isPaused(){
        return nes.isPaused();
    }

    @Override
    public void stepInstruction(){
        nes.stepInstruction();
    }

    @Override
    public void stepCycles(final int cycles){
        nes.stepCycles(cycles);
    }

    @Override
    public void stepFrame(){
        nes.stepFrame();
    }

    private IntUnaryOperator peek(){
        return memory.peek();
    }
}
