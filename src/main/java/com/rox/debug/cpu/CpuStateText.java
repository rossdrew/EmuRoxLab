package com.rox.debug.cpu;

import com.rox.debug.BeamPosition;
import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import com.rox.debug.breakpoints.BreakHit;

import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-line text renderings of CPU state, shared by every CPU view - the flagged-issue {@code state.txt}
 * and the live CPU state panel - so both read the same way.
 */
public final class CpuStateText {
    /** Where a line's state starts: wide enough for the longest instruction, {@code   $FFFF  ORA ($40),Y}. */
    private static final int STATE_COLUMN = 24;
    /** Marks an instruction line with a breakpoint, in place of the space after the {@code >} column. */
    public static final char BREAKPOINT_MARK = '\u25CF';
    /** Starts the line saying what stopped the emulation - see {@link #hit}. */
    public static final String HIT_PREFIX = "Stopped by: ";
    private static final Pattern INSTRUCTION_ADDRESS = Pattern.compile("^[> ][ " + BREAKPOINT_MARK + "]\\$([0-9A-F]{4})  ");

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
     * {@code Breakpoint at $C123}, {@code Read $0042 = $05 by $C0F1} or {@code Write $0042 = $05 by $C0F1} - each
     * followed by {@code  (scanline 241, dot 6)} when the beam position is known.
     */
    public static String hit(final BreakHit hit){
        final String what = switch (hit.cause()){
            case BREAKPOINT -> String.format("Breakpoint at $%04X", hit.address());
            case READ -> String.format("Read $%04X = $%02X by $%04X", hit.address(), hit.value(), hit.pc());
            case WRITE -> String.format("Write $%04X = $%02X by $%04X", hit.address(), hit.value(), hit.pc());
        };
        return hit.beam().isKnown()
                ? what + String.format(" (scanline %d, dot %d)", hit.beam().scanline(), hit.beam().dot())
                : what;
    }

    /** {@code Scanline: 241  Dot: 6} - where the PPU is drawing, as a flagged issue's {@code state.txt} puts it. */
    public static String beam(final BeamPosition beam){
        return String.format("Scanline: %d  Dot: %d", beam.scanline(), beam.dot());
    }

    /** The address of the instruction on a {@link #panel} line, if it's an instruction line. */
    public static OptionalInt instructionAddress(final String line){
        final Matcher address = INSTRUCTION_ADDRESS.matcher(line);
        return address.find() ? OptionalInt.of(Integer.parseInt(address.group(1), 16)) : OptionalInt.empty();
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
     * {@code upcomingCount - 1} above that, furthest ahead at the top. Lines with a breakpoint carry
     * {@link #BREAKPOINT_MARK}; while something has stopped the emulation, a {@link #HIT_PREFIX} line says what.
     */
    public static String panel(final CpuDebugSource source, final int upcomingCount){
        return panel(source.state(), source, upcomingCount);
    }

    /** As {@link #panel(CpuDebugSource, int)}, for a CPU state already read - so a caller can use the same read elsewhere. */
    public static String panel(final MOS6502Snapshot cpu, final CpuDebugSource source, final int upcomingCount){
        final StringBuilder out = new StringBuilder();
        out.append(source.isPaused() ? "PAUSED" : "Running").append("  ")
                .append(clockRate(source.measuredHz(), source.intendedHz())).append('\n');
        source.lastHit().ifPresent(hit -> out.append(HIT_PREFIX).append(hit(hit)).append('\n'));
        final Set<Integer> breakpoints = source.breakpoints().breakpoints();
        out.append(registers(cpu)).append('\n');
        out.append("Flags: ").append(flags(cpu)).append("  (NV-BDIZC)\n");
        out.append(interruptLines(cpu)).append('\n');
        out.append(beam(source.beam())).append("\n\n");
        out.append("--- Next (furthest ahead at top) ---\n");
        final List<DisassembledInstruction> upcoming = source.upcomingInstructions(upcomingCount);
        final Optional<MOS6502Snapshot> currentState = source.currentInstructionState();
        for (int i = upcoming.size() - 1; i >= 0; i--){
            //only the current instruction has a state - the rest haven't run yet
            final String line = i == 0 && currentState.isPresent()
                    ? instruction(upcoming.get(i), true, currentState.get())
                    : instruction(upcoming.get(i), i == 0);
            out.append(marked(line, breakpoints.contains(upcoming.get(i).address()))).append('\n');
        }
        out.append("--- Recent (latest at top) ---\n");
        final List<TracedInstruction> recent = source.recentInstructions();
        for (int i = recent.size() - 1; i >= 0; i--){
            final DisassembledInstruction instruction = recent.get(i).instruction();
            out.append(marked(instruction(instruction, false, recent.get(i).before()), breakpoints.contains(instruction.address())))
                    .append('\n');
        }
        return out.toString();
    }

    private static String marked(final String line, final boolean breakpoint){
        return breakpoint ? line.charAt(0) + String.valueOf(BREAKPOINT_MARK) + line.substring(2) : line;
    }
}
