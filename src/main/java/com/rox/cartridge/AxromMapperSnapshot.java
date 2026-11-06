package com.rox.cartridge;

/** {@link AxromMapper}'s state: its CHR-RAM and the one register. {@code prgRam} is always empty - the board has none. */
public record AxromMapperSnapshot(int[] prgRam, int[] chrRam, int bankSelect) implements MapperSnapshot {
}
