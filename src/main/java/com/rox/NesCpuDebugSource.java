package com.rox;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.cpu.mos6502.assembler.Disassembler;
import com.rox.debug.cpu.CpuDebugSource;

import java.util.ArrayList;
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

    @Override
    public List<DisassembledInstruction> upcomingInstructions(final int count){
        return Disassembler.disassembleForward(peek(), nes.cpu().programCounter(), count);
    }

    @Override
    public List<DisassembledInstruction> recentInstructions(){
        final int[] addresses = nes.instructionTrace().recent();
        int count = addresses.length;
        //the trace's newest entry is the instruction the CPU is about to start - it's the PC, not history
        if (count > 0 && addresses[count - 1] == nes.cpu().programCounter()){
            count--;
        }
        final IntUnaryOperator peek = peek();
        final List<DisassembledInstruction> instructions = new ArrayList<>(count);
        for (int i = 0; i < count; i++){
            instructions.add(Disassembler.disassembleOne(peek, addresses[i]));
        }
        return instructions;
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
