package com.rox;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.cpu.mos6502.assembler.Disassembler;
import com.rox.debug.cpu.CpuDebugSource;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** {@link CpuDebugSource} over a live {@link NES}, reading memory through {@link DebugCapture}'s side-effect-free peek. */
final class NesCpuDebugSource implements CpuDebugSource {
    private final NES nes;

    NesCpuDebugSource(final NES nes){
        this.nes = nes;
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

    @Override
    public List<DisassembledInstruction> recentInstructions(){
        //decoded from the bytes recorded as each instruction ran, not today's memory - see InstructionTrace
        final List<DisassembledInstruction> trace = nes.instructionTrace().recentInstructions();
        //the newest entry is the current instruction (about to start, or part-way through) - not history yet
        return trace.isEmpty() ? trace : trace.subList(0, trace.size() - 1);
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

    /** Over a copy of RAM taken now - one copy per refresh, not per byte read. */
    private IntUnaryOperator peek(){
        return DebugCapture.sideEffectFreePeek(nes.ram().snapshot(), nes.cartridge());
    }
}
