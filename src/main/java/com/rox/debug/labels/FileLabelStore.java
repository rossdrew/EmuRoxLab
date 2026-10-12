package com.rox.debug.labels;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** A {@link LabelStore} backed by a {@link LabelFile} - e.g. {@code SavePaths.labelsFile(romPath)}. */
public final class FileLabelStore implements LabelStore {
    private final Path path;

    public FileLabelStore(final Path path){
        this.path = path;
    }

    @Override
    public List<MemoryLabel> load() throws IOException, LabelFileException {
        return LabelFile.read(path);
    }

    @Override
    public void save(final List<MemoryLabel> labels) throws IOException {
        LabelFile.write(path, labels);
    }
}
