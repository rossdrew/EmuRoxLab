package com.rox.cartridge;

/** {@link NromMapper}'s state - just its RAM, since NROM has no registers. */
public record NromMapperSnapshot(int[] prgRam, int[] chrRam) implements MapperSnapshot {
}
