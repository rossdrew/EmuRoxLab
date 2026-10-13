package com.rox.debug.labels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileLabelStoreTest {
    @Test
    public void savesAndLoadsThroughTheLabelsFile(@TempDir final Path tempDir) throws IOException, LabelFileException {
        final Path file = tempDir.resolve("loz").resolve("labels.txt");
        final FileLabelStore store = new FileLabelStore(file);
        assertTrue(store.load().isEmpty(), "no file yet");

        store.save(List.of(MemoryLabel.at("rupees", 0x066D)));

        assertEquals(List.of(MemoryLabel.at("rupees", 0x066D)), store.load());
        assertEquals(List.of(MemoryLabel.at("rupees", 0x066D)), LabelFile.read(file));
    }
}
