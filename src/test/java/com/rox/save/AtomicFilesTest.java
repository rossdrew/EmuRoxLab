package com.rox.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAReadableFileAndLeavesNoTmpFileBehind() throws IOException {
        final Path target = tempDir.resolve("nested").resolve("battery.sav");
        final byte[] content = {1, 2, 3, 4};

        AtomicFiles.write(target, content);

        assertArrayEquals(content, Files.readAllBytes(target));
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".tmp")));
    }

    @Test
    void replacesAnExistingFile() throws IOException {
        final Path target = tempDir.resolve("battery.sav");
        AtomicFiles.write(target, new byte[]{1});

        AtomicFiles.write(target, new byte[]{9, 9});

        assertArrayEquals(new byte[]{9, 9}, Files.readAllBytes(target));
    }

    @Test
    void aFailedWriteLeavesThePreviousFileAsItWas() throws IOException {
        final Path target = tempDir.resolve("battery.sav");
        AtomicFiles.write(target, new byte[]{1, 2});
        Files.createDirectory(target.resolveSibling("battery.sav.tmp")); //nowhere to write the new content

        assertThrows(IOException.class, () -> AtomicFiles.write(target, new byte[]{9}));

        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(target));
    }

    @Test
    void writesAFileGivenWithoutAFolder() throws IOException {
        final Path target = Path.of("atomic-files-test-" + ProcessHandle.current().pid() + ".bin");
        try {
            AtomicFiles.write(target, new byte[]{7});
            assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
        } finally {
            Files.deleteIfExists(target);
        }
    }
}
