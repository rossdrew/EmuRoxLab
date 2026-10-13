package com.rox.debug.labels;

import com.rox.debug.memory.MemoryEditor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

/**
 * Everything a watch list does, minus the Swing: the ROM's labels, each one's live value and lock state
 * as display {@link Row}s, and the actions on them. Label changes are saved straight away; a save that
 * fails is undone, so the list never drifts from the file.
 *
 * <p>If the labels file couldn't be read (e.g. a line being hand-edited), labels can't be added, renamed
 * or removed until a {@link #reload()} succeeds - so saving from the watch list can never overwrite a
 * file the user is part-way through fixing.
 */
public final class LabelWatchModel {
    /** One label as displayed: {@code address} like {@code $066D} or {@code $0670-$0671}; values per {@link LabelValues}. */
    public record Row(String name, String address, String hex, String decimal, boolean locked, boolean editable) {
    }

    private final LabelStore store;
    private final LabelWatchTarget target;
    private final List<MemoryLabel> labels = new ArrayList<>();
    private String loadProblem;

    public LabelWatchModel(final LabelStore store, final LabelWatchTarget target){
        this.store = store;
        this.target = target;
        reload();
    }

    /**
     * Re-reads the labels file - e.g. after editing it by hand. If that fails, the current labels stay (so
     * their values can still be set and their locks released) but can't be changed until a reload works.
     * After a successful reload, any locked byte that a label covered before but none covers now is
     * released - otherwise it would stay held with no row left to unlock it. Locks no label ever covered
     * are left alone.
     */
    public void reload(){
        final List<MemoryLabel> loaded;
        try {
            loaded = store.load();
        } catch (IOException | LabelFileException e){
            loadProblem = e.getMessage();
            return;
        }
        final List<MemoryLabel> previous = List.copyOf(labels);
        labels.clear();
        labels.addAll(loaded);
        loadProblem = null;
        releaseLocksNoLongerCovered(previous);
    }

    private void releaseLocksNoLongerCovered(final List<MemoryLabel> previous){
        final Set<Integer> stillCovered = coveredBytes(labels);
        final Map<Integer, Integer> locks = target.locks();
        for (final int formerlyCovered : coveredBytes(previous)){
            if (locks.containsKey(formerlyCovered) && !stillCovered.contains(formerlyCovered)){
                target.unlock(formerlyCovered);
            }
        }
    }

    /** Every byte the labels cover, by {@link MemoryEditor#canonical} address - so RAM mirrors compare equal. */
    private static Set<Integer> coveredBytes(final List<MemoryLabel> labels){
        final Set<Integer> covered = new TreeSet<>();
        for (final MemoryLabel label : labels){
            for (int address = label.start(); address <= label.end(); address++){
                covered.add(MemoryEditor.canonical(address));
            }
        }
        return covered;
    }

    /** Why the labels file couldn't be read, if it couldn't - labels are read-only meanwhile. */
    public Optional<String> loadProblem(){
        return Optional.ofNullable(loadProblem);
    }

    public List<MemoryLabel> labels(){
        return List.copyOf(labels);
    }

    /** Every label's current value and lock state, from one consistent read of memory. */
    public List<Row> rows(){
        final IntUnaryOperator peek = target.peek();
        final Map<Integer, Integer> locks = target.locks();
        final List<Row> rows = new ArrayList<>(labels.size());
        for (final MemoryLabel label : labels){
            rows.add(new Row(label.name(), address(label), LabelValues.hex(label, peek), LabelValues.decimal(label, peek),
                    isLocked(label, locks), isEditable(label)));
        }
        return rows;
    }

    /**
     * Adds a label written as in the file, e.g. {@code $066D rupees}.
     *
     * @throws LabelFileException if it isn't a valid label
     * @throws IllegalArgumentException if its name is already taken
     * @throws IllegalStateException while the labels file couldn't be read
     */
    public void add(final String labelText) throws LabelFileException, IOException {
        requireLabelsEditable();
        final MemoryLabel label = LabelFile.parseLabel(labelText);
        requireUnusedName(label.name());
        labels.add(label);
        saveOrUndo(() -> labels.remove(labels.size() - 1));
    }

    /**
     * @throws IllegalArgumentException if the name is invalid or already taken by another label
     * @throws IllegalStateException while the labels file couldn't be read
     */
    public void rename(final int index, final String newName) throws IOException {
        requireLabelsEditable();
        final MemoryLabel old = labels.get(index);
        final MemoryLabel renamed = new MemoryLabel(newName.strip(), old.start(), old.length());
        if (!renamed.name().equals(old.name())){
            requireUnusedName(renamed.name());
        }
        labels.set(index, renamed);
        saveOrUndo(() -> labels.set(index, old));
    }

    /**
     * Removes a label - releasing any lock it held, so nothing stays held with no row left to unlock it.
     *
     * @throws IllegalStateException while the labels file couldn't be read
     */
    public void remove(final int index) throws IOException {
        requireLabelsEditable();
        final MemoryLabel removed = labels.remove(index);
        try {
            store.save(labels);
        } catch (IOException e){
            labels.add(index, removed);
            throw e;
        }
        if (isLocked(removed, target.locks())){
            unlockBytes(removed);
        }
    }

    /**
     * Sets a label's bytes from text per {@link LabelValues#parse} - re-locking at the new value if it's locked.
     *
     * @throws IllegalArgumentException if the text isn't one byte value per byte, or the label isn't editable
     */
    public void setValue(final int index, final String text){
        final MemoryLabel label = labels.get(index);
        requireEditable(label);
        final int[] values = LabelValues.parse(label, text);
        final boolean locked = isLocked(label, target.locks());
        for (int i = 0; i < values.length; i++){
            if (locked){
                target.lock(label.start() + i, values[i]);
            } else {
                target.poke(label.start() + i, values[i]);
            }
        }
    }

    /**
     * Locks a label's bytes at their current values, or releases them.
     *
     * @throws IllegalArgumentException if the label isn't editable
     */
    public void setLocked(final int index, final boolean locked){
        final MemoryLabel label = labels.get(index);
        requireEditable(label);
        if (locked){
            final IntUnaryOperator peek = target.peek();
            for (int address = label.start(); address <= label.end(); address++){
                target.lock(address, peek.applyAsInt(address));
            }
        } else {
            unlockBytes(label);
        }
    }

    private void unlockBytes(final MemoryLabel label){
        for (int address = label.start(); address <= label.end(); address++){
            target.unlock(address);
        }
    }

    private static String address(final MemoryLabel label){
        return label.length() == 1
                ? String.format("$%04X", label.start())
                : String.format("$%04X-$%04X", label.start(), label.end());
    }

    private static boolean isEditable(final MemoryLabel label){
        for (int address = label.start(); address <= label.end(); address++){
            if (!MemoryEditor.isEditable(address)){
                return false;
            }
        }
        return true;
    }

    private static boolean isLocked(final MemoryLabel label, final Map<Integer, Integer> locks){
        for (int address = label.start(); address <= label.end(); address++){
            if (!locks.containsKey(MemoryEditor.canonical(address))){
                return false;
            }
        }
        return true;
    }

    private void requireEditable(final MemoryLabel label){
        if (!isEditable(label)){
            throw new IllegalArgumentException("'" + label.name() + "' isn't in CPU RAM or PRG-RAM, so it can only be watched");
        }
    }

    private void requireLabelsEditable(){
        if (loadProblem != null){
            throw new IllegalStateException("The labels file couldn't be read, so labels can't be changed until it's fixed and reloaded: " + loadProblem);
        }
    }

    private void requireUnusedName(final String name){
        if (labels.stream().anyMatch(label -> label.name().equals(name))){
            throw new IllegalArgumentException("'" + name + "' is already used by another label");
        }
    }

    private void saveOrUndo(final Runnable undo) throws IOException {
        try {
            store.save(labels);
        } catch (IOException e){
            undo.run();
            throw e;
        }
    }
}
