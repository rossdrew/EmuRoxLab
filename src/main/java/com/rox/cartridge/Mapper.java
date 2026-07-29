package com.rox.cartridge;

import com.rox.mem.MemoryBus;

/**
 * A cartridge board's banking strategy: the CPU-visible {@code $6000-$FFFF} window (PRG-RAM and
 * PRG-ROM, however the board maps/switches them). One implementation per iNES mapper number -
 * {@link NromMapper} for mapper 0.
 */
public interface Mapper extends MemoryBus {
}
