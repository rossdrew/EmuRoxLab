package com.rox.mem;

/**
 * A {@link MemoryBus} that can also accept an OAM DMA transfer ({@code $4014}): 256 bytes copied
 * straight into sprite memory in one shot, rather than one byte at a time through the normal register
 * interface. Lets {@link NESMemoryBus} drive a DMA into the PPU without depending on the concrete
 * {@code PPU} class.
 */
public interface OamDmaBus extends MemoryBus {
    /** Copy 256 bytes into OAM, starting at the current OAM address, wrapping mod 256. */
    void writeOamDma(int[] pageBytes);
}
