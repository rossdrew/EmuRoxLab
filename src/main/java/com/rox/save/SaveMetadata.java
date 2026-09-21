package com.rox.save;

import java.time.Instant;

/**
 * A save file's header metadata. {@code createdAt} is set once, the moment a save file is first
 * created, and never changes again for that file's life. {@code accumulatedGameTimeMillis} is the
 * total play time spent reaching this save point - added to on every re-save, including across a
 * resumed session, never reset.
 */
public record SaveMetadata(SaveType type, Instant createdAt, long accumulatedGameTimeMillis) {
}
