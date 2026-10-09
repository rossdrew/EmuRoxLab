package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

import java.util.Locale;

/**
 * One-line text renderings of CPU state, shared by every CPU view - the flagged-issue {@code state.txt}
 * and the live CPU state panel - so both read the same way.
 */
public final class CpuStateText {
    private CpuStateText(){
    }

    /** {@code PC: $8123  A: $01  X: $02  Y: $03  SP: $FD} */
    public static String registers(final MOS6502Snapshot cpu){
        return String.format("PC: $%04X  A: $%02X  X: $%02X  Y: $%02X  SP: $%02X",
                cpu.pc(), cpu.a(), cpu.x(), cpu.y(), cpu.stackPointer());
    }

    /** {@code Nv-BdIzC} - the status register's {@code NV-BDIZC} bits, uppercase when set. */
    public static String flags(final MOS6502Snapshot cpu){
        return new StringBuilder()
                .append(cpu.negative() ? 'N' : 'n')
                .append(cpu.signedOverflow() ? 'V' : 'v')
                .append('-')
                .append(cpu.breakFlag() ? 'B' : 'b')
                .append(cpu.decimal() ? 'D' : 'd')
                .append(cpu.interruptDisable() ? 'I' : 'i')
                .append(cpu.zero() ? 'Z' : 'z')
                .append(cpu.carry() ? 'C' : 'c')
                .toString();
    }

    /** {@code IRQ line: clear  NMI pending: yes} */
    public static String interruptLines(final MOS6502Snapshot cpu){
        return "IRQ line: " + (cpu.irqLineAsserted() ? "asserted" : "clear") + "  NMI pending: " + (cpu.nmiPending() ? "yes" : "no");
    }

    /** {@code > $8123  JMP $9000} for the instruction at the PC, {@code   $8126  INX} otherwise. */
    public static String instruction(final DisassembledInstruction instruction, final boolean atPc){
        return (atPc ? "> " : "  ") + String.format("$%04X  %s", instruction.address(), instruction.formatted());
    }

    /**
     * {@code Clock: 1,789,773 Hz of 1,789,773 Hz (100.0%)} - below 100% while a game slows down means the
     * emulator can't keep up; near 100% means the slowdown is the game's own.
     */
    public static String clockRate(final double measuredHz, final long intendedHz){
        //Locale.ROOT: the same ',' grouping and '.' decimal point whatever the machine's locale
        return String.format(Locale.ROOT, "Clock: %,.0f Hz of %,d Hz (%.1f%%)", measuredHz, intendedHz, 100.0 * measuredHz / intendedHz);
    }

    /**
     * The whole CPU state view as text: run state and clock rate, registers, flags, interrupt lines,
     * then the recent-instruction history leading into the next {@code upcomingCount} instructions -
     * read top to bottom as "how we got here, then what's next".
     */
    public static String panel(final CpuDebugSource source, final int upcomingCount){
        final MOS6502Snapshot cpu = source.state();
        final StringBuilder out = new StringBuilder();
        out.append(source.isPaused() ? "PAUSED" : "Running").append("  ")
                .append(clockRate(source.measuredHz(), source.intendedHz())).append('\n');
        out.append(registers(cpu)).append('\n');
        out.append("Flags: ").append(flags(cpu)).append("  (NV-BDIZC)\n");
        out.append(interruptLines(cpu)).append("\n\n");
        out.append("--- Recent (oldest first) ---\n");
        for (final DisassembledInstruction instruction : source.recentInstructions()){
            out.append(instruction(instruction, false)).append('\n');
        }
        out.append("--- Next ---\n");
        boolean first = true;
        for (final DisassembledInstruction instruction : source.upcomingInstructions(upcomingCount)){
            out.append(instruction(instruction, first)).append('\n');
            first = false;
        }
        return out.toString();
    }
}
