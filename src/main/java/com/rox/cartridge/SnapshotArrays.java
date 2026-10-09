package com.rox.cartridge;

/** Copy helpers shared by the mappers' {@code snapshot()}/{@code restore()} - see {@link MapperSnapshot}. */
final class SnapshotArrays {
    private static final int[] NONE = new int[0];

    private SnapshotArrays(){
    }

    /** A copy of {@code array}, or an empty array for a board that doesn't have one (e.g. CHR-RAM on a CHR-ROM board). */
    static int[] copyOf(final int[] array){
        return array == null ? NONE.clone() : array.clone();
    }

    /**
     * Copies {@code from} into {@code into} ({@code null} meaning "this board has none", matched by an
     * empty {@code from}).
     *
     * @throws IllegalArgumentException if the sizes don't match, i.e. the snapshot is from a different board
     */
    static void restore(final int[] from, final int[] into, final String what){
        final int expected = into == null ? 0 : into.length;
        if (from.length != expected){
            throw new IllegalArgumentException("Expected " + expected + " bytes of " + what + ", got " + from.length);
        }
        if (into != null){
            System.arraycopy(from, 0, into, 0, from.length);
        }
    }

    static <T extends MapperSnapshot> T expect(final MapperSnapshot snapshot, final Class<T> type){
        if (!type.isInstance(snapshot)){
            throw new IllegalArgumentException("Can't restore a " + snapshot.getClass().getSimpleName() + " into a " + type.getSimpleName() + "'s mapper");
        }
        return type.cast(snapshot);
    }
}
