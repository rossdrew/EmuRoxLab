package com.rox.debug.labels;

import java.io.IOException;
import java.util.List;

/** Where a ROM's labels are kept - see {@link FileLabelStore}. */
public interface LabelStore {
    List<MemoryLabel> load() throws IOException, LabelFileException;

    void save(List<MemoryLabel> labels) throws IOException;
}
