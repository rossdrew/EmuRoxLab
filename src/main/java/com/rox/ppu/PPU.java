package com.rox.ppu;

import com.rox.clock.ClockWatcher;
import com.rox.mem.MemoryBus;

/**
 * Headless NES PPU: correct vblank/NMI timing and just enough of the {@code $2000-$2007} register
 * behaviour that real games' boot-time "wait for vblank" polling loops and VRAM/OAM upload code work
 * without crashing - no pixel rendering, no framebuffer. Registers repeat every 8 bytes through
 * {@code $2000-$3FFF}.
 *
 * Runs at 3 dots per CPU cycle (NTSC), 341 dots/scanline, 262 scanlines/frame. Vblank starts at
 * scanline 241 dot 1 and clears at the start of the pre-render scanline (261, dot 1) - matching real
 * hardware, this happens automatically every frame regardless of whether {@code $2002} is ever read.
 * NMI is modeled as a real level (vblank flag AND the control register's NMI-enable bit) with edge
 * detection, not a one-shot check at vblank's start: this also reproduces the documented hardware
 * quirk where enabling NMI while the vblank flag is already set fires an NMI immediately. Callers
 * drive the CPU's actual NMI line from {@link #consumeNmiEdge()}, one-shot per rising edge (mirrors
 * {@code MOS6502}'s own {@code signalNMI()}/{@code consumeNMI()} edge-latch pattern) - see how
 * {@code NES.java} wires this in exactly like the existing APU-IRQ listener.
 *
 * Simplifications: no odd-frame dot skip (341*262 isn't evenly divisible by 3, so the exact CPU-cycle
 * offset of vblank drifts by fractions of a cycle frame to frame - harmless here, only the dot
 * position within a frame matters). {@code $2007}'s VRAM access goes straight to a flat 16KB buffer,
 * not real nametable-mirroring-aware VRAM or cartridge CHR-ROM, and skips the well-known "read buffer"
 * quirk (a $2007 read normally returns the *previous* buffered byte, not the one just addressed) -
 * fine since nothing renders yet. {@code $4014} OAM DMA isn't wired to this class's OAM at all yet
 * (still a no-op, same as before this class existed) - sprite content doesn't matter without
 * rendering, and there's still no CPU-stall mechanism in {@code Clock}/{@code ClockWatcher} (same
 * pre-existing gap as the DMC DMA-stall simplification noted in {@code DMCChannel}).
 */
public class PPU implements ClockWatcher, MemoryBus {
    static final int DOTS_PER_SCANLINE = 341;
    static final int SCANLINES_PER_FRAME = 262;
    static final int DOTS_PER_CPU_CYCLE = 3;
    static final int VBLANK_START_SCANLINE = 241;
    static final int VBLANK_END_SCANLINE = 261;
    static final int VBLANK_EDGE_DOT = 1;

    //341 dots/scanline isn't a multiple of the 3 dots/CPU-cycle rate, so the target dot doesn't
    //always land on a tick boundary - round up (ceiling division) to the tick whose 3-dot span
    //contains it. advanceDot() itself checks after every individual dot, not just per tick(), so
    //this only matters for computing these two "how many ticks until X" constants, not correctness.
    /** CPU ticks from power-on/reset until vblank first starts - exact, useful for tests. */
    static final int TICKS_UNTIL_VBLANK_START =
            ceilingDivide(VBLANK_START_SCANLINE * DOTS_PER_SCANLINE + VBLANK_EDGE_DOT, DOTS_PER_CPU_CYCLE);
    /** CPU ticks from power-on/reset until vblank first clears (pre-render scanline). */
    static final int TICKS_UNTIL_VBLANK_END =
            ceilingDivide(VBLANK_END_SCANLINE * DOTS_PER_SCANLINE + VBLANK_EDGE_DOT, DOTS_PER_CPU_CYCLE);

    private static int ceilingDivide(final int numerator, final int denominator){
        return (numerator + denominator - 1) / denominator;
    }

    private static final int REGISTER_ADDRESS_MASK = 0x07;
    private static final int PPUCTRL = 0x00;
    private static final int PPUMASK = 0x01;
    private static final int PPUSTATUS = 0x02;
    private static final int OAMADDR = 0x03;
    private static final int OAMDATA = 0x04;
    private static final int PPUSCROLL = 0x05;
    private static final int PPUADDR = 0x06;
    private static final int PPUDATA = 0x07;

    private static final int NMI_ENABLE_BIT = 0x80;
    private static final int VRAM_INCREMENT_BIT = 0x04;
    private static final int VRAM_INCREMENT_32 = 32;
    private static final int VRAM_INCREMENT_1 = 1;
    private static final int VBLANK_STATUS_BIT = 0x80;

    private static final int OAM_SIZE = 0x100;
    private static final int VRAM_SIZE = 0x4000;
    private static final int VRAM_ADDRESS_MASK = 0x3FFF;
    private static final int ADDRESS_HIGH_BYTE_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    private final int[] oam = new int[OAM_SIZE];
    private final int[] vram = new int[VRAM_SIZE];

    private int dot;
    private int scanline;

    private boolean vblankFlag;
    private boolean nmiEnabled;
    private boolean previousNmiLine;
    private boolean nmiEdgePending;

    private int controlRegister;
    private int maskRegister;
    private int vramIncrement = VRAM_INCREMENT_1;
    private int oamAddress;

    private boolean addressLatch;
    private int addressHighByte;
    private int vramAddress;
    private int scrollX;
    private int scrollY;

    /** CPU-cycle clock: the PPU itself runs at 3x this rate (once per dot, 3 dots per CPU cycle). */
    @Override
    public void tick(){
        for (int i = 0; i < DOTS_PER_CPU_CYCLE; i++){
            advanceDot();
        }
    }

    private void advanceDot(){
        dot++;
        if (dot >= DOTS_PER_SCANLINE){
            dot = 0;
            scanline++;
            if (scanline >= SCANLINES_PER_FRAME){
                scanline = 0;
            }
        }
        if (scanline == VBLANK_START_SCANLINE && dot == VBLANK_EDGE_DOT){
            setVblankFlag(true);
        } else if (scanline == VBLANK_END_SCANLINE && dot == VBLANK_EDGE_DOT){
            setVblankFlag(false);
        }
    }

    private void setVblankFlag(final boolean asserted){
        vblankFlag = asserted;
        updateNmiLine();
    }

    /** NMI is a level (vblank && enabled) with edge detection, not a one-shot check at vblank start. */
    private void updateNmiLine(){
        final boolean currentNmiLine = vblankFlag && nmiEnabled;
        if (currentNmiLine && !previousNmiLine){
            nmiEdgePending = true;
        }
        previousNmiLine = currentNmiLine;
    }

    /** True exactly once per vblank/NMI-enable rising edge - the caller drives cpu.signalNMI() from this. */
    public boolean consumeNmiEdge(){
        if (nmiEdgePending){
            nmiEdgePending = false;
            return true;
        }
        return false;
    }

    @Override
    public int read(final int address){
        return switch (address & REGISTER_ADDRESS_MASK){
            case PPUSTATUS -> readStatusRegister();
            case OAMDATA -> oam[oamAddress];
            case PPUDATA -> readDataRegister();
            default -> 0; //write-only registers: real hardware returns open bus, unmodeled here
        };
    }

    @Override
    public void write(final int address, final int value){
        switch (address & REGISTER_ADDRESS_MASK){
            case PPUCTRL -> writeControlRegister(value);
            case PPUMASK -> maskRegister = value & BYTE_MASK;
            case OAMADDR -> oamAddress = value & BYTE_MASK;
            case OAMDATA -> writeOamData(value);
            case PPUSCROLL -> writeScrollRegister(value);
            case PPUADDR -> writeAddressRegister(value);
            case PPUDATA -> writeDataRegister(value);
            default -> { }
        }
    }

    /**
     * Handle a $2000 write.
     *
     * Register: VPHB SINN
     * V (bit 7): NMI enable
     * I (bit 2): VRAM address increment per $2007 access (0=1, 1=32)
     * Remaining bits (nametable select, sprite/background pattern table, sprite size, PPU
     * master/slave) captured in {@link #controlRegister()} but unused - no rendering yet.
     */
    private void writeControlRegister(final int value){
        controlRegister = value & BYTE_MASK;
        nmiEnabled = (value & NMI_ENABLE_BIT) != 0;
        vramIncrement = (value & VRAM_INCREMENT_BIT) != 0 ? VRAM_INCREMENT_32 : VRAM_INCREMENT_1;
        updateNmiLine();
    }

    /**
     * Handle a $2002 read: vblank flag in bit 7 (sprite-0-hit/overflow bits always 0, unmodeled).
     * Clears the vblank flag and resets the write-latch shared by $2005/$2006.
     *
     * Deliberately does *not* call {@link #updateNmiLine()}: doing so here can only ever matter on a
     * falling transition (this read only ever clears vblankFlag, never sets it), which never sets
     * {@code nmiEdgePending} - and the pre-render scanline's own {@link #setVblankFlag(boolean)} call
     * always runs again before any later rising edge is possible, correctly resyncing
     * {@code previousNmiLine} by then regardless. Confirmed by simulation, not just by inspection.
     */
    private int readStatusRegister(){
        final int result = vblankFlag ? VBLANK_STATUS_BIT : 0;
        vblankFlag = false;
        addressLatch = false;
        return result;
    }

    private void writeOamData(final int value){
        oam[oamAddress] = value & BYTE_MASK;
        oamAddress = (oamAddress + 1) & BYTE_MASK;
    }

    /** Handle a $2005 write: first write is X scroll, second is Y scroll - stored, unused (no rendering). */
    private void writeScrollRegister(final int value){
        if (!addressLatch){
            scrollX = value & BYTE_MASK;
        } else {
            scrollY = value & BYTE_MASK;
        }
        addressLatch = !addressLatch;
    }

    /**
     * Handle a $2006 write: first write is the address high byte, second the low byte - same
     * write-latch as $2005. The high byte only needs masking to a plain byte here - shifting it left
     * 8 and masking the combined result to 14 bits (below) already discards its top 2 bits, so a
     * tighter mask at this point would be redundant, not just simpler.
     */
    private void writeAddressRegister(final int value){
        if (!addressLatch){
            addressHighByte = value & BYTE_MASK;
        } else {
            vramAddress = ((addressHighByte << ADDRESS_HIGH_BYTE_SHIFT) | (value & BYTE_MASK)) & VRAM_ADDRESS_MASK;
        }
        addressLatch = !addressLatch;
    }

    private int readDataRegister(){
        final int value = vram[vramAddress];
        vramAddress = (vramAddress + vramIncrement) & VRAM_ADDRESS_MASK;
        return value;
    }

    private void writeDataRegister(final int value){
        vram[vramAddress] = value & BYTE_MASK;
        vramAddress = (vramAddress + vramIncrement) & VRAM_ADDRESS_MASK;
    }

    int controlRegister(){
        return controlRegister;
    }

    int maskRegister(){
        return maskRegister;
    }

    int scrollX(){
        return scrollX;
    }

    int scrollY(){
        return scrollY;
    }

    int vramAddress(){
        return vramAddress;
    }

    int scanline(){
        return scanline;
    }

    int dot(){
        return dot;
    }
}
