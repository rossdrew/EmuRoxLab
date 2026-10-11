package com.rox.debug.memory;

/**
 * The CPU address ranges worth browsing in a memory viewer, matching how {@code NESMemoryBus} actually
 * routes them. Deliberately no {@code $2000-$4017}: reading those registers has side effects (clearing
 * vblank, advancing {@code $2007}'s buffer, shifting controller bits), so a passive view can't show them.
 */
public enum MemoryRegion {
    CPU_RAM("CPU RAM", 0x0000, 0x07FF),
    ZERO_PAGE("Zero page", 0x0000, 0x00FF),
    STACK("Stack", 0x0100, 0x01FF),
    PRG_RAM("PRG-RAM", 0x6000, 0x7FFF),
    PRG_ROM("PRG-ROM (as currently banked)", 0x8000, 0xFFFF);

    private final String label;
    private final int start;
    private final int end;

    MemoryRegion(final String label, final int start, final int end){
        this.label = label;
        this.start = start;
        this.end = end;
    }

    public int start(){
        return start;
    }

    /** Inclusive. */
    public int end(){
        return end;
    }

    /** e.g. {@code Stack ($0100-$01FF)} - what a region picker shows. */
    @Override
    public String toString(){
        return String.format("%s ($%04X-$%04X)", label, start, end);
    }
}
