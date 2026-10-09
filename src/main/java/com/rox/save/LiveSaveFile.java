package com.rox.save;

/** A decoded live-snapshot save file: its metadata plus the system state it holds. */
public record LiveSaveFile(SaveMetadata metadata, SystemSnapshot snapshot) {
}
