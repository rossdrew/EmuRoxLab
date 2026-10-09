package com.rox.cartridge;

/** {@link Mmc3Mapper}'s state: R0-R7, bank select, mirroring and the whole scanline IRQ counter. */
public record Mmc3MapperSnapshot(int[] prgRam, int[] chrRam, int[] bankRegisters, int bankSelect,
                                 boolean horizontalMirroring, int irqLatch, int irqCounter,
                                 boolean irqReloadRequested, boolean irqEnabled, boolean irqPending,
                                 boolean lastChrAddressA12High) implements MapperSnapshot {
}
