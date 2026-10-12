package com.rox.debug.labels;

import com.rox.debug.memory.MemoryEditor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelWatchModelTest {
    /** Labels held in memory; can be told to fail its next load or save. */
    private static final class FakeStore implements LabelStore {
        List<MemoryLabel> saved = new ArrayList<>();
        Exception failNextLoad;
        boolean failNextSave;
        int saves;

        @Override
        public List<MemoryLabel> load() throws IOException, LabelFileException {
            if (failNextLoad != null){
                final Exception e = failNextLoad;
                failNextLoad = null;
                if (e instanceof LabelFileException lfe){
                    throw lfe;
                }
                throw (IOException) e;
            }
            return List.copyOf(saved);
        }

        @Override
        public void save(final List<MemoryLabel> labels) throws IOException {
            if (failNextSave){
                failNextSave = false;
                throw new IOException("disk full");
            }
            saves++;
            saved = new ArrayList<>(labels);
        }
    }

    /** 64KB of plain memory; locks keyed like MemoryEditor's, by canonical address. */
    private static final class FakeTarget implements LabelWatchTarget {
        final int[] memory = new int[0x10000];
        final Map<Integer, Integer> locks = new TreeMap<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public IntUnaryOperator peek(){
            final int[] copy = memory.clone();
            return address -> copy[address];
        }

        @Override
        public void poke(final int address, final int value){
            calls.add(String.format("poke $%04X=%d", address, value));
            memory[address] = value;
        }

        @Override
        public void lock(final int address, final int value){
            calls.add(String.format("lock $%04X=%d", address, value));
            locks.put(MemoryEditor.canonical(address), value);
            memory[address] = value;
        }

        @Override
        public void unlock(final int address){
            calls.add(String.format("unlock $%04X", address));
            locks.remove(MemoryEditor.canonical(address));
        }

        @Override
        public Map<Integer, Integer> locks(){
            return new TreeMap<>(locks);
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeTarget target = new FakeTarget();

    private LabelWatchModel modelWith(final MemoryLabel... labels){
        store.saved = new ArrayList<>(List.of(labels));
        return new LabelWatchModel(store, target);
    }

    @Test
    public void loadsTheStoresLabelsOnCreation(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("rupees", 0x066D));

        assertEquals(List.of(MemoryLabel.at("rupees", 0x066D)), model.labels());
        assertTrue(model.loadProblem().isEmpty());
    }

    @Test
    public void rowsShowAddressValuesLockAndEditability(){
        target.memory[0x066D] = 187;
        target.memory[0x0670] = 1;
        target.memory[0x0671] = 2;
        final LabelWatchModel model = modelWith(MemoryLabel.at("rupees", 0x066D), MemoryLabel.spanning("keys", 0x0670, 0x0671),
                MemoryLabel.at("reset_vector_lo", 0xFFFC));
        target.locks.put(0x066D, 187);

        assertEquals(List.of(
                new LabelWatchModel.Row("rupees", "$066D", "$BB", "187", true, true),
                new LabelWatchModel.Row("keys", "$0670-$0671", "$01 $02", "1 2", false, true),
                new LabelWatchModel.Row("reset_vector_lo", "$FFFC", "$00", "0", false, false)), model.rows());
    }

    @Test
    public void aMultiByteLabelIsOnlyLockedWhenEveryByteIs(){
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("keys", 0x0670, 0x0671));
        target.locks.put(0x0670, 0);

        assertFalse(model.rows().get(0).locked());
    }

    @Test
    public void lockStateMatchesThroughRamMirrors(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("mirrored", 0x0810));
        target.locks.put(0x0010, 5); //MemoryEditor keys a lock on $0810 as $0010

        assertTrue(model.rows().get(0).locked());
    }

    @Test
    public void aLabelSpanningIntoRomIsNotEditable(){
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("straddles", 0x7FFF, 0x8000));

        assertFalse(model.rows().get(0).editable());
    }

    @Test
    public void addParsesFileSyntaxAndSaves() throws Exception {
        final LabelWatchModel model = modelWith();

        model.add("$0670-$0671 keys");

        assertEquals(List.of(MemoryLabel.spanning("keys", 0x0670, 0x0671)), model.labels());
        assertEquals(model.labels(), store.saved);
    }

    @Test
    public void addRejectsBadTextAndDuplicateNamesWithoutSaving() throws Exception {
        final LabelWatchModel model = modelWith(MemoryLabel.at("rupees", 0x066D));

        assertThrows(LabelFileException.class, () -> model.add("nonsense"));
        assertThrows(IllegalArgumentException.class, () -> model.add("$0001 rupees"));

        assertEquals(0, store.saves);
        assertEquals(1, model.labels().size());
    }

    @Test
    public void aFailedSaveUndoesTheAdd(){
        final LabelWatchModel model = modelWith();
        store.failNextSave = true;

        assertThrows(IOException.class, () -> model.add("$0001 x"));

        assertTrue(model.labels().isEmpty());
    }

    @Test
    public void renameKeepsTheAddressAndSaves() throws IOException {
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("keys", 0x0670, 0x0671));

        model.rename(0, "  key_count ");

        assertEquals(List.of(MemoryLabel.spanning("key_count", 0x0670, 0x0671)), store.saved);
    }

    @Test
    public void renameToItsOwnNameIsFineButAnotherLabelsNameIsNot() throws IOException {
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1), MemoryLabel.at("b", 2));

        model.rename(0, "a");
        assertThrows(IllegalArgumentException.class, () -> model.rename(0, "b"));
        assertThrows(IllegalArgumentException.class, () -> model.rename(0, "has space"));

        assertEquals(List.of(MemoryLabel.at("a", 1), MemoryLabel.at("b", 2)), model.labels());
    }

    @Test
    public void aFailedSaveUndoesTheRename(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1));
        store.failNextSave = true;

        assertThrows(IOException.class, () -> model.rename(0, "b"));

        assertEquals(List.of(MemoryLabel.at("a", 1)), model.labels());
    }

    @Test
    public void removeSavesAndReleasesItsLock() throws IOException {
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1), MemoryLabel.spanning("b", 2, 3));
        model.setLocked(1, true);

        model.remove(1);

        assertEquals(List.of(MemoryLabel.at("a", 1)), store.saved);
        assertTrue(target.locks.isEmpty(), "no lock left held with no row to release it");
    }

    @Test
    public void removingAnUnlockedLabelTouchesNoLocks() throws IOException {
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1));

        model.remove(0);

        assertTrue(target.calls.isEmpty());
    }

    @Test
    public void aFailedSaveUndoesTheRemoveInPlaceAndKeepsItsLock(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1), MemoryLabel.at("b", 2), MemoryLabel.at("c", 3));
        model.setLocked(1, true);
        store.failNextSave = true;

        assertThrows(IOException.class, () -> model.remove(1));

        assertEquals(List.of(MemoryLabel.at("a", 1), MemoryLabel.at("b", 2), MemoryLabel.at("c", 3)), model.labels());
        assertEquals(Map.of(2, 0), target.locks);
    }

    @Test
    public void setValuePokesEachByte(){
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("keys", 0x0670, 0x0671));

        model.setValue(0, "$01 2");

        assertEquals(List.of("poke $0670=1", "poke $0671=2"), target.calls);
    }

    @Test
    public void setValueOnALockedLabelMovesTheLock(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("rupees", 0x066D));
        model.setLocked(0, true);
        target.calls.clear();

        model.setValue(0, "255");

        assertEquals(List.of("lock $066D=255"), target.calls);
        assertEquals(Map.of(0x066D, 255), target.locks);
    }

    @Test
    public void setValueRefusesRomAndBadInput(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("rom", 0x8000), MemoryLabel.at("ram", 0x0001));

        assertThrows(IllegalArgumentException.class, () -> model.setValue(0, "1"));
        assertThrows(IllegalArgumentException.class, () -> model.setValue(1, "256"));

        assertTrue(target.calls.isEmpty());
    }

    @Test
    public void lockingHoldsEachByteAtItsCurrentValueAndUnlockingReleasesThem(){
        target.memory[0x0670] = 7;
        target.memory[0x0671] = 8;
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("keys", 0x0670, 0x0671));

        model.setLocked(0, true);
        assertEquals(Map.of(0x0670, 7, 0x0671, 8), target.locks);
        assertTrue(model.rows().get(0).locked());

        model.setLocked(0, false);
        assertTrue(target.locks.isEmpty());
    }

    @Test
    public void lockingRomIsRefused(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("rom", 0x8000));

        assertThrows(IllegalArgumentException.class, () -> model.setLocked(0, true));
        assertTrue(target.calls.isEmpty());
    }

    /** Never let the watch list overwrite a labels file the user is part-way through fixing by hand. */
    @Test
    public void anUnreadableFileMakesLabelsReadOnlyUntilASuccessfulReload() throws Exception {
        store.saved = new ArrayList<>(List.of(MemoryLabel.at("a", 1)));
        store.failNextLoad = new LabelFileException(3, "nonsense");
        final LabelWatchModel model = new LabelWatchModel(store, target);

        assertEquals("Line 3: nonsense", model.loadProblem().orElseThrow());
        assertTrue(model.labels().isEmpty());
        assertThrows(IllegalStateException.class, () -> model.add("$0002 b"));
        assertEquals(0, store.saves);

        model.reload();
        assertTrue(model.loadProblem().isEmpty());
        assertEquals(List.of(MemoryLabel.at("a", 1)), model.labels());
        model.add("$0002 b");
        assertEquals(1, store.saves);
    }

    @Test
    public void renameAndRemoveAreAlsoRefusedWhileTheFileIsUnreadable() throws Exception {
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1));
        store.failNextLoad = new IOException("permission denied");
        model.reload();

        assertEquals("permission denied", model.loadProblem().orElseThrow());
        assertThrows(IllegalStateException.class, () -> model.rename(0, "b"));
        assertThrows(IllegalStateException.class, () -> model.remove(0));
        assertEquals(0, store.saves);
    }

    @Test
    public void labelsIsACopy(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1));

        assertThrows(UnsupportedOperationException.class, () -> model.labels().clear());
    }

    @Test
    public void reloadReplacesRatherThanAppends(){
        final LabelWatchModel model = modelWith(MemoryLabel.at("a", 1));

        model.reload();
        model.reload();

        assertEquals(List.of(MemoryLabel.at("a", 1)), model.labels());
    }

    @Test
    public void setValueOnALockedMultiByteLabelMovesEveryBytesLock(){
        final LabelWatchModel model = modelWith(MemoryLabel.spanning("keys", 0x0670, 0x0671));
        model.setLocked(0, true);
        target.calls.clear();

        model.setValue(0, "3 4");

        assertEquals(List.of("lock $0670=3", "lock $0671=4"), target.calls);
    }
}
