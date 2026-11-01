package com.rox.debug.breakpoints;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Breakpoints (stop when the PC reaches an address) and watchpoints (stop after an instruction reads or writes
 * one). Mirrored addresses count as the same address: CPU RAM ({@code $0000-$1FFF}, every {@code $0800}) and the
 * PPU registers ({@code $2000-$3FFF}, every 8 bytes).
 * <ul>
 *     <li><b>Edited</b> from any thread: each edit swaps in a new immutable set, so the clock thread never sees one
 *     half-made.</li>
 *     <li><b>Checked</b> on the ticking thread only: {@link #recordAccess} as the CPU touches memory,
 *     {@link #check} after every tick. With nothing set, each costs one volatile read.</li>
 * </ul>
 */
public final class Breakpoints {
    private static final int ADDRESS_MASK = 0xFFFF;
    private static final int CPU_RAM_END = 0x1FFF;
    private static final int CPU_RAM_MIRROR_MASK = 0x07FF;
    private static final int PPU_REGISTERS_START = 0x2000;
    private static final int PPU_REGISTERS_END = 0x3FFF;
    private static final int PPU_REGISTER_MIRROR_MASK = 0x0007;

    /** Fast lookups (indexed by address) alongside the sorted views handed out. */
    private record State(SortedSet<Integer> breakpoints, SortedMap<Integer, WatchKind> watches,
                         boolean[] breakpointAt, WatchKind[] watchAt){
        static final State EMPTY = new State(new TreeSet<>(), new TreeMap<>(), null, null);

        static State of(final SortedSet<Integer> breakpoints, final SortedMap<Integer, WatchKind> watches){
            boolean[] breakpointAt = null;
            if (!breakpoints.isEmpty()){
                breakpointAt = new boolean[ADDRESS_MASK + 1];
                for (final int address : breakpoints){
                    breakpointAt[address] = true;
                }
            }
            WatchKind[] watchAt = null;
            if (!watches.isEmpty()){
                watchAt = new WatchKind[ADDRESS_MASK + 1];
                for (final Map.Entry<Integer, WatchKind> watch : watches.entrySet()){
                    watchAt[watch.getKey()] = watch.getValue();
                }
            }
            return new State(Collections.unmodifiableSortedSet(breakpoints), Collections.unmodifiableSortedMap(watches),
                    breakpointAt, watchAt);
        }
    }

    private volatile State state = State.EMPTY;
    /** Where the instruction now running started - what a watchpoint hit reports. Ticking thread only. */
    private int instructionStart;
    /** A watchpoint hit, held until its instruction finishes. Ticking thread only. */
    private BreakHit pendingWatchHit;

    /** @throws IllegalArgumentException for an address outside {@code $0000-$FFFF} */
    public synchronized void addBreakpoint(final int address){
        final SortedSet<Integer> breakpoints = new TreeSet<>(state.breakpoints());
        breakpoints.add(canonical(address));
        state = State.of(breakpoints, new TreeMap<>(state.watches()));
    }

    public synchronized void removeBreakpoint(final int address){
        final SortedSet<Integer> breakpoints = new TreeSet<>(state.breakpoints());
        breakpoints.remove(canonical(address));
        state = State.of(breakpoints, new TreeMap<>(state.watches()));
    }

    /** Adds a watchpoint, or changes the kind of the one already at {@code address}. */
    public synchronized void addWatch(final int address, final WatchKind kind){
        final SortedMap<Integer, WatchKind> watches = new TreeMap<>(state.watches());
        watches.put(canonical(address), kind);
        state = State.of(new TreeSet<>(state.breakpoints()), watches);
    }

    public synchronized void removeWatch(final int address){
        final SortedMap<Integer, WatchKind> watches = new TreeMap<>(state.watches());
        watches.remove(canonical(address));
        state = State.of(new TreeSet<>(state.breakpoints()), watches);
    }

    /** Breakpoint addresses, ascending, each by its lowest mirror. */
    public SortedSet<Integer> breakpoints(){
        return state.breakpoints();
    }

    /** Watched addresses, ascending, each by its lowest mirror. */
    public SortedMap<Integer, WatchKind> watches(){
        return state.watches();
    }

    /** The CPU has read or written {@code value} at {@code address} - ticking thread only. */
    public void recordAccess(final int address, final boolean write, final int value){
        final WatchKind[] watchAt = state.watchAt();
        if (watchAt == null || pendingWatchHit != null){
            return;
        }
        final WatchKind kind = watchAt[canonical(address)];
        if (kind != null && kind.catches(write)){
            pendingWatchHit = new BreakHit(write ? BreakHit.Cause.WRITE : BreakHit.Cause.READ, canonical(address), value,
                    instructionStart);
        }
    }

    /**
     * After every tick, on the ticking thread: between instructions, a watchpoint hit by the instruction just
     * finished, or else a breakpoint on the one about to run.
     *
     * @param pc the CPU's program counter - the next instruction's address, when {@code atInstructionBoundary}
     */
    public Optional<BreakHit> check(final boolean atInstructionBoundary, final int pc){
        if (!atInstructionBoundary){
            return Optional.empty();
        }
        final State current = state;
        if (current.breakpointAt() == null && current.watchAt() == null){
            return Optional.empty();
        }
        instructionStart = pc;
        if (pendingWatchHit != null){
            final BreakHit hit = pendingWatchHit;
            pendingWatchHit = null;
            return Optional.of(hit);
        }
        if (current.breakpointAt() != null && current.breakpointAt()[pc]){
            return Optional.of(new BreakHit(BreakHit.Cause.BREAKPOINT, pc, 0, pc));
        }
        return Optional.empty();
    }

    /** {@code address} by its lowest mirror. */
    static int canonical(final int address){
        if (address < 0 || address > ADDRESS_MASK){
            throw new IllegalArgumentException(String.format("Not a CPU address: $%X", address));
        }
        if (address <= CPU_RAM_END){
            return address & CPU_RAM_MIRROR_MASK;
        }
        if (address <= PPU_REGISTERS_END){
            return PPU_REGISTERS_START | (address & PPU_REGISTER_MIRROR_MASK);
        }
        return address;
    }
}
