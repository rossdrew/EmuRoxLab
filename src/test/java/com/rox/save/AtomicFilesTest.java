package com.rox.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AtomicFilesTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAReadableFileAndLeavesNoTmpFileBehind() throws IOException {
        final Path target = tempDir.resolve("nested").resolve("battery.sav");
        final byte[] content = {1, 2, 3, 4};

        AtomicFiles.write(target, content);

        assertArrayEquals(content, Files.readAllBytes(target));
        assertEquals(List.of(), tmpFilesIn(target.getParent()));
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
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView(PosixFileAttributeView.class),
                "file system without POSIX permissions");
        final Set<PosixFilePermission> writable = Files.getPosixFilePermissions(tempDir);
        Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("r-x------")); //nowhere to stage the new content
        try {
            assumeFalse(Files.isWritable(tempDir), "running as a user that can write anywhere");

            assertThrows(IOException.class, () -> AtomicFiles.write(target, new byte[]{9}));
        } finally {
            Files.setPosixFilePermissions(tempDir, writable);
        }

        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(target));
    }

    @Test
    void aFailedReplaceLeavesNoStagingFileBehind() throws IOException {
        final Path target = tempDir.resolve("battery.sav");
        Files.createDirectories(target.resolve("in-the-way")); //a folder with something in it can't be replaced

        assertThrows(IOException.class, () -> AtomicFiles.write(target, new byte[]{9}));

        assertEquals(List.of(), tmpFilesIn(tempDir));
    }

    @Test
    void eachWriteStagesInAFileOfItsOwn() throws IOException {
        final Path target = tempDir.resolve("battery.sav");

        AtomicFiles.write(target, new byte[]{1});
        AtomicFiles.write(target, new byte[]{2});

        assertArrayEquals(new byte[]{2}, Files.readAllBytes(target));
        assertEquals(List.of(), tmpFilesIn(tempDir), "each staging file moved into place, none left over");
    }

    private static List<Path> tmpFilesIn(final Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)){
            return files.filter(file -> file.getFileName().toString().endsWith(".tmp")).toList();
        }
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
