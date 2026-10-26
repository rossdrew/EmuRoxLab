package com.rox;

import com.rox.roundabout.data.MemoryRegion;

import java.util.List;

/** The NES CPU address space, split into the regions Roundabout's memory map and hex view colour. */
final class NesMemoryMap {
    private NesMemoryMap(){
    }

    public static final List<MemoryRegion> REGIONS = List.of(
            new MemoryRegion("Zero page", 0x0000, 0x00FF, 0xFF6FDC9C),
            new MemoryRegion("Stack", 0x0100, 0x01FF, 0xFFFFB454),
            new MemoryRegion("OAM buffer", 0x0200, 0x02FF, 0xFFFF7EB6),
            new MemoryRegion("RAM", 0x0300, 0x07FF, 0xFF89DDFF),
            new MemoryRegion("RAM mirrors", 0x0800, 0x1FFF, 0xFF56606F),
            new MemoryRegion("PPU registers", 0x2000, 0x3FFF, 0xFFC792EA),
            new MemoryRegion("APU / IO", 0x4000, 0x401F, 0xFF4FD6BE),
            new MemoryRegion("Expansion", 0x4020, 0x5FFF, 0xFF56606F),
            new MemoryRegion("PRG-RAM", 0x6000, 0x7FFF, 0xFFE5C07B),
            new MemoryRegion("PRG-ROM", 0x8000, 0xFFFF, 0xFF7AA2F7));
}
