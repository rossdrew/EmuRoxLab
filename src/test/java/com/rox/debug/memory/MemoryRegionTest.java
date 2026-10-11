package com.rox.debug.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemoryRegionTest {
    @Test
    public void rangesMatchTheBusMap(){
        assertEquals(0x0000, MemoryRegion.CPU_RAM.start());
        assertEquals(0x07FF, MemoryRegion.CPU_RAM.end());
        assertEquals(0x0000, MemoryRegion.ZERO_PAGE.start());
        assertEquals(0x00FF, MemoryRegion.ZERO_PAGE.end());
        assertEquals(0x0100, MemoryRegion.STACK.start());
        assertEquals(0x01FF, MemoryRegion.STACK.end());
        assertEquals(0x6000, MemoryRegion.PRG_RAM.start());
        assertEquals(0x7FFF, MemoryRegion.PRG_RAM.end());
        assertEquals(0x8000, MemoryRegion.PRG_ROM.start());
        assertEquals(0xFFFF, MemoryRegion.PRG_ROM.end());
    }

    /** $2000-$4017 reads have side effects - no region may include any of it. */
    @ParameterizedTest
    @EnumSource(MemoryRegion.class)
    public void noRegionTouchesTheRegisterRange(final MemoryRegion region){
        assertTrue(region.end() < 0x2000 || region.start() > 0x4017, region.name());
    }

    @Test
    public void labelsIncludeTheRange(){
        assertEquals("Stack ($0100-$01FF)", MemoryRegion.STACK.toString());
        assertEquals("PRG-ROM (as currently banked) ($8000-$FFFF)", MemoryRegion.PRG_ROM.toString());
    }
}
