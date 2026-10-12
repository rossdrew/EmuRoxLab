package com.rox.debug.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemoryEditorTest {
    private final List<String> writes = new ArrayList<>();
    private final MemoryEditor editor = new MemoryEditor(
            (address, value) -> writes.add(String.format("ram $%04X=%d", address, value)),
            (address, value) -> writes.add(String.format("prg $%04X=%d", address, value)));

    @ParameterizedTest
    @ValueSource(ints = {0x0000, 0x07FF, 0x0800, 0x1FFF, 0x6000, 0x7FFF})
    public void cpuRamAndPrgRamAreEditable(final int address){
        assertTrue(MemoryEditor.isEditable(address));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0x2000, 0x4017, 0x5FFF, 0x8000, 0xFFFF, 0x10000})
    public void registersRomAndUnmappedSpaceAreNot(final int address){
        assertFalse(MemoryEditor.isEditable(address));
    }

    @Test
    public void writeAppliesImmediatelyToTheRightMemory(){
        editor.write(0x0010, 1);
        editor.write(0x6000, 2);

        assertEquals(List.of("ram $0010=1", "prg $6000=2"), writes);
    }

    @Test
    public void ramMirrorsAreWrittenToTheRealByte(){
        editor.write(0x1810, 3);

        assertEquals(List.of("ram $0010=3"), writes);
    }

    @Test
    public void refusesUneditableAddressesAndNonByteValues(){
        assertThrows(IllegalArgumentException.class, () -> editor.write(0x8000, 1));
        assertThrows(IllegalArgumentException.class, () -> editor.queue(0x2002, 1));
        assertThrows(IllegalArgumentException.class, () -> editor.lock(0x4016, 1));
        assertThrows(IllegalArgumentException.class, () -> editor.write(0x0010, -1));
        assertThrows(IllegalArgumentException.class, () -> editor.write(0x0010, 256));
        editor.write(0x0010, 255); //the top of the range is fine
        assertEquals(List.of("ram $0010=255"), writes);
    }

    @Test
    public void queuedEditsWaitForTheNextTickThenApplyOnce(){
        editor.queue(0x0010, 1);
        editor.queue(0x6001, 2);
        assertTrue(writes.isEmpty(), "nothing until the clock thread ticks");

        editor.tick();
        editor.tick();

        assertEquals(List.of("ram $0010=1", "prg $6001=2"), writes);
    }

    @Test
    public void locksApplyEveryTickUntilUnlocked(){
        editor.lock(0x0810, 9); //a mirror - locks the real byte, $0010

        editor.tick();
        editor.tick();
        editor.unlock(0x0010);
        editor.tick();

        assertEquals(List.of("ram $0010=9", "ram $0010=9"), writes);
    }

    @Test
    public void aLockWinsOverAQueuedEditInTheSameTick(){
        editor.lock(0x0010, 9);
        editor.queue(0x0010, 1);

        editor.tick();

        assertEquals(List.of("ram $0010=1", "ram $0010=9"), writes, "queued first, then the lock re-applied");
    }

    @Test
    public void lockingAgainReplacesTheValue(){
        editor.lock(0x0010, 1);
        editor.lock(0x0010, 2);

        editor.tick();

        assertEquals(List.of("ram $0010=2"), writes);
    }

    @Test
    public void locksAreListedByRealAddressInOrder(){
        editor.lock(0x6000, 5);
        editor.lock(0x0810, 6);
        editor.lock(0x0001, 7);

        assertEquals(List.of(0x0001, 0x0010, 0x6000), new ArrayList<>(editor.locks().keySet()));
        assertEquals(Map.of(0x0001, 7, 0x0010, 6, 0x6000, 5), editor.locks());
    }

    @Test
    public void locksIsACopy(){
        editor.lock(0x0010, 1);
        final Map<Integer, Integer> snapshot = editor.locks();

        editor.unlock(0x0010);

        assertEquals(Map.of(0x0010, 1), snapshot);
        assertTrue(editor.locks().isEmpty());
    }

    @Test
    public void withNothingToDoATickWritesNothing(){
        editor.tick();
        editor.queue(0x0010, 1);
        editor.tick();
        editor.tick(); //the queue is empty again and nothing is locked

        assertEquals(List.of("ram $0010=1"), writes);
    }

    /** A UI thread queueing while the clock thread ticks: every edit must land, none waiting forever. */
    @Test
    public void editsQueuedFromAnotherThreadAllArrive() throws InterruptedException {
        final java.util.concurrent.atomic.AtomicInteger applied = new java.util.concurrent.atomic.AtomicInteger();
        final MemoryEditor concurrentEditor = new MemoryEditor((a, v) -> applied.incrementAndGet(), (a, v) -> { });
        final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        final Thread clock = new Thread(() -> {
            while (running.get()){
                concurrentEditor.tick();
            }
        });
        clock.start();

        for (int i = 0; i < 10_000; i++){
            concurrentEditor.queue(i & 0x7FF, i & 0xFF);
        }
        final long deadline = System.currentTimeMillis() + 5000;
        while (applied.get() < 10_000 && System.currentTimeMillis() < deadline){
            Thread.onSpinWait();
        }
        running.set(false);
        clock.join(5000);

        assertEquals(10_000, applied.get());
    }

    @Test
    public void theLastRamMirrorByteIsTheLastRealRamByte(){
        editor.write(0x1FFF, 4);

        assertEquals(List.of("ram $07FF=4"), writes);
    }

    @Test
    public void prgRamStartsExactlyAt6000(){
        editor.write(0x6000, 5);

        assertEquals(List.of("prg $6000=5"), writes);
    }

    /** Ticks run ~1.8 million times a second, so an idle editor must settle back to "nothing to do". */
    @Test
    public void pendingWorkClearsOnceQueuedEditsAreDoneAndOnlyStaysWhileLocked(){
        assertFalse(editor.hasPendingWork());

        editor.queue(0x0010, 1);
        assertTrue(editor.hasPendingWork());
        editor.tick();
        assertFalse(editor.hasPendingWork(), "queue drained, nothing locked");

        editor.lock(0x0010, 2);
        editor.tick();
        assertTrue(editor.hasPendingWork(), "a lock needs re-applying every tick");

        editor.unlock(0x0010);
        editor.tick();
        assertFalse(editor.hasPendingWork());
    }
}
