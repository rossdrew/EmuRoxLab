package com.rox.ppu;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.Mirroring;
import com.rox.clock.ClockWatcher;
import com.rox.mem.OamDmaBus;

import static com.rox.ByteUtil.BYTE_MASK;

/**
 * Headless NES PPU: correct vblank/NMI timing, full {@code $0000-$3FFF} PPU address space wiring
 * (CHR pattern tables via the cartridge, mirrored nametable RAM, palette RAM), the "loopy" internal
 * scroll/address registers, OAM DMA, and background + sprite rendering to a {@link #framebuffer()} of
 * raw palette indices (0-63) - {@link #rgbFramebuffer()} converts those through {@link NesPalette} for
 * actual display. Registers repeat every 8 bytes through {@code $2000-$3FFF}.
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
 * {@code $2005}/{@code $2006}, reset by a {@code $2002} read. Besides the register-level bit
 * manipulation ({@code $2000} nametable-select bits, {@code $2005} coarse/fine split, {@code $2006}
 * address latching), {@code currentVramAddress} also drives the background rendering pipeline's own
 * rendering-time-only updates: coarse-X increment during fetches, Y increment at dot 256, and the
 * dot-257/280-304 horizontal/vertical copies from {@code temporaryVramAddress} - see
 * {@link #backgroundStep()}.
 *
 * Simplifications: no odd-frame dot skip (341*262 isn't evenly divisible by 3, so the exact CPU-cycle
 * offset of vblank drifts by fractions of a cycle frame to frame - harmless here, only the dot
 * position within a frame matters). Four-screen nametable mirroring isn't modeled (see
 * {@link Mirroring}). OAM DMA always stalls the CPU a fixed 514 cycles, not real hardware's 513 (even
 * start cycle) or 514 (odd) - no total-cycle counter exists anywhere in the CPU to detect that parity,
 * and the 1-cycle difference doesn't affect correctness, only real-hardware-exact timing.
 *
 * Sprite pipeline simplifications: real hardware spreads secondary-OAM evaluation and sprite pattern
 * fetching across dots 65-256 and 257-320 respectively; both are collapsed here into a single step (at
 * dots 65 and 257) since nothing outside the PPU can observe mid-evaluation/mid-fetch state - the
 * resulting secondary OAM and fetched pattern bytes are byte-for-byte identical to real hardware's by
 * the time the next scanline starts. Sprite overflow uses a simple "found more than 8 sprites in range"
 * count, not real hardware's well-known buggy diagonal-read overflow detection (see nesdev's "Sprite
 * overflow bug") - only the obscure false-positive/false-negative edge cases differ, not correct
 * rendering. OAMADDR's glitchy behaviour during evaluation (real hardware corrupts low OAM entries if
 * OAMADDR isn't 0 at the start of a scanline) isn't modeled.
 */
public class PPU implements ClockWatcher, OamDmaBus {
    public static final int FRAMEBUFFER_WIDTH = 256;
    public static final int FRAMEBUFFER_HEIGHT = 240;

    /**
     * Where things fall on the 341-dot/scanline, 262-scanline/frame grid: scanlines 0-239 are visible,
     * 240 is post-render (idle), 241-260 are vertical blank, 261 is the pre-render scanline.
     * {@code DOTS_PER_SCANLINE}/{@code SCANLINES_PER_FRAME} are public since the debug viewer's
     * {@code BeamPositionPanel} draws against them directly; everything else here is {@code PPU}-internal.
     */
    public static final class FrameTiming {
        public static final int DOTS_PER_SCANLINE = 341;
        public static final int SCANLINES_PER_FRAME = 262;
        static final int DOTS_PER_CPU_CYCLE = 3;
        static final int VBLANK_START_SCANLINE = 241;
        static final int VBLANK_END_SCANLINE = 261;
        static final int VBLANK_EDGE_DOT = 1;
        static final int VISIBLE_SCANLINE_MAX = 239;

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

        private FrameTiming(){
        }
    }

    /** {@code $2000-$2007}'s CPU-bus offsets - registers repeat every 8 bytes through {@code $2000-$3FFF}. */
    private static final class RegisterAddress {
        static final int MASK = 0x07;
        static final int PPUCTRL = 0x00;
        static final int PPUMASK = 0x01;
        static final int PPUSTATUS = 0x02;
        static final int OAMADDR = 0x03;
        static final int OAMDATA = 0x04;
        static final int PPUSCROLL = 0x05;
        static final int PPUADDR = 0x06;
        static final int PPUDATA = 0x07;

        private RegisterAddress(){
        }
    }

    private static final int VBLANK_STATUS_BIT = 0x80;

    private static final int OAM_SIZE = 0x100;
    private static final int OAM_ADDRESS_MASK = 0xFF;
    private static final int VRAM_ADDRESS_MASK = 0x3FFF;
    private static final int LOOPY_REGISTER_MASK = 0x7FFF;
    private static final int ADDRESS_HIGH_BYTE_SHIFT = 8;

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

    //background rendering pipeline (nesdev's standard 8-dot tile-fetch cycle + shift registers),
    //verified dot-for-dot against nesdev's PPU_scrolling/PPU_rendering/PPU_palettes wiki pages
    private static final int TILE_FETCH_GROUP_DOTS = 8;
    private static final int MAIN_FETCH_REGION_END_DOT = 256; //dots 1-256
    private static final int HORIZONTAL_COPY_DOT = 257;
    private static final int PREFETCH_REGION_START_DOT = 321;
    private static final int PREFETCH_REGION_END_DOT = 336; //dots 321-336: next scanline's first 2 tiles
    private static final int PREFETCH_RELOAD_DOT = 337; //reloads the 2nd prefetched tile's data
    private static final int VERTICAL_COPY_START_DOT = 280;
    private static final int VERTICAL_COPY_END_DOT = 304; //pre-render only, dots 280-304 inclusive

    private static final int TILE_BYTES = 16; //8-byte low bitplane + 8-byte high bitplane
    private static final int PATTERN_HIGH_PLANE_OFFSET = 8;

    private static final int SHIFT_REGISTER_MASK = 0xFFFF;
    private static final int SHIFT_REGISTER_LOW_BYTE_CLEAR_MASK = 0xFF00;
    private static final int PIXEL_MUX_MSB = 0x8000;

    private static final int NAMETABLE_BASE_ADDRESS = 0x2000;
    private static final int NAMETABLE_FETCH_ADDRESS_MASK = 0x0FFF;
    private static final int ATTRIBUTE_BASE_ADDRESS = 0x23C0;
    private static final int ATTRIBUTE_NAMETABLE_MASK = 0x0C00;
    private static final int ATTRIBUTE_COARSE_Y_SHIFT_IN_ADDRESS = 4;
    private static final int ATTRIBUTE_COARSE_Y_ADDRESS_MASK = 0x38;
    private static final int ATTRIBUTE_COARSE_X_SHIFT_IN_ADDRESS = 2;
    private static final int ATTRIBUTE_COARSE_X_ADDRESS_MASK = 0x07;
    private static final int ATTRIBUTE_QUADRANT_BIT = 0x02;
    private static final int ATTRIBUTE_GROUP_MASK = 0x03;

    private static final int COARSE_X_MAX = 31; //wraps to 0 + toggles the nametable-X bit
    private static final int NAMETABLE_X_TOGGLE_BIT = 1 << NAMETABLE_SELECT_SHIFT; //bit 10 = 0x0400
    private static final int NAMETABLE_Y_TOGGLE_BIT = 1 << (NAMETABLE_SELECT_SHIFT + 1); //bit 11 = 0x0800
    private static final int FINE_Y_FULL_MASK = FINE_Y_MASK << FINE_Y_SHIFT; //0x7000
    private static final int COARSE_Y_WRAP_WITH_TOGGLE = 29; //last real row of tiles
    private static final int COARSE_Y_WRAP_WITHOUT_TOGGLE = 31; //attribute-table rows, no nametable toggle
    private static final int HORIZONTAL_COPY_MASK = COARSE_X_MASK | NAMETABLE_X_TOGGLE_BIT; //0x041F
    private static final int VERTICAL_COPY_MASK = FINE_Y_FULL_MASK | NAMETABLE_Y_TOGGLE_BIT
            | (COARSE_Y_MASK << COARSE_Y_SHIFT); //0x7BE0

    private static final int PALETTE_COLOR_MASK = 0x3F; //6-bit NES master-palette index (0-63)

    //sprite rendering pipeline (nesdev's standard OAM evaluation + pattern fetch + priority mux) - see
    //the class javadoc's "Sprite pipeline simplifications" paragraph for how this collapses real
    //hardware's multi-dot evaluation/fetch windows into single steps.
    private static final int OAM_SPRITE_COUNT = 64;
    private static final int OAM_BYTES_PER_SPRITE = 4;
    private static final int MAX_SPRITES_PER_SCANLINE = 8;
    private static final int SPRITE_HEIGHT_SHORT = 8;
    private static final int SPRITE_HEIGHT_TALL = 16;
    private static final int SPRITE_EVALUATION_DOT = 65;
    private static final int TALL_SPRITE_PATTERN_TABLE_SIZE = 0x1000;
    private static final int SPRITE_PALETTE_MASK = 0x03;
    private static final int SPRITE_PRIORITY_BEHIND_BACKGROUND_BIT = 0x20;
    private static final int SPRITE_FLIP_HORIZONTAL_BIT = 0x40;
    private static final int SPRITE_FLIP_VERTICAL_BIT = 0x80;
    private static final int SPRITE_PALETTE_RAM_OFFSET = 0x10; //$3F10-$3F1F vs background's $3F00-$3F0F
    private static final int SPRITE_ZERO_HIT_EXCLUDED_X = 255; //real hardware never sets the hit flag here
    private static final int SPRITE_ZERO_HIT_STATUS_BIT = 0x40;
    private static final int SPRITE_OVERFLOW_STATUS_BIT = 0x20;

    private final Cartridge cartridge;

    private final int[] oam = new int[OAM_SIZE];
    private final int[] nametableRam = new int[NAMETABLE_SIZE];
    private final int[] paletteRam = new int[PALETTE_SIZE];

    /**  a unit of time in which we advance by exactly one pixel-column's worth of work, whether or not that pixel is currently visible on screen. */
    private int dot;
    /** 341 dots, 256 of which are visible pixels */
    private int scanline;

    private boolean vblankFlag;
    private boolean previousNmiLine;
    private boolean nmiEdgePending;

    private PPUControlRegister controlRegister = new PPUControlRegister(0x0);
    private PPUMaskRegister maskRegister = new PPUMaskRegister(0x0);
    private int oamAddress;

    private boolean writeToggle; //shared $2005/$2006 write toggle
    private int temporaryVramAddress; //staged by $2005/$2006 until latched into currentVramAddress
    private int currentVramAddress; //used for $2007 access
    private int fineXScroll; //3 bits
    private int readBuffer; //$2007's one-read-of-latency buffer for non-palette addresses

    private boolean oamDmaPending;

    /** One raw palette-RAM index (0-63) per pixel - {@link #rgbFramebuffer()} converts these to real colour. */
    private final int[] framebuffer = new int[FRAMEBUFFER_WIDTH * FRAMEBUFFER_HEIGHT];
    private boolean frameReady;

    //two tiles' worth of pattern/attribute data, shifted left 1 bit per dot; fineXScroll selects
    //which bit each pixel is drawn from (see drawPixel())
    private int bgPatternShiftLow;
    private int bgPatternShiftHigh;
    private int bgAttributeShiftLow;
    private int bgAttributeShiftHigh;
    //latches for the tile currently being fetched (2 tiles ahead of what's being drawn), folded
    //into the shift registers' low byte by reloadShiftRegisters()
    private int nextTileId;
    private int nextTilePaletteGroup; //2 bits (0-3)
    private int nextPatternLowByte;
    private int nextPatternHighByte;

    //secondary OAM for the upcoming scanline (up to 8 sprites x 4 bytes), rebuilt every scanline by
    //evaluateSpritesForNextScanline()
    private final int[] secondaryOam = new int[MAX_SPRITES_PER_SCANLINE * OAM_BYTES_PER_SPRITE];
    private int secondaryOamCount;
    private int secondaryOamSpriteZeroSlot = -1; //-1 if OAM sprite 0 isn't in range this scanline
    private boolean spriteOverflow;
    private boolean spriteZeroHitFlag;

    //per-slot rendering state fetchSpritesForNextScanline() loaded for the *current* scanline's active
    //sprites - pattern bytes already have horizontal flip applied, so drawPixel() never needs to know
    //about flip at all
    private final int[] spritePatternLowByte = new int[MAX_SPRITES_PER_SCANLINE];
    private final int[] spritePatternHighByte = new int[MAX_SPRITES_PER_SCANLINE];
    private final int[] spriteAttributes = new int[MAX_SPRITES_PER_SCANLINE];
    private final int[] spriteXPosition = new int[MAX_SPRITES_PER_SCANLINE];
    private final boolean[] spriteIsZero = new boolean[MAX_SPRITES_PER_SCANLINE];
    private int activeSpriteCount;

    public PPU(final Cartridge cartridge){
        this.cartridge = cartridge;
    }

    /** CPU-cycle clock: the PPU itself runs at 3x this rate (once per dot, 3 dots per CPU cycle). */
    @Override
    public void tick(){
        for (int i = 0; i < FrameTiming.DOTS_PER_CPU_CYCLE; i++){
            advanceDot();
        }
    }

    private void advanceDot(){
        dot++;
        if (dot >= FrameTiming.DOTS_PER_SCANLINE){
            dot = 0;
            scanline++;
            if (scanline >= FrameTiming.SCANLINES_PER_FRAME){
                scanline = 0;
            }
        }
        if (scanline == FrameTiming.VBLANK_START_SCANLINE && dot == FrameTiming.VBLANK_EDGE_DOT){
            setVblankFlag(true);
            frameReady = true;
        } else if (scanline == FrameTiming.VBLANK_END_SCANLINE && dot == FrameTiming.VBLANK_EDGE_DOT){
            setVblankFlag(false);
            spriteOverflow = false;
            spriteZeroHitFlag = false;
        }
        backgroundStep();
        spriteStep();
    }

    /** True exactly once per frame (set at scanline 241 dot 1) - mirrors {@link #consumeNmiEdge()}'s one-shot pattern. */
    public boolean consumeFrameReady(){
        if (frameReady){
            frameReady = false;
            return true;
        }
        return false;
    }

    /**
     * Runs on every dot of every visible (0-239) and pre-render (261) scanline. A pixel is drawn on
     * every dot 1-256 of a visible scanline regardless of whether rendering is enabled (falling back to
     * the backdrop colour when it isn't); the fetch/shift/scroll machinery below that only runs when
     * rendering is enabled - real hardware freezes the v register entirely otherwise.
     *
     * <p>The shift registers shift one dot <em>later</em> than the fetch switch's own dot range in each
     * region - dots 2-257 (not 1-256) and 322-337 (not 321-336) - and that shift happens <em>before</em>
     * this dot's pixel is drawn, not after: dot 1 draws directly from whatever the previous scanline's
     * prefetch already left sitting in the registers, with no shift of its own first. Getting either of
     * those backwards (shifting on the same 1-256/321-336 range the fetch switch uses, or drawing before
     * shifting) is a real, easy-to-make off-by-one - it shows up as the whole background shifted one
     * pixel to the right - caught here by simulating the exact algorithm against known CHR/nametable
     * fixtures before trusting it, not just by re-reading nesdev's timing diagram more carefully.
     */
    private void backgroundStep(){
        if (scanline > FrameTiming.VISIBLE_SCANLINE_MAX && scanline != FrameTiming.VBLANK_END_SCANLINE){
            return;
        }

        final boolean renderingEnabled = renderingEnabled();
        if (renderingEnabled){
            final boolean inShiftRange = (dot >= 2 && dot <= HORIZONTAL_COPY_DOT)
                    || (dot > PREFETCH_REGION_START_DOT && dot <= PREFETCH_RELOAD_DOT);
            if (inShiftRange){
                shiftBackgroundShiftRegisters();
            }
        }

        if (dot >= 1 && dot <= MAIN_FETCH_REGION_END_DOT && scanline <= FrameTiming.VISIBLE_SCANLINE_MAX){
            drawPixel(dot - 1, scanline);
        }

        if (!renderingEnabled){
            return;
        }

        final boolean inMainFetchRegion = dot >= 1 && dot <= MAIN_FETCH_REGION_END_DOT;
        final boolean inPrefetchRegion = dot >= PREFETCH_REGION_START_DOT && dot <= PREFETCH_REGION_END_DOT;
        if (inMainFetchRegion || inPrefetchRegion){
            fetchStep();
        }
        if (dot == MAIN_FETCH_REGION_END_DOT){
            incrementFineY();
        }
        if (dot == HORIZONTAL_COPY_DOT){
            reloadShiftRegisters();
            copyHorizontalBits();
        }
        if (dot == PREFETCH_RELOAD_DOT){
            reloadShiftRegisters();
        }
        if (scanline == FrameTiming.VBLANK_END_SCANLINE && dot >= VERTICAL_COPY_START_DOT && dot <= VERTICAL_COPY_END_DOT){
            copyVerticalBits();
        }
    }

    /**
     * The repeating 8-dot tile fetch (NT byte, attribute byte, pattern low, pattern high, coarse-X
     * increment) - runs during both the main fetch region (dots 1-256) and the next scanline's 2-tile
     * prefetch region (dots 321-336). Reload is skipped on the very first dot of either region (dot 1,
     * dot 321): the previous group's data was already folded in by {@link #backgroundStep()}'s explicit
     * dot-257/dot-337 reloads, so reloading again here would stomp the shift registers' just-shifted low
     * byte with stale data instead of letting it drain into the high byte - verified against nesdev's own
     * reload-cadence documentation (ticks 9, 17, ..., 257) before trusting this by-construction reasoning.
     */
    private void fetchStep(){
        switch ((dot - 1) % TILE_FETCH_GROUP_DOTS){
            case 0 -> {
                if (dot != 1 && dot != PREFETCH_REGION_START_DOT){
                    reloadShiftRegisters();
                }
                nextTileId = readMemory(NAMETABLE_BASE_ADDRESS | (currentVramAddress & NAMETABLE_FETCH_ADDRESS_MASK));
            }
            case 2 -> nextTilePaletteGroup = fetchAttributePaletteGroup();
            case 4 -> nextPatternLowByte = fetchPatternByte(0);
            case 6 -> nextPatternHighByte = fetchPatternByte(PATTERN_HIGH_PLANE_OFFSET);
            case 7 -> incrementCoarseX();
            default -> { }
        }
    }

    /** The attribute byte's 2x2 grid of 2-bit palette-group values, indexed by which quadrant coarse X/Y falls in. */
    private int fetchAttributePaletteGroup(){
        final int attributeAddress = ATTRIBUTE_BASE_ADDRESS
                | (currentVramAddress & ATTRIBUTE_NAMETABLE_MASK)
                | ((currentVramAddress >> ATTRIBUTE_COARSE_Y_SHIFT_IN_ADDRESS) & ATTRIBUTE_COARSE_Y_ADDRESS_MASK)
                | ((currentVramAddress >> ATTRIBUTE_COARSE_X_SHIFT_IN_ADDRESS) & ATTRIBUTE_COARSE_X_ADDRESS_MASK);
        final int attributeByte = readMemory(attributeAddress);
        final int coarseX = currentVramAddress & COARSE_X_MASK;
        final int coarseY = (currentVramAddress >> COARSE_Y_SHIFT) & COARSE_Y_MASK;
        final int quadrantShift = ((coarseY & ATTRIBUTE_QUADRANT_BIT) << 1) | (coarseX & ATTRIBUTE_QUADRANT_BIT);
        return (attributeByte >> quadrantShift) & ATTRIBUTE_GROUP_MASK;
    }

    private int fetchPatternByte(final int planeOffset){
        final int fineY = (currentVramAddress >> FINE_Y_SHIFT) & FINE_Y_MASK;
        return readMemory(controlRegister.backgroundPatternTableBase() + nextTileId * TILE_BYTES + fineY + planeOffset);
    }

    /** Folds the latched tile's data into the shift registers' low byte - the high byte drains from the previous tile. */
    private void reloadShiftRegisters(){
        bgPatternShiftLow = (bgPatternShiftLow & SHIFT_REGISTER_LOW_BYTE_CLEAR_MASK) | nextPatternLowByte;
        bgPatternShiftHigh = (bgPatternShiftHigh & SHIFT_REGISTER_LOW_BYTE_CLEAR_MASK) | nextPatternHighByte;
        //the 2-bit palette group is constant for all 8 pixels of a tile, so it's broadcast across a
        //whole byte (all-1s or all-0s per bit) rather than genuinely tracking 8 individual bits - a
        //well-known, behaviourally-identical simplification of real hardware's 1-bit-latch design
        final int attributeLowFill = (nextTilePaletteGroup & 0x01) != 0 ? BYTE_MASK : 0;
        final int attributeHighFill = (nextTilePaletteGroup & 0x02) != 0 ? BYTE_MASK : 0;
        bgAttributeShiftLow = (bgAttributeShiftLow & SHIFT_REGISTER_LOW_BYTE_CLEAR_MASK) | attributeLowFill;
        bgAttributeShiftHigh = (bgAttributeShiftHigh & SHIFT_REGISTER_LOW_BYTE_CLEAR_MASK) | attributeHighFill;
    }

    private void shiftBackgroundShiftRegisters(){
        bgPatternShiftLow = (bgPatternShiftLow << 1) & SHIFT_REGISTER_MASK;
        bgPatternShiftHigh = (bgPatternShiftHigh << 1) & SHIFT_REGISTER_MASK;
        bgAttributeShiftLow = (bgAttributeShiftLow << 1) & SHIFT_REGISTER_MASK;
        bgAttributeShiftHigh = (bgAttributeShiftHigh << 1) & SHIFT_REGISTER_MASK;
    }

    /** nesdev's standard coarse-X increment: wraps at 31, toggling the horizontal nametable-select bit. */
    private void incrementCoarseX(){
        if ((currentVramAddress & COARSE_X_MASK) == COARSE_X_MAX){
            currentVramAddress = (currentVramAddress & ~COARSE_X_MASK) ^ NAMETABLE_X_TOGGLE_BIT;
        } else {
            currentVramAddress = (currentVramAddress + 1) & LOOPY_REGISTER_MASK;
        }
    }

    /**
     * nesdev's standard fine/coarse-Y increment (dot 256 only): fine Y wraps into coarse Y, which
     * itself wraps at 29 (the real last row of tiles, toggling the vertical nametable-select bit) - but
     * at 31 (a coarse Y a game deliberately set out of bounds, into the attribute table) wraps to 0
     * *without* toggling the nametable bit, since that out-of-bounds value was never really "on" the
     * next nametable to begin with.
     */
    private void incrementFineY(){
        if ((currentVramAddress & FINE_Y_FULL_MASK) != FINE_Y_FULL_MASK){
            currentVramAddress = (currentVramAddress + (1 << FINE_Y_SHIFT)) & LOOPY_REGISTER_MASK;
            return;
        }
        currentVramAddress &= ~FINE_Y_FULL_MASK;
        int coarseY = (currentVramAddress >> COARSE_Y_SHIFT) & COARSE_Y_MASK;
        if (coarseY == COARSE_Y_WRAP_WITH_TOGGLE){
            coarseY = 0;
            currentVramAddress ^= NAMETABLE_Y_TOGGLE_BIT;
        } else if (coarseY == COARSE_Y_WRAP_WITHOUT_TOGGLE){
            coarseY = 0;
        } else {
            coarseY++;
        }
        currentVramAddress = ((currentVramAddress & COARSE_Y_CLEAR_MASK) | (coarseY << COARSE_Y_SHIFT)) & LOOPY_REGISTER_MASK;
    }

    /** hori(v)=hori(t): copies coarse X + the horizontal nametable-select bit, dot 257 only. */
    private void copyHorizontalBits(){
        currentVramAddress = ((currentVramAddress & ~HORIZONTAL_COPY_MASK)
                | (temporaryVramAddress & HORIZONTAL_COPY_MASK)) & LOOPY_REGISTER_MASK;
    }

    /** vert(v)=vert(t): copies fine Y + coarse Y + the vertical nametable-select bit, pre-render dots 280-304. */
    private void copyVerticalBits(){
        currentVramAddress = ((currentVramAddress & ~VERTICAL_COPY_MASK)
                | (temporaryVramAddress & VERTICAL_COPY_MASK)) & LOOPY_REGISTER_MASK;
    }

    private boolean renderingEnabled(){
        return maskRegister.renderingEnabled();
    }

    /**
     * Runs on every dot of every visible (0-239) and pre-render (261) scanline, mirroring
     * {@link #backgroundStep()}'s scanline range. Builds secondary OAM for the scanline about to start
     * at dot 65 (real hardware: dots 65-256) and fetches that scanline's sprite pattern bytes at dot 257
     * (real hardware: dots 257-320) - see the class javadoc's sprite-pipeline simplification note for why
     * collapsing each multi-dot window into a single dot is behaviourally identical.
     */
    private void spriteStep(){
        if (scanline > FrameTiming.VISIBLE_SCANLINE_MAX && scanline != FrameTiming.VBLANK_END_SCANLINE){
            return;
        }
        if (!renderingEnabled()){
            return;
        }
        if (dot == SPRITE_EVALUATION_DOT){
            evaluateSpritesForNextScanline();
        }
        if (dot == HORIZONTAL_COPY_DOT){ //dot 257, same dot as background's hori(v)=hori(t) copy
            fetchSpritesForNextScanline();
        }
    }

    /**
     * Builds secondary OAM (up to 8 sprites) for the scanline about to start, scanning primary OAM in
     * index order so slot 0 of secondary OAM (if occupied) is always the highest-priority sprite in
     * range - OAM index 0 specifically is also tracked (via {@link #secondaryOamSpriteZeroSlot}) for
     * sprite-0-hit detection. Sets {@link #spriteOverflow} (sticky until the pre-render scanline clears
     * it) when more than 8 sprites are in range; see the class javadoc for why this doesn't reproduce
     * real hardware's buggy overflow detection.
     */
    private void evaluateSpritesForNextScanline(){
        final int targetScanline = scanline == FrameTiming.VBLANK_END_SCANLINE ? 0 : scanline + 1;
        final int spriteHeight = controlRegister.tallSprites() ? SPRITE_HEIGHT_TALL : SPRITE_HEIGHT_SHORT;
        int found = 0;
        int spriteZeroSlot = -1;
        boolean overflow = false;
        for (int i = 0; i < OAM_SPRITE_COUNT; i++){
            final int base = i * OAM_BYTES_PER_SPRITE;
            final int row = targetScanline - oam[base];
            if (row < 0 || row >= spriteHeight){
                continue;
            }
            if (found < MAX_SPRITES_PER_SCANLINE){
                final int destBase = found * OAM_BYTES_PER_SPRITE;
                secondaryOam[destBase] = oam[base];
                secondaryOam[destBase + 1] = oam[base + 1];
                secondaryOam[destBase + 2] = oam[base + 2];
                secondaryOam[destBase + 3] = oam[base + 3];
                if (i == 0){
                    spriteZeroSlot = found;
                }
                found++;
            } else {
                overflow = true;
            }
        }
        secondaryOamCount = found;
        secondaryOamSpriteZeroSlot = spriteZeroSlot;
        if (overflow){
            spriteOverflow = true;
        }
    }

    /**
     * Fetches pattern-table bytes for every sprite {@link #evaluateSpritesForNextScanline()} just found,
     * for the scanline about to start. Handles 8x8 vs 8x16 sizing ({@code $2000} bit 5; for 8x16, the
     * tile index's own low bit selects the pattern table and its high bits pick which of the pair of
     * tiles) and vertical/horizontal flip - horizontal flip is resolved here, once, by reversing the
     * fetched byte's bit order, rather than being checked per pixel at draw time.
     */
    private void fetchSpritesForNextScanline(){
        final int targetScanline = scanline == FrameTiming.VBLANK_END_SCANLINE ? 0 : scanline + 1;
        final boolean tallSprites = controlRegister.tallSprites();
        final int spriteHeight = tallSprites ? SPRITE_HEIGHT_TALL : SPRITE_HEIGHT_SHORT;
        for (int slot = 0; slot < secondaryOamCount; slot++){
            final int base = slot * OAM_BYTES_PER_SPRITE;
            final int spriteY = secondaryOam[base];
            final int tileIndex = secondaryOam[base + 1];
            final int attributes = secondaryOam[base + 2];
            final boolean flipV = (attributes & SPRITE_FLIP_VERTICAL_BIT) != 0;
            final boolean flipH = (attributes & SPRITE_FLIP_HORIZONTAL_BIT) != 0;

            int row = targetScanline - spriteY;
            if (flipV){
                row = spriteHeight - 1 - row;
            }
            final int patternTableBase;
            final int patternTileIndex;
            if (tallSprites){
                patternTableBase = (tileIndex & 0x01) != 0 ? TALL_SPRITE_PATTERN_TABLE_SIZE : 0;
                final int topTile = tileIndex & 0xFE;
                if (row >= SPRITE_HEIGHT_SHORT){
                    patternTileIndex = topTile | 0x01;
                    row -= SPRITE_HEIGHT_SHORT;
                } else {
                    patternTileIndex = topTile;
                }
            } else {
                patternTableBase = controlRegister.spritePatternTableBase();
                patternTileIndex = tileIndex;
            }

            int lowByte = readMemory(patternTableBase + patternTileIndex * TILE_BYTES + row);
            int highByte = readMemory(patternTableBase + patternTileIndex * TILE_BYTES + row + PATTERN_HIGH_PLANE_OFFSET);
            if (flipH){
                lowByte = reverseBits(lowByte);
                highByte = reverseBits(highByte);
            }

            spritePatternLowByte[slot] = lowByte;
            spritePatternHighByte[slot] = highByte;
            spriteAttributes[slot] = attributes;
            spriteXPosition[slot] = secondaryOam[base + 3];
            spriteIsZero[slot] = slot == secondaryOamSpriteZeroSlot;
        }
        activeSpriteCount = secondaryOamCount;
    }

    /** Reverses an 8-bit value's bit order - implements horizontal sprite flip on a fetched pattern byte. */
    private static int reverseBits(final int value){
        int result = 0;
        int remaining = value;
        for (int bit = 0; bit < 8; bit++){
            result = (result << 1) | (remaining & 0x01);
            remaining >>= 1;
        }
        return result;
    }

    /**
     * Resolves one framebuffer pixel by compositing the background shift-register state (selected by
     * {@link #fineXScroll}, as in Phase 3/4) with up to 8 active sprites for this scanline, in OAM-index
     * priority order - the first opaque sprite found wins, both for the final colour (subject to that
     * sprite's own front/back priority bit against an opaque background pixel) and for sprite-0-hit
     * detection, since OAM index 0 (if in range) is always secondary-OAM slot 0. Falls back to the
     * backdrop colour ({@code $3F00}) when nothing is opaque - real hardware's "universal background
     * colour" quirk, same as background-only rendering already relied on.
     */
    private void drawPixel(final int x, final int y){
        final boolean backgroundVisible = maskRegister.showBackground() && (x >= 8 || maskRegister.showBackgroundLeft());

        int paletteRamIndex = 0; //the universal backdrop colour, $3F00
        int bgPixelValue = 0;
        if (backgroundVisible){
            final int bitMux = PIXEL_MUX_MSB >> fineXScroll;
            bgPixelValue = (((bgPatternShiftHigh & bitMux) != 0) ? 2 : 0)
                    | (((bgPatternShiftLow & bitMux) != 0) ? 1 : 0);
            if (bgPixelValue != 0){
                final int paletteGroup = (((bgAttributeShiftHigh & bitMux) != 0) ? 2 : 0)
                        | (((bgAttributeShiftLow & bitMux) != 0) ? 1 : 0);
                paletteRamIndex = resolvePaletteIndex(PALETTE_START_ADDRESS + (paletteGroup << 2) + bgPixelValue);
            }
        }

        final boolean spritesVisible = maskRegister.showSprites() && (x >= 8 || maskRegister.showSpritesLeft());
        if (spritesVisible){
            for (int slot = 0; slot < activeSpriteCount; slot++){
                final int offset = x - spriteXPosition[slot];
                if (offset < 0 || offset > 7){
                    continue;
                }
                final int spriteBitMux = 0x80 >> offset;
                final int spritePixelValue = (((spritePatternHighByte[slot] & spriteBitMux) != 0) ? 2 : 0)
                        | (((spritePatternLowByte[slot] & spriteBitMux) != 0) ? 1 : 0);
                if (spritePixelValue == 0){
                    continue;
                }
                if (spriteIsZero[slot] && bgPixelValue != 0 && x != SPRITE_ZERO_HIT_EXCLUDED_X){
                    spriteZeroHitFlag = true;
                }
                final boolean behindBackground = (spriteAttributes[slot] & SPRITE_PRIORITY_BEHIND_BACKGROUND_BIT) != 0;
                if (bgPixelValue == 0 || !behindBackground){
                    final int paletteGroup = spriteAttributes[slot] & SPRITE_PALETTE_MASK;
                    paletteRamIndex = resolvePaletteIndex(PALETTE_START_ADDRESS + SPRITE_PALETTE_RAM_OFFSET
                            + (paletteGroup << 2) + spritePixelValue);
                }
                break; //OAM-index priority: the first opaque sprite found wins, both for colour and hit detection
            }
        }
        framebuffer[y * FRAMEBUFFER_WIDTH + x] = paletteRam[paletteRamIndex] & PALETTE_COLOR_MASK;
    }

    private void setVblankFlag(final boolean asserted){
        vblankFlag = asserted;
        updateNmiLine();
    }

    /** NMI is a level (vblank && enabled) with edge detection, not a one-shot check at vblank start. */
    private void updateNmiLine(){
        final boolean currentNmiLine = vblankFlag && controlRegister.nmiEnabled();
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
        return switch (address & RegisterAddress.MASK){
            case RegisterAddress.PPUSTATUS -> readStatusRegister();
            case RegisterAddress.OAMDATA -> oam[oamAddress];
            case RegisterAddress.PPUDATA -> readDataRegister();
            default -> 0; //write-only registers: real hardware returns open bus, unmodeled here
        };
    }

    @Override
    public void write(final int address, final int value){
        switch (address & RegisterAddress.MASK){
            case RegisterAddress.PPUCTRL -> writeControlRegister(value);
            case RegisterAddress.PPUMASK -> maskRegister = new PPUMaskRegister(value);
            case RegisterAddress.OAMADDR -> oamAddress = value & BYTE_MASK;
            case RegisterAddress.OAMDATA -> writeOamData(value);
            case RegisterAddress.PPUSCROLL -> writeScrollRegister(value);
            case RegisterAddress.PPUADDR -> writeAddressRegister(value);
            case RegisterAddress.PPUDATA -> writeDataRegister(value);
            default -> { }
        }
    }

    /**
     * Handle a $2000 write - see {@link PPUControlRegister} for the full bit layout. Bit-level decoding
     * lives there; this method's own remaining concern is the nametable-select bits' side effect on the
     * loopy {@code t} register (latched into its nametable-select bits, 10-11) - takes effect the next
     * time {@code v} is reloaded from {@code t} (a $2006 second write, or the rendering pipeline's own
     * horizontal/vertical copies) - and re-checking the NMI line, since bit 7 can change it.
     */
    private void writeControlRegister(final int value){
        controlRegister = new PPUControlRegister(value);
        temporaryVramAddress = (temporaryVramAddress & NAMETABLE_SELECT_CLEAR_MASK)
                | (controlRegister.nametableSelect() << NAMETABLE_SELECT_SHIFT);
        updateNmiLine();
    }

    /**
     * Handle a $2002 read: vblank flag in bit 7, sprite-0-hit in bit 6, sprite overflow in bit 5.
     * Clears the vblank flag and resets the write-latch shared by $2005/$2006 - sprite-0-hit and
     * overflow are *not* cleared by this read, only by the pre-render scanline's dot 1 (see
     * {@link #advanceDot()}), matching real hardware.
     *
     * Deliberately does *not* call {@link #updateNmiLine()}: doing so here can only ever matter on a
     * falling transition (this read only ever clears vblankFlag, never sets it), which never sets
     * {@code nmiEdgePending} - and the pre-render scanline's own {@link #setVblankFlag(boolean)} call
     * always runs again before any later rising edge is possible, correctly resyncing
     * {@code previousNmiLine} by then regardless. Confirmed by simulation, not just by inspection.
     */
    private int readStatusRegister(){
        int result = vblankFlag ? VBLANK_STATUS_BIT : 0;
        result |= spriteZeroHitFlag ? SPRITE_ZERO_HIT_STATUS_BIT : 0;
        result |= spriteOverflow ? SPRITE_OVERFLOW_STATUS_BIT : 0;
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
        currentVramAddress = (currentVramAddress + controlRegister.vramIncrement()) & LOOPY_REGISTER_MASK;
        return result;
    }

    private void writeDataRegister(final int value){
        writeMemory(currentVramAddress & VRAM_ADDRESS_MASK, value);
        currentVramAddress = (currentVramAddress + controlRegister.vramIncrement()) & LOOPY_REGISTER_MASK;
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
            paletteRam[resolvePaletteIndex(address)] = value & PALETTE_COLOR_MASK;
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
        return controlRegister.rawValue();
    }

    /** The decoded $2000 bits (NMI enable, pattern table selects, sprite size, nametable select, ...) - see {@link PPUControlRegister}. */
    public PPUControlRegister controlRegisterDecoded(){
        return controlRegister;
    }

    public int maskRegister(){
        return maskRegister.rawValue();
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

    /** {@link #framebuffer()}'s raw palette indices, converted through {@link NesPalette} to packed {@code 0xRRGGBB} colours. */
    public int[] rgbFramebuffer(){
        final int[] rgb = new int[framebuffer.length];
        for (int i = 0; i < framebuffer.length; i++){
            rgb[i] = NesPalette.rgb(framebuffer[i]);
        }
        return rgb;
    }

    /**
     * A logical {@code $3F00-$3F1F} palette-RAM dump (32 entries), with the backdrop-mirror quirk
     * ({@link #resolvePaletteIndex}) already resolved - unlike the physical {@link #paletteRam} array,
     * where a mirrored address (e.g. {@code $3F10}) is never itself written, this returns what a real
     * read of every address in that range would actually show.
     */
    public int[] paletteSnapshot(){
        final int[] snapshot = new int[PALETTE_SIZE];
        for (int i = 0; i < PALETTE_SIZE; i++){
            snapshot[i] = paletteRam[resolvePaletteIndex(PALETTE_START_ADDRESS + i)];
        }
        return snapshot;
    }
}
