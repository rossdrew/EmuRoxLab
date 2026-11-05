package com.rox;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RecentRomsTest {
    /** A store that remembers what was last saved. */
    private static final class ListStore implements RecentRoms.Store {
        List<String> saved;

        ListStore(final List<String> saved){
            this.saved = saved;
        }

        @Override
        public List<String> load(){
            return saved;
        }

        @Override
        public void save(final List<String> paths){
            saved = List.copyOf(paths);
        }
    }

    private final ListStore store = new ListStore(List.of());
    private final RecentRoms recent = new RecentRoms(store);

    private static Path rom(final String path){
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Test
    void startsWithWhatWasSaved(){
        final RecentRoms loaded = new RecentRoms(new ListStore(List.of("/roms/b.nes", "/roms/a.nes")));

        assertEquals(List.of(rom("/roms/b.nes"), rom("/roms/a.nes")), loaded.list());
    }

    @Test
    void theNewestIsFirstAndSaved(){
        recent.add(Path.of("/roms/a.nes"));
        recent.add(Path.of("/roms/b.nes"));

        assertEquals(List.of(rom("/roms/b.nes"), rom("/roms/a.nes")), recent.list());
        assertEquals(List.of(rom("/roms/b.nes").toString(), rom("/roms/a.nes").toString()), store.saved);
    }

    @Test
    void reopeningARomMovesItToTheTopRatherThanListingItTwice(){
        recent.add(Path.of("/roms/a.nes"));
        recent.add(Path.of("/roms/b.nes"));

        recent.add(Path.of("/roms/../roms/a.nes"));

        assertEquals(List.of(rom("/roms/a.nes"), rom("/roms/b.nes")), recent.list());
    }

    @Test
    void aRelativePathIsListedAsAbsolute(){
        recent.add(Path.of("resource/rom/loz.nes"));

        assertEquals(List.of(Path.of("resource/rom/loz.nes").toAbsolutePath()), recent.list());
    }

    @Test
    void keepsOnlyTheNewestTen(){
        IntStream.range(0, 12).forEach(i -> recent.add(Path.of("/roms/" + i + ".nes")));

        assertEquals(RecentRoms.MAX, recent.list().size());
        assertEquals(rom("/roms/11.nes"), recent.list().get(0));
        assertEquals(rom("/roms/2.nes"), recent.list().get(RecentRoms.MAX - 1));
        assertEquals(RecentRoms.MAX, store.saved.size());
    }

    @Test
    void aSavedListIsCleanedUpOnLoad(){
        final List<String> saved = new ArrayList<>(List.of("/roms/a.nes", "/roms/./a.nes"));
        IntStream.range(0, 12).forEach(i -> saved.add("/roms/" + i + ".nes"));

        final List<Path> loaded = new RecentRoms(new ListStore(saved)).list();

        assertEquals(RecentRoms.MAX, loaded.size());
        assertEquals(List.of(rom("/roms/a.nes"), rom("/roms/0.nes")), loaded.subList(0, 2));
    }

    @Test
    void removeTakesOneOff(){
        recent.add(Path.of("/roms/a.nes"));
        recent.add(Path.of("/roms/b.nes"));

        recent.remove(Path.of("/roms/a.nes"));

        assertEquals(List.of(rom("/roms/b.nes")), recent.list());
        assertEquals(List.of(rom("/roms/b.nes").toString()), store.saved);
    }

    @Test
    void removingOneNotListedSavesNothing(){
        recent.add(Path.of("/roms/a.nes"));
        store.saved = List.of("untouched");

        recent.remove(Path.of("/roms/z.nes"));

        assertEquals(List.of("untouched"), store.saved);
    }

    @Test
    void clearEmptiesIt(){
        recent.add(Path.of("/roms/a.nes"));

        recent.clear();

        assertEquals(List.of(), recent.list());
        assertEquals(List.of(), store.saved);
    }

    @Test
    void theListCannotBeChangedFromOutside(){
        recent.add(Path.of("/roms/a.nes"));

        assertThrows(UnsupportedOperationException.class, () -> recent.list().clear());
    }

    @Test
    void aRomIsLabelledByItsFileName(){
        recent.add(Path.of("/roms/loz.nes"));
        recent.add(Path.of("/roms/mario3.nes"));

        assertEquals("loz.nes", recent.label(rom("/roms/loz.nes")));
    }

    @Test
    void romsSharingAFileNameAreLabelledWithTheirFolders(){
        recent.add(Path.of("/roms/us/zelda.nes"));
        recent.add(Path.of("/roms/eu/zelda.nes"));

        assertEquals("zelda.nes (" + rom("/roms/us") + ")", recent.label(rom("/roms/us/zelda.nes")));
        assertEquals("zelda.nes (" + rom("/roms/eu") + ")", recent.label(rom("/roms/eu/zelda.nes")));
    }

    @Test
    void preferencesKeepTheListBetweenRuns(){
        final InMemoryPreferences node = new InMemoryPreferences();
        final RecentRoms first = new RecentRoms(RecentRoms.in(node));
        first.add(Path.of("/roms/a.nes"));
        first.add(Path.of("/roms/b.nes"));

        final RecentRoms next = new RecentRoms(RecentRoms.in(node));

        assertEquals(List.of(rom("/roms/b.nes"), rom("/roms/a.nes")), next.list());
        assertTrue(node.flushes > 0, "written through");
    }

    @Test
    void preferencesForgetRomsTakenOffTheList(){
        final InMemoryPreferences node = new InMemoryPreferences();
        final RecentRoms first = new RecentRoms(RecentRoms.in(node));
        first.add(Path.of("/roms/a.nes"));
        first.add(Path.of("/roms/b.nes"));

        first.clear();

        assertEquals(List.of(), new RecentRoms(RecentRoms.in(node)).list());
    }

    @Test
    void preferencesThatCannotBeWrittenStillKeepTheListForThisRun(){
        final InMemoryPreferences node = new InMemoryPreferences();
        node.flushFailure = new BackingStoreException("read-only");
        final RecentRoms recentRoms = new RecentRoms(RecentRoms.in(node));

        recentRoms.add(Path.of("/roms/a.nes"));

        assertEquals(List.of(rom("/roms/a.nes")), recentRoms.list());
    }
}
