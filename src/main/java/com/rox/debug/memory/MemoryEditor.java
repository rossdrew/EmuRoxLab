package com.rox.debug.memory;

import com.rox.clock.ClockWatcher;

import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Debugging edits to memory: one-off writes, and locks that keep a byte at a value. Only CPU RAM
 * ({@code $0000-$1FFF}, mirrored down to its real 2KB) and PRG-RAM ({@code $6000-$7FFF}) can be edited -
 * anywhere else, a write would hit hardware registers or a mapper's bank switching rather than memory.
 *
 * <p>While the emulation runs, edits are {@link #queue}d and applied on the clock thread by {@link #tick()},
 * so the UI never writes into memory the emulation is using; with nothing ticking, they're
 * {@link #write}n straight away. Locks are re-applied every tick, after anything queued, so a locked byte
 * holds its value whatever the game writes. With nothing queued or locked, a tick costs one volatile read.
 */
public final class MemoryEditor implements ClockWatcher {
    /** Where an edit actually lands - CPU RAM by its real {@code $0000-$07FF} address, or PRG-RAM by CPU address. */
    @FunctionalInterface
    public interface ByteWriter {
        void write(int address, int value);
    }

    private static final int CPU_RAM_END = 0x1FFF;
    private static final int CPU_RAM_MIRROR_MASK = 0x07FF;
    private static final int PRG_RAM_START = 0x6000;
    private static final int PRG_RAM_END = 0x7FFF;
    private static final int MAX_BYTE = 0xFF;

    private record Edit(int address, int value){
    }

    private final ByteWriter ramWriter;
    private final ByteWriter prgRamWriter;
    private final Queue<Edit> queued = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Integer> locks = new ConcurrentHashMap<>();
    //set whenever there's anything to apply; tick() re-checks after clearing it, so an edit queued
    //concurrently is never left waiting
    private volatile boolean workPending;

    public MemoryEditor(final ByteWriter ramWriter, final ByteWriter prgRamWriter){
        this.ramWriter = ramWriter;
        this.prgRamWriter = prgRamWriter;
    }

    /** True for CPU RAM ({@code $0000-$1FFF}) and PRG-RAM ({@code $6000-$7FFF}). */
    public static boolean isEditable(final int address){
        return (address >= 0 && address <= CPU_RAM_END) || (address >= PRG_RAM_START && address <= PRG_RAM_END);
    }

    /** Writes now - only when nothing else is ticking (paused, or not running). */
    public void write(final int address, final int value){
        final int canonical = validate(address, value);
        apply(canonical, value);
    }

    /** Writes at the next {@link #tick()}, on the clock thread. */
    public void queue(final int address, final int value){
        queued.add(new Edit(validate(address, value), value));
        workPending = true;
    }

    /** Keeps {@code address} at {@code value} from the next tick on, until {@link #unlock}ed. Replaces any existing lock there. */
    public void lock(final int address, final int value){
        locks.put(validate(address, value), value);
        workPending = true;
    }

    public void unlock(final int address){
        locks.remove(canonical(address));
    }

    /** Current locks, address to value, in address order (RAM by its real {@code $0000-$07FF} address). */
    public Map<Integer, Integer> locks(){
        return new TreeMap<>(locks);
    }

    /** Whether the next {@link #tick()} has anything to do - false whenever nothing is queued or locked, keeping idle ticks cheap. */
    boolean hasPendingWork(){
        return workPending;
    }

    @Override
    public void tick(){
        if (!workPending){
            return;
        }
        workPending = false;
        Edit edit;
        while ((edit = queued.poll()) != null){
            apply(edit.address(), edit.value());
        }
        locks.forEach(this::apply);
        if (!locks.isEmpty() || !queued.isEmpty()){
            workPending = true;
        }
    }

    private int validate(final int address, final int value){
        if (!isEditable(address)){
            throw new IllegalArgumentException(String.format("$%04X can't be edited - only CPU RAM ($0000-$1FFF) and PRG-RAM ($6000-$7FFF)", address));
        }
        if (value < 0 || value > MAX_BYTE){
            throw new IllegalArgumentException("A byte is 0-255, not " + value);
        }
        return canonical(address);
    }

    /** RAM mirrors reduced to the real byte, so $0810 and $0010 are the same edit/lock. */
    private static int canonical(final int address){
        return address <= CPU_RAM_END ? address & CPU_RAM_MIRROR_MASK : address;
    }

    /** Takes a {@link #canonical} address: CPU RAM is already down at $0000-$07FF by now. */
    private void apply(final int address, final int value){
        if (address < PRG_RAM_START){
            ramWriter.write(address, value);
        } else {
            prgRamWriter.write(address, value);
        }
    }
}
