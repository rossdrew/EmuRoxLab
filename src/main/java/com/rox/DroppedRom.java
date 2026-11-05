package com.rox;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Which ROM, if any, files dropped on the game window are. */
final class DroppedRom {
    private DroppedRom(){
    }

    /** The one {@code .nes} file dropped - empty for anything else, several files included. */
    static Optional<Path> of(final List<Path> dropped){
        if (dropped.size() != 1){
            return Optional.empty();
        }
        final Path file = dropped.get(0);
        return file.getFileName() != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nes")
                ? Optional.of(file)
                : Optional.empty();
    }
}
