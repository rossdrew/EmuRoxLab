package com.rox.cartridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads a ".nes" file into a {@link Cartridge}, dispatching to a {@link Mapper} by header mapper number. */
public final class RomLoader {
    private RomLoader(){
    }

    /** Read and parse a ".nes" file from disk. */
    public static Cartridge load(final Path path) throws IOException {
        return fromBytes(Files.readAllBytes(path));
    }

    /** Parse already-in-memory iNES file bytes (e.g. a synthetic ROM built for a test/demo). */
    public static Cartridge fromBytes(final byte[] fileBytes){
        final INesRom rom = INesRom.parse(fileBytes);
        final Mapper mapper = switch (rom.mapperNumber()){
            case 0 -> new NromMapper(rom);
            default -> throw new IllegalArgumentException("Unsupported mapper: " + rom.mapperNumber());
        };
        return new Cartridge(rom, mapper);
    }
}
