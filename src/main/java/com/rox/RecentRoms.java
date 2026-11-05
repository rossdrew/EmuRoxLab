package com.rox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** The ROMs opened most recently, newest first - kept between runs by a {@link Store}. */
final class RecentRoms {
    static final int MAX = 10;

    /** Where the list lives between runs. */
    interface Store {
        List<String> load();

        void save(List<String> paths);
    }

    private final Store store;
    private final List<Path> roms = new ArrayList<>();

    RecentRoms(final Store store){
        this.store = store;
        store.load().stream().map(RecentRoms::normalised).distinct().limit(MAX).forEach(roms::add);
    }

    /** Newest first. */
    List<Path> list(){
        return List.copyOf(roms);
    }

    /** Puts {@code rom} first - moving it there if it's already listed - dropping the oldest past {@link #MAX}. */
    void add(final Path rom){
        final Path path = normalised(rom.toString());
        roms.remove(path);
        roms.add(0, path);
        if (roms.size() > MAX){
            roms.remove(MAX);
        }
        save();
    }

    void remove(final Path rom){
        if (roms.remove(normalised(rom.toString()))){
            save();
        }
    }

    void clear(){
        roms.clear();
        save();
    }

    /**
     * How {@code rom} reads in a menu: its file name - with its folder too, when another listed ROM
     * shares that name.
     */
    String label(final Path rom){
        final Path name = rom.getFileName();
        final boolean nameShared = roms.stream().filter(other -> other.getFileName().equals(name)).count() > 1;
        return nameShared ? name + " (" + rom.getParent() + ")" : name.toString();
    }

    private void save(){
        store.save(roms.stream().map(Path::toString).toList());
    }

    private static Path normalised(final String path){
        return Path.of(path).toAbsolutePath().normalize();
    }

    /** A {@link Store} in the user's Java preferences, one key per ROM. */
    static Store in(final Preferences node){
        return new Store(){
            @Override
            public List<String> load(){
                final List<String> paths = new ArrayList<>();
                for (int i = 0; i < MAX; i++){
                    final String path = node.get(key(i), null);
                    if (path != null){
                        paths.add(path);
                    }
                }
                return paths;
            }

            @Override
            public void save(final List<String> paths){
                for (int i = 0; i < MAX; i++){
                    if (i < paths.size()){
                        node.put(key(i), paths.get(i));
                    } else {
                        node.remove(key(i));
                    }
                }
                try {
                    node.flush();
                } catch (BackingStoreException e){
                    System.err.println("Couldn't save the recent ROMs list: " + e.getMessage());
                }
            }
        };
    }

    private static String key(final int index){
        return "rom" + index;
    }
}
