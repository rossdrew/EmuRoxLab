package com.rox.save;

import java.nio.file.Path;

/**
 * Where a ROM's save files live: a directory named after the ROM (its filename without extension),
 * alongside the ROM file itself - not a hardcoded project path, since a ROM can be loaded from
 * anywhere.
 */
public final class SavePaths {
    private static final String BATTERY_SAVE_FILE_NAME = "battery.sav";
    private static final String LIVE_SAVE_FILE_NAME = "latest.sav";

    private SavePaths(){
    }

    public static Path saveDirectory(final Path romPath){
        final Path absoluteRomPath = romPath.toAbsolutePath();
        final String fileName = absoluteRomPath.getFileName().toString();
        final int extensionIndex = fileName.lastIndexOf('.');
        final String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return absoluteRomPath.getParent().resolve(baseName);
    }

    public static Path batterySaveFile(final Path romPath){
        return saveDirectory(romPath).resolve(BATTERY_SAVE_FILE_NAME);
    }

    public static Path liveSaveFile(final Path romPath){
        return saveDirectory(romPath).resolve(LIVE_SAVE_FILE_NAME);
    }
}
