package com.rox.save;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Where a ROM's save files live: a directory named after the ROM (its filename without extension),
 * alongside the ROM file itself - not a hardcoded project path, since a ROM can be loaded from
 * anywhere.
 */
public final class SavePaths {
    private static final String BATTERY_SAVE_FILE_NAME = "battery.sav";
    private static final String LIVE_SAVE_FILE_NAME = "latest.sav";
    private static final String DEBUG_SNAPSHOT_PREFIX = "debug-snapshot-";
    private static final DateTimeFormatter DEBUG_SNAPSHOT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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

    /** {@code debug-snapshot-<yyyyMMdd-HHmmss>} - names both a flagged issue's folder and the {@code .sav} inside it. */
    public static String debugSnapshotName(final LocalDateTime capturedAt){
        return DEBUG_SNAPSHOT_PREFIX + DEBUG_SNAPSHOT_TIMESTAMP.format(capturedAt);
    }

    /** Where a flagged issue's files go: a {@link #debugSnapshotName} folder in the ROM's save directory. */
    public static Path debugSnapshotDirectory(final Path romPath, final LocalDateTime capturedAt){
        return saveDirectory(romPath).resolve(debugSnapshotName(capturedAt));
    }
}
