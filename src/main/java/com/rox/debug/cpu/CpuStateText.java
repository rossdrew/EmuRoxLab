package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;

import java.util.List;
import java.util.Optional;
import java.util.Locale;

/**
 * One-line text renderings of CPU state, shared by every CPU view - the flagged-issue {@code state.txt}
 * and the live CPU state panel - so both read the same way.
 */
public final class CpuStateText {
    /** Where a line's state starts: wide enough for the longest instruction, {@code   $FFFF  ORA ($40),Y}. */
    private static final int STATE_COLUMN = 24;

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

    /**
     * {@code > $E45B  JMP $E45B    A:B0 X:0F Y:26 SP:FF  Nv-bdIzc} - the instruction, then the state it started
     * with (no PC: that's the instruction's own address), plus {@code irq}/{@code nmi} when active.
     */
    public static String instruction(final DisassembledInstruction instruction, final boolean atPc, final MOS6502Snapshot before){
        return String.format("%-" + STATE_COLUMN + "s%s", instruction(instruction, atPc), state(before));
    }

    /** {@code A:B0 X:0F Y:26 SP:FF  Nv-bdIzc}, plus {@code  irq}/{@code  nmi} when active. */
    public static String state(final MOS6502Snapshot cpu){
        return String.format("A:%02X X:%02X Y:%02X SP:%02X  %s%s%s", cpu.a(), cpu.x(), cpu.y(), cpu.stackPointer(), flags(cpu),
                cpu.irqLineAsserted() ? "  irq" : "", cpu.nmiPending() ? "  nmi" : "");
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
     * then the instructions on one time axis running bottom to top - the oldest recent instruction at
     * the bottom, the latest above it, then the current one ({@code >}) and the next
     * {@code upcomingCount - 1} above that, furthest ahead at the top.
     */
    public static String panel(final CpuDebugSource source, final int upcomingCount){
        return panel(source.state(), source, upcomingCount);
    }

    /** As {@link #panel(CpuDebugSource, int)}, for a CPU state already read - so a caller can use the same read elsewhere. */
    public static String panel(final MOS6502Snapshot cpu, final CpuDebugSource source, final int upcomingCount){
        final StringBuilder out = new StringBuilder();
        out.append(source.isPaused() ? "PAUSED" : "Running").append("  ")
                .append(clockRate(source.measuredHz(), source.intendedHz())).append('\n');
        out.append(registers(cpu)).append('\n');
        out.append("Flags: ").append(flags(cpu)).append("  (NV-BDIZC)\n");
        out.append(interruptLines(cpu)).append("\n\n");
        out.append("--- Next (furthest ahead at top) ---\n");
        final List<DisassembledInstruction> upcoming = source.upcomingInstructions(upcomingCount);
        final Optional<MOS6502Snapshot> currentState = source.currentInstructionState();
        for (int i = upcoming.size() - 1; i >= 0; i--){
            //only the current instruction has a state - the rest haven't run yet
            final String line = i == 0 && currentState.isPresent()
                    ? instruction(upcoming.get(i), true, currentState.get())
                    : instruction(upcoming.get(i), i == 0);
            out.append(line).append('\n');
        }
        out.append("--- Recent (latest at top) ---\n");
        final List<TracedInstruction> recent = source.recentInstructions();
        for (int i = recent.size() - 1; i >= 0; i--){
            out.append(instruction(recent.get(i).instruction(), false, recent.get(i).before())).append('\n');
        }
        return out.toString();
    }
}
