package com.rox.cartridge;

/**
 * A {@link Mapper}'s complete mutable state (RAM and registers - never ROM, which a restore assumes is
 * the same cartridge), one record type per board. Arrays hold 0-255 per element; a board with CHR-ROM
 * rather than CHR-RAM records an empty {@code chrRam}.
 */
public sealed interface MapperSnapshot permits NromMapperSnapshot, Mmc1MapperSnapshot, Mmc3MapperSnapshot,
        //new boards go last: SnapshotCodec stores the index of the snapshot's type in this list
        AxromMapperSnapshot {
    int[] prgRam();

    int[] chrRam();
}
