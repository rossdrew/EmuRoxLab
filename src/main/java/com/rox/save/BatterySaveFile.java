package com.rox.save;

/** A decoded battery-backed save file: its metadata plus the raw PRG-RAM bytes (0-255 per element, {@code $6000-$7FFF}). */
public record BatterySaveFile(SaveMetadata metadata, int[] prgRam) {
}
