package com.rox.ppu;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.Mirroring;
import com.rox.clock.ClockWatcher;
import com.rox.mem.OamDmaBus;

/**
 * Headless NES PPU: correct vblank/NMI timing, full {@code $0000-$3FFF} PPU address space wiring
 * (CHR pattern tables via the cartridge, mirrored nametable RAM, palette RAM), the "loopy" internal
 * scroll/address registers, and OAM DMA - no pixel rendering, no framebuffer yet (that's a later
 * phase). Registers repeat every 8 bytes through {@code $2000-$3FFF}.
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
 * The internal current/temporary VRAM address, fine-X-scroll and write-toggle fields follow nesdev's
 * standard "loopy" model (traditionally named {@code v}/{@code t}/{@code x}/{@code w} there):
 * {@link #currentVramAddress} (used for {@code $2007} access) and {@link #temporaryVramAddress}
 * (staged by {@code $2005}/{@code $2006} until latched into {@code currentVramAddress}) are both
 * conceptually 15 bits laid out as {@code 0yyy NNYY YYYX XXXX} (fine Y, nametable select, coarse Y,
 * coarse X); {@link #fineXScroll} is 3 bits; {@link #writeToggle} is shared by
 * {@code $2005}/{@code $2006}, reset by a {@code $2002} read. This phase only implements the
 * register-level bit manipulation ({@code $2000}
 * nametable-select bits, {@code $2005} coarse/fine split, {@code $2006} address latching) - the
 * rendering-time-only updates (coarse-X increment during fetches, Y increment at dot 256, the
 * dot-257/280-304 horizontal/vertical copies) belong to the not-yet-written rendering pipeline.
 *
 * Simplifications: no odd-frame dot skip (341*262 isn't evenly divisible by 3, so the exact CPU-cycle
 * offset of vblank drifts by fractions of a cycle frame to frame - harmless here, only the dot
 * position within a frame matters). Four-screen nametable mirroring isn't modeled (see
 * {@link Mirroring}). OAM DMA always stalls the CPU a fixed 514 cycles, not real hardware's 513 (even
 * start cycle) or 514 (odd) - no total-cycle counter exists anywhere in the CPU to detect that parity,
 * and the 1-cycle difference doesn't affect correctness, only real-hardware-exact timing.
 */
public class PPU implements ClockWatcher, OamDmaBus {
    public static final int DOTS_PER_SCANLINE = 341;
    public static final int SCANLINES_PER_FRAME = 262;
    public static final int FRAMEBUFFER_WIDTH = 256;
    public static final int FRAMEBUFFER_HEIGHT = 240;
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
    private static final int OAM_ADDRESS_MASK = 0xFF;
    private static final int VRAM_ADDRESS_MASK = 0x3FFF;
    private static final int LOOPY_REGISTER_MASK = 0x7FFF;
    private static final int ADDRESS_HIGH_BYTE_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    private static final int CHR_END_ADDRESS = 0x2000; //exclusive - $0000-$1FFF
    private static final int PALETTE_START_ADDRESS = 0x3F00; //inclusive - $3F00-$3FFF
    private static final int NAMETABLE_SIZE = 0x800; //2KB physical nametable RAM
    private static final int NAMETABLE_OFFSET_MASK = 0x3FF; //1KB per logical nametable
    private static final int NAMETABLE_TABLE_SHIFT = 10;
    private static final int NAMETABLE_TABLE_MASK = 0x03;
    private static final int PALETTE_SIZE = 0x20;
    private static final int PALETTE_INDEX_MASK = 0x1F;
    private static final int PALETTE_BACKDROP_MIRROR_BIT = 0x10;

    //t/v bit layout: 0yyy NNYY YYYX XXXX (fine Y | nametable select | coarse Y | coarse X)
    private static final int COARSE_X_MASK = 0x1F;
    private static final int COARSE_Y_SHIFT = 5;
    private static final int COARSE_Y_MASK = 0x1F;
    private static final int NAMETABLE_SELECT_SHIFT = 10;
    private static final int NAMETABLE_SELECT_MASK = 0x03;
    private static final int FINE_Y_SHIFT = 12;
    private static final int FINE_Y_MASK = 0x07;
    private static final int FINE_X_MASK = 0x07;
    private static final int COARSE_X_CLEAR_MASK = ~COARSE_X_MASK & LOOPY_REGISTER_MASK;
    private static final int COARSE_Y_CLEAR_MASK = ~(COARSE_Y_MASK << COARSE_Y_SHIFT) & LOOPY_REGISTER_MASK;
    private static final int NAMETABLE_SELECT_CLEAR_MASK = ~(NAMETABLE_SELECT_MASK << NAMETABLE_SELECT_SHIFT) & LOOPY_REGISTER_MASK;
    private static final int FINE_Y_CLEAR_MASK = ~(FINE_Y_MASK << FINE_Y_SHIFT) & LOOPY_REGISTER_MASK;
    private static final int ADDRESS_HIGH_BYTE_MASK = 0x3F;
    private static final int ADDRESS_HIGH_BYTE_CLEAR_MASK = 0x00FF;
    private static final int ADDRESS_LOW_BYTE_CLEAR_MASK = 0x7F00;

    private static final int OAM_DMA_STALL_CYCLES = 514;

    private final Cartridge cartridge;

    private final int[] oam = new int[OAM_SIZE];
    private final int[] nametableRam = new int[NAMETABLE_SIZE];
    private final int[] paletteRam = new int[PALETTE_SIZE];

    /**  a unit of time in which we advance by exactly one pixel-column's worth of work, whether or not that pixel is currently visible on screen. */
    private int dot;
    /** 341 dots, 256 of which are visible pixels */
    private int scanline;

    private boolean vblankFlag;
    private boolean nmiEnabled;
    private boolean previousNmiLine;
    private boolean nmiEdgePending;

    private int controlRegister;
    private int maskRegister;
    private int vramIncrement = VRAM_INCREMENT_1;
    private int oamAddress;

    private boolean writeToggle; //shared $2005/$2006 write toggle
    private int temporaryVramAddress; //staged by $2005/$2006 until latched into currentVramAddress
    private int currentVramAddress; //used for $2007 access
    private int fineXScroll; //3 bits
    private int readBuffer; //$2007's one-read-of-latency buffer for non-palette addresses

    private boolean oamDmaPending;

    /** One raw palette-RAM index (0-63) per pixel, not RGB yet - see {@code NesPalette} in a later phase. */
    private final int[] framebuffer = new int[FRAMEBUFFER_WIDTH * FRAMEBUFFER_HEIGHT];
    private boolean frameReady;

    public PPU(final Cartridge cartridge){
        this.cartridge = cartridge;
    }

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
            frameReady = true;
        } else if (scanline == VBLANK_END_SCANLINE && dot == VBLANK_EDGE_DOT){
            setVblankFlag(false);
        }
    }

    /** True exactly once per frame (set at scanline 241 dot 1) - mirrors {@link #consumeNmiEdge()}'s one-shot pattern. */
    public boolean consumeFrameReady(){
        if (frameReady){
            frameReady = false;
            return true;
        }
        return false;
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
     * NN (bits 0-1): nametable select, latched into t's nametable-select bits (10-11) - takes effect
     * the next time v is reloaded from t (a $2006 second write, or the rendering pipeline's own
     * horizontal/vertical copies).
     * Remaining bits (sprite/background pattern table, sprite size, PPU master/slave) captured in
     * {@link #controlRegister()} but unused - no rendering yet.
     */
    private void writeControlRegister(final int value){
        controlRegister = value & BYTE_MASK;
        nmiEnabled = (value & NMI_ENABLE_BIT) != 0;
        vramIncrement = (value & VRAM_INCREMENT_BIT) != 0 ? VRAM_INCREMENT_32 : VRAM_INCREMENT_1;
        temporaryVramAddress = (temporaryVramAddress & NAMETABLE_SELECT_CLEAR_MASK)
                | ((value & NAMETABLE_SELECT_MASK) << NAMETABLE_SELECT_SHIFT);
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
        writeToggle = false;
        return result;
    }

    private void writeOamData(final int value){
        oam[oamAddress] = value & BYTE_MASK;
        oamAddress = (oamAddress + 1) & OAM_ADDRESS_MASK;
    }

    /**
     * Handle a $2005 write (loopy model): first write sets {@link #fineXScroll} and
     * {@link #temporaryVramAddress}'s coarse X bits (0-4); second write sets its fine Y bits (12-14)
     * and coarse Y bits (5-9). The legacy {@link #scrollX()}/{@link #scrollY()} test accessors
     * reconstruct the original byte values from these fields.
     */
    private void writeScrollRegister(final int value){
        if (!writeToggle){
            fineXScroll = value & FINE_X_MASK;
            temporaryVramAddress = (temporaryVramAddress & COARSE_X_CLEAR_MASK) | (value >> 3);
        } else {
            temporaryVramAddress = (temporaryVramAddress & FINE_Y_CLEAR_MASK) | ((value & FINE_X_MASK) << FINE_Y_SHIFT);
            temporaryVramAddress = (temporaryVramAddress & COARSE_Y_CLEAR_MASK) | ((value >> 3) << COARSE_Y_SHIFT);
        }
        writeToggle = !writeToggle;
    }

    /**
     * Handle a $2006 write (loopy model): first write sets {@link #temporaryVramAddress}'s high 6 bits
     * (8-13) and clears the unused 15th bit; second write sets its low 8 bits and latches
     * {@code currentVramAddress = temporaryVramAddress} - real hardware only updates the *visible*
     * VRAM address on the second write, not the first.
     */
    private void writeAddressRegister(final int value){
        if (!writeToggle){
            temporaryVramAddress = ((temporaryVramAddress & ADDRESS_HIGH_BYTE_CLEAR_MASK)
                    | ((value & ADDRESS_HIGH_BYTE_MASK) << ADDRESS_HIGH_BYTE_SHIFT))
                    & LOOPY_REGISTER_MASK;
        } else {
            temporaryVramAddress = (temporaryVramAddress & ADDRESS_LOW_BYTE_CLEAR_MASK) | (value & BYTE_MASK);
            currentVramAddress = temporaryVramAddress;
        }
        writeToggle = !writeToggle;
    }

    /**
     * Handle a $2007 read: addresses $0000-$3EFF return a buffered byte from the *previous* $2007
     * read (real hardware's well-known one-read-of-latency quirk), while palette addresses
     * ($3F00-$3FFF) return their value immediately - but still refresh the buffer with the nametable
     * byte "underneath" that palette mirror (real hardware physically stores nametable data at those
     * VRAM addresses too; the palette read just bypasses it). Both paths auto-increment
     * {@link #currentVramAddress}.
     */
    private int readDataRegister(){
        final int address = currentVramAddress & VRAM_ADDRESS_MASK;
        final int result;
        if (address >= PALETTE_START_ADDRESS){
            result = readMemory(address);
            //real hardware still drives the nametable RAM address bus underneath a palette read -
            //$3F00-$3FFF's "underneath" address is $2F00-$2FFF (i.e. address - $1000), which
            //readMemory correctly routes to nametableRam since it's below PALETTE_START_ADDRESS
            readBuffer = readMemory(address - 0x1000);
        } else {
            result = readBuffer;
            readBuffer = readMemory(address);
        }
        currentVramAddress = (currentVramAddress + vramIncrement) & LOOPY_REGISTER_MASK;
        return result;
    }

    private void writeDataRegister(final int value){
        writeMemory(currentVramAddress & VRAM_ADDRESS_MASK, value);
        currentVramAddress = (currentVramAddress + vramIncrement) & LOOPY_REGISTER_MASK;
    }

    /** $0000-$1FFF CHR (cartridge pattern tables), $2000-$3EFF nametables (mirrored), $3F00-$3FFF palette. */
    private int readMemory(final int address){
        if (address < CHR_END_ADDRESS){
            return cartridge.readChr(address);
        }
        if (address < PALETTE_START_ADDRESS){
            return nametableRam[resolveNametableIndex(address)];
        }
        return paletteRam[resolvePaletteIndex(address)];
    }

    private void writeMemory(final int address, final int value){
        if (address < CHR_END_ADDRESS){
            cartridge.writeChr(address, value & BYTE_MASK);
        } else if (address < PALETTE_START_ADDRESS){
            nametableRam[resolveNametableIndex(address)] = value & BYTE_MASK;
        } else {
            paletteRam[resolvePaletteIndex(address)] = value & BYTE_MASK;
        }
    }

    /** Resolves a $2000-$3EFF address (already known to be below $3F00) to a physical nametable-RAM index. */
    private int resolveNametableIndex(final int address){
        final int nametableAddress = address & 0x0FFF; //fold $3000-$3EFF's mirror of $2000-$2EFF down
        final int logicalTable = (nametableAddress >> NAMETABLE_TABLE_SHIFT) & NAMETABLE_TABLE_MASK;
        final int offset = nametableAddress & NAMETABLE_OFFSET_MASK;
        final int physicalTable = resolvePhysicalNametable(logicalTable);
        return physicalTable * 0x400 + offset;
    }

    public int resolvePhysicalNametable(final int logicalTable){
        return switch (cartridge.nametableMirroring()){
            case HORIZONTAL -> logicalTable >> 1;
            case VERTICAL -> logicalTable & 0x01;
            case SINGLE_SCREEN_LOWER -> 0;
            case SINGLE_SCREEN_UPPER -> 1;
        };
    }

    /**
     * Resolves a $3F00-$3FFF address to a palette-RAM index, folding in the backdrop-mirror quirk:
     * only $3F10/$3F14/$3F18/$3F1C mirror $3F00/$3F04/$3F08/$3F0C (every 4th entry from $3F10), not
     * the whole $3F10-$3F1F half of the table.
     */
    private int resolvePaletteIndex(final int address){
        final int index = address & PALETTE_INDEX_MASK;
        final boolean isBackdropMirror = index >= PALETTE_BACKDROP_MIRROR_BIT && (index & 0x03) == 0;
        return isBackdropMirror ? index - PALETTE_BACKDROP_MIRROR_BIT : index;
    }

    /** OAM DMA ($4014): 256 bytes in one shot, wrapping from the current OAM address like OAMDATA writes do. */
    @Override
    public void writeOamDma(final int[] pageBytes){
        for (final int pageByte : pageBytes){
            writeOamData(pageByte);
        }
        oamDmaPending = true;
    }

    /** One-shot: returns the DMA stall length (514 cycles) once per completed DMA, 0 otherwise. */
    public int consumeOamDmaStallCycles(){
        if (oamDmaPending){
            oamDmaPending = false;
            return OAM_DMA_STALL_CYCLES;
        }
        return 0;
    }

    public int controlRegister(){
        return controlRegister;
    }

    public int maskRegister(){
        return maskRegister;
    }

    /** Reconstructs the original $2005 first-write byte value from the coarse and fine X scroll. */
    public int scrollX(){
        return ((temporaryVramAddress & COARSE_X_MASK) << 3) | fineXScroll;
    }

    /** Reconstructs the original $2005 second-write byte value from the coarse and fine Y scroll. */
    public int scrollY(){
        return (((temporaryVramAddress >> COARSE_Y_SHIFT) & COARSE_Y_MASK) << 3)
                | ((temporaryVramAddress >> FINE_Y_SHIFT) & FINE_Y_MASK);
    }

    public int vramAddress(){
        return currentVramAddress;
    }

    /**
     * The staging register ($2005/$2006 write into this, $2006's second write latches it into
     * {@link #currentVramAddress}).
     */
    int temporaryVramAddress(){
        return temporaryVramAddress;
    }

    public int scanline(){
        return scanline;
    }

    public int dot(){
        return dot;
    }

    /**
     * Peeks the vblank flag without the read-and-clear side effect real {@code $2002} access has via
     * {@link #read} - a passive observer (e.g. a debug HUD) must not be able to accidentally eat the
     * vblank flag/reset the write-latch out from under the emulated game's own polling.
     */
    public boolean vblankFlag(){
        return vblankFlag;
    }

    public int oamAddress(){
        return oamAddress;
    }

    /** Defensive copy - callers must not be able to mutate nametable RAM behind the PPU's back. */
    public int[] nametableSnapshot(){
        return nametableRam.clone();
    }

    /** Defensive copy - callers must not be able to mutate OAM behind the PPU's back. */
    public int[] oamSnapshot(){
        return oam.clone();
    }

    /** Defensive copy - callers must not be able to mutate the framebuffer behind the PPU's back. */
    public int[] framebuffer(){
        return framebuffer.clone();
    }
}
