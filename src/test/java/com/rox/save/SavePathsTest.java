package com.rox.save;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SavePathsTest {

    @Test
    public void saveDirectoryIsNamedAfterTheRomWithoutItsExtension(){
        final Path romPath = Path.of("resource/rom/loz.nes");

        assertEquals(Path.of("resource/rom/loz").toAbsolutePath(), SavePaths.saveDirectory(romPath));
    }

    @Test
    public void saveDirectoryIsAlongsideTheRomNotAHardcodedProjectPath(){
        final Path romPath = Path.of("/somewhere/else/entirely/mario.nes");

        assertEquals(Path.of("/somewhere/else/entirely/mario"), SavePaths.saveDirectory(romPath));
    }

    @Test
    public void romFileNameWithNoExtensionIsUsedAsIs(){
        final Path romPath = Path.of("resource/rom/noextension");

        assertEquals(Path.of("resource/rom/noextension").toAbsolutePath(), SavePaths.saveDirectory(romPath));
    }

    @Test
    public void romFileNameThatIsOnlyADotIsUsedAsIsNotAsAnEmptyBaseName(){
        //extensionIndex == 0 (the dot is the very first character) - the whole name is kept, not
        //stripped down to an empty string, distinguishing ">0" from a ">=0" off-by-one
        final Path romPath = Path.of("resource/rom/.nes");

        assertEquals(Path.of("resource/rom/.nes").toAbsolutePath(), SavePaths.saveDirectory(romPath));
    }

    @Test
    public void batterySaveFileIsInsideTheSaveDirectory(){
        final Path romPath = Path.of("resource/rom/loz.nes");

        assertEquals(SavePaths.saveDirectory(romPath).resolve("battery.sav"), SavePaths.batterySaveFile(romPath));
    }

    @Test
    public void liveSaveFileIsInsideTheSaveDirectory(){
        final Path romPath = Path.of("resource/rom/loz.nes");

        assertEquals(SavePaths.saveDirectory(romPath).resolve("latest.sav"), SavePaths.liveSaveFile(romPath));
    }

    @Test
    public void debugSnapshotNameIsTheZeroPaddedLocalTimestamp(){
        assertEquals("debug-snapshot-20260307-090501", SavePaths.debugSnapshotName(LocalDateTime.of(2026, 3, 7, 9, 5, 1)));
    }

    @Test
    public void debugSnapshotDirectoryIsInTheRomsSaveDirectory(){
        final Path romPath = Path.of("/roms/loz.nes");

        assertEquals(Path.of("/roms/loz/debug-snapshot-20261009-143000"),
                SavePaths.debugSnapshotDirectory(romPath, LocalDateTime.of(2026, 10, 9, 14, 30, 0)));
    }
}
