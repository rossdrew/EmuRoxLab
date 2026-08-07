package com.rox.ppu;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.INesRom;
import com.rox.cartridge.Mapper;
import com.rox.cartridge.Mirroring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.ppu.PPU.FrameTiming.DOTS_PER_CPU_CYCLE;
import static com.rox.ppu.PPU.FrameTiming.DOTS_PER_SCANLINE;
import static com.rox.ppu.PPU.FrameTiming.SCANLINES_PER_FRAME;
import static com.rox.ppu.PPU.FrameTiming.TICKS_UNTIL_VBLANK_END;
import static com.rox.ppu.PPU.FrameTiming.TICKS_UNTIL_VBLANK_START;
import static com.rox.ppu.PPU.FrameTiming.VBLANK_END_SCANLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PPUTest {
    private static final int PPUCTRL = 0x2000;
    private static final int PPUMASK = 0x2001;
    private static final int PPUSTATUS = 0x2002;
    private static final int OAMADDR = 0x2003;
    private static final int OAMDATA = 0x2004;
    private static final int PPUSCROLL = 0x2005;
    private static final int PPUADDR = 0x2006;
    private static final int PPUDATA = 0x2007;

    private static final int NMI_ENABLE = 0x80;
    private static final int VRAM_INCREMENT_32 = 0x04;
    private static final int VBLANK_BIT = 0x80;

    private static final int SHOW_BACKGROUND_LEFT = 0x02;
    private static final int SHOW_BACKGROUND = 0x08;
    private static final int SHOW_SPRITES = 0x10;
    private static final int HORIZONTAL_SCROLL_MASK = 0x041F; //coarse X (bits 0-4) + nametable-X bit (10)
    private static final int VERTICAL_SCROLL_MASK = 0x7BE0; //fine Y (12-14) + nametable-Y bit (11) + coarse Y (5-9)

    /**
     * A hand-rolled {@link Mapper} test double behaving like an 8KB CHR-RAM board with a
     * per-test-overridable mirroring mode - {@link Cartridge} is final, so it can't be mocked
     * directly; wrapping this in a real {@code Cartridge} keeps these tests focused on the PPU's own
     * address-space wiring without needing a real {@code INesRom}-backed {@code Mapper}.
     */
    private static final class FakeMapper implements Mapper {
        private final int[] chr = new int[0x2000];
        private Mirroring mirroring = Mirroring.HORIZONTAL;

        @Override public int read(final int address){ return 0; }
        @Override public void write(final int address, final int value){ }
        @Override public int readChr(final int address){ return chr[address & 0x1FFF]; }
        @Override public void writeChr(final int address, final int value){ chr[address & 0x1FFF] = value & 0xFF; }
        @Override public Mirroring nametableMirroring(){ return mirroring; }
    }

    private FakeMapper mapper;
    private PPU ppu;
    private long ticksIssued; //tracks cumulative ticks so tickTo()/tickThroughScanline() can be called more than once per test

    @BeforeEach
    public void setup(){
        final byte[] fileBytes = new byte[16 + 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 1;
        final INesRom rom = INesRom.parse(fileBytes);
        mapper = new FakeMapper();
        ppu = new PPU(new Cartridge(rom, mapper));
    }

    private void tick(final int times){
        for (int i = 0; i < times; i++){
            ppu.tick();
        }
    }

    @Test
    public void vblankFlagStartsClear(){
        assertEquals(0, ppu.read(PPUSTATUS) & VBLANK_BIT);
    }

    @Test
    public void vblankFlagSetsAtExactlyTheDocumentedTick(){
        tick(TICKS_UNTIL_VBLANK_START - 1);
        assertEquals(0, ppu.read(PPUSTATUS) & VBLANK_BIT, "not yet - one tick early");

        tick(1);
        assertEquals(VBLANK_BIT, ppu.read(PPUSTATUS) & VBLANK_BIT, "should be set now");
    }

    @Test
    public void readingStatusClearsTheVblankFlag(){
        tick(TICKS_UNTIL_VBLANK_START);
        assertEquals(VBLANK_BIT, ppu.read(PPUSTATUS) & VBLANK_BIT, "first read should observe it set");
        assertEquals(0, ppu.read(PPUSTATUS) & VBLANK_BIT, "second read should observe it already cleared");
    }

    @Test
    public void vblankFlagClearsAutomaticallyAtPreRenderScanlineWithoutBeingRead(){
        tick(TICKS_UNTIL_VBLANK_END);
        assertEquals(0, ppu.read(PPUSTATUS) & VBLANK_BIT, "should have cleared itself, never having been read");
    }

    @Test
    public void nmiFiresOnVblankStartWhenEnabled(){
        ppu.write(PPUCTRL, NMI_ENABLE);

        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeNmiEdge());
    }

    @Test
    public void nmiDoesNotFireWhenDisabled(){
        tick(TICKS_UNTIL_VBLANK_START);

        assertFalse(ppu.consumeNmiEdge());
    }

    @Test
    public void consumeNmiEdgeIsOneShot(){
        ppu.write(PPUCTRL, NMI_ENABLE);
        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeNmiEdge());
        assertFalse(ppu.consumeNmiEdge(), "the same edge must not be reported twice");
    }

    @Test
    public void enablingNmiWhileAlreadyInVblankFiresImmediately(){
        //enter vblank first, with NMI still disabled - no edge yet
        tick(TICKS_UNTIL_VBLANK_START);
        assertFalse(ppu.consumeNmiEdge());

        //now enable NMI while the vblank flag is still set - real hardware fires immediately here too
        ppu.write(PPUCTRL, NMI_ENABLE);

        assertTrue(ppu.consumeNmiEdge());
    }

    @Test
    public void disablingNmiThenReenablingDuringTheSameVblankFiresAgain(){
        ppu.write(PPUCTRL, NMI_ENABLE);
        tick(TICKS_UNTIL_VBLANK_START);
        assertTrue(ppu.consumeNmiEdge());

        ppu.write(PPUCTRL, 0); //disable
        ppu.write(PPUCTRL, NMI_ENABLE); //re-enable, still within the same vblank

        assertTrue(ppu.consumeNmiEdge(), "re-enabling within the same vblank is a fresh rising edge");
    }

    @Test
    public void registersMirrorEveryEightBytes(){
        ppu.write(PPUCTRL + 8, NMI_ENABLE); //$2008 mirrors $2000
        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeNmiEdge(), "the mirrored write should have the same effect as writing $2000 directly");
    }

    @Test
    public void controlRegisterCapturesRawValue(){
        ppu.write(PPUCTRL, 0x55);

        assertEquals(0x55, ppu.controlRegister());
    }

    @Test
    public void maskRegisterIsCapturedButOtherwiseUnused(){
        ppu.write(PPUMASK, 0x1E);

        assertEquals(0x1E, ppu.maskRegister());
    }

    @Test
    public void vramIncrementDefaultsToOne(){
        writeAddress(0x0000);
        ppu.write(PPUDATA, 0x11);

        assertEquals(1, ppu.vramAddress(), "default increment is 1");
    }

    @Test
    public void vramIncrementStaysOneWhenControlBitExplicitlyClear(){
        //writes $2000 with every bit *except* the increment bit set, to distinguish "bit correctly
        //read as clear" from a mutant that ORs it in regardless of the written value
        ppu.write(PPUCTRL, 0xFF & ~VRAM_INCREMENT_32);
        writeAddress(0x0000);

        ppu.write(PPUDATA, 0x11);

        assertEquals(1, ppu.vramAddress());
    }

    @Test
    public void vramIncrementIsThirtyTwoWhenControlBitSet(){
        ppu.write(PPUCTRL, VRAM_INCREMENT_32);
        writeAddress(0x0000);

        ppu.write(PPUDATA, 0x11);

        assertEquals(32, ppu.vramAddress());
    }

    @Test
    public void dataRegisterReadWriteRoundTripsThroughTheReadBufferDelay(){
        writeAddress(0x1234); //within CHR space ($0000-$1FFF)
        ppu.write(PPUDATA, 0x77);

        writeAddress(0x1234);
        ppu.read(PPUDATA); //primes the read buffer - see readBufferQuirkDelaysNonPaletteReadsByOneRead
        assertEquals(0x77, ppu.read(PPUDATA), "second read at the address should return the buffered byte");
    }

    @Test
    public void addressRegisterMasksToFourteenBits(){
        //$4321 & $3FFF = $0321 - the top bits of the first (high) byte write are discarded
        ppu.write(PPUADDR, 0x43);
        ppu.write(PPUADDR, 0x21);
        ppu.write(PPUDATA, 0x99);

        writeAddress(0x0321);
        ppu.read(PPUDATA); //prime the read buffer
        assertEquals(0x99, ppu.read(PPUDATA));
    }

    @Test
    public void scrollRegisterCapturesXThenYInOrder(){
        ppu.write(PPUSCROLL, 0x12); //first write -> X
        ppu.write(PPUSCROLL, 0x34); //second write -> Y

        assertEquals(0x12, ppu.scrollX());
        assertEquals(0x34, ppu.scrollY());
    }

    @Test
    public void scrollAndAddressRegistersShareTheSameWriteLatch(){
        //$2005 and $2006 toggle one shared first/second-write latch, not independent per-register
        //state - a $2006 write followed by a $2005 write must land in $2005's "second write" slot (Y)
        ppu.write(PPUADDR, 0x12); //first write overall -> $2006's high byte
        ppu.write(PPUSCROLL, 0x34); //second write overall -> lands in $2005's Y slot, not its X slot

        assertEquals(0, ppu.scrollX(), "X was never actually written to");
        assertEquals(0x34, ppu.scrollY(), "the shared latch routed this write to Y, since it was the 2nd overall");
    }

    @Test
    public void statusReadResetsTheSharedWriteLatchMidSequence(){
        ppu.write(PPUADDR, 0x12); //first write (high byte)
        ppu.read(PPUSTATUS); //resets the latch
        ppu.write(PPUADDR, 0x34); //should now be treated as a fresh first write (high byte), not the low byte
        ppu.write(PPUADDR, 0x56); //this is the real second write (low byte)

        ppu.write(PPUDATA, 0xAB);
        writeAddress(0x3456);
        ppu.read(PPUDATA); //prime the read buffer
        assertEquals(0xAB, ppu.read(PPUDATA), "address should be $3456, proving the mid-sequence reset worked");
    }

    @Test
    public void oamAddressAndDataRoundTripWithAutoIncrementOnWriteOnly(){
        ppu.write(OAMADDR, 0x10);
        ppu.write(OAMDATA, 0x9A); //auto-increments OAMADDR to 0x11
        ppu.write(OAMDATA, 0x9B);

        ppu.write(OAMADDR, 0x10);
        assertEquals(0x9A, ppu.read(OAMDATA), "read must not itself advance OAMADDR");
        assertEquals(0x9A, ppu.read(OAMDATA), "reading twice at the same address should be stable");

        ppu.write(OAMADDR, 0x11);
        assertEquals(0x9B, ppu.read(OAMDATA));
    }

    @Test
    public void dotAndScanlineAdvanceTogetherAndWrapAtScanlineEnd(){
        assertEquals(0, ppu.dot());
        assertEquals(0, ppu.scanline());

        tick(1); //3 dots: 0->1->2->3
        assertEquals(3, ppu.dot());
        assertEquals(0, ppu.scanline());

        //341 dots/scanline isn't a multiple of 3, so 341 individual dot advances (114 ticks - one
        //dot short of 342, since we've already done 3) lands 1 dot past the wrap: 3 + 339 = 342
        //total dots = 341 (one full scanline) + 1, i.e. scanline 1, dot 1
        tick(113);
        assertEquals(1, ppu.dot());
        assertEquals(1, ppu.scanline());
    }

    @Test
    public void scanlineNeverVisiblyReachesScanlinesPerFrame(){
        //scanline must wrap to 0 the instant it would reach SCANLINES_PER_FRAME (262), never letting
        //that value itself be observed - a boundary bug (e.g. off-by-one) would show up as scanline
        //visibly holding 262 for one scanline's worth of ticks before wrapping late
        final int maxTicks = (2 * SCANLINES_PER_FRAME * DOTS_PER_SCANLINE) / DOTS_PER_CPU_CYCLE + 10;
        for (int i = 0; i < maxTicks; i++){
            ppu.tick();
            assertTrue(ppu.scanline() < SCANLINES_PER_FRAME,
                    "scanline should never reach " + SCANLINES_PER_FRAME + ", was " + ppu.scanline());
        }
    }

    @Test
    public void vblankClearsAtPreRenderSoTheNextFramesVblankFiresAFreshNmiEdge(){
        //proves the pre-render-scanline clear (not just the vblank-start set) actually runs: if it
        //didn't, previousNmiLine would stay stale from the first edge and the second frame's vblank
        //start would be silently missed instead of firing a fresh edge
        ppu.write(PPUCTRL, NMI_ENABLE);
        tick(TICKS_UNTIL_VBLANK_START);
        assertTrue(ppu.consumeNmiEdge(), "first vblank should fire");

        final int maxTicks = 2 * SCANLINES_PER_FRAME * DOTS_PER_SCANLINE;
        for (int i = 0; i < maxTicks; i++){
            ppu.tick();
            if (ppu.consumeNmiEdge()){
                return; //second edge found - success
            }
        }
        fail("second vblank (next frame) never fired a fresh NMI edge");
    }

    @Test
    public void readDataRegisterAutoIncrementsAfterEachRead(){
        writeAddress(0x0000);
        ppu.write(PPUDATA, 0x11);
        ppu.write(PPUDATA, 0x22);

        writeAddress(0x0000);
        ppu.read(PPUDATA); //prime the read buffer with the byte at address 0
        assertEquals(0x11, ppu.read(PPUDATA), "first real read returns address 0's byte");
        assertEquals(0x22, ppu.read(PPUDATA), "second real read auto-increments to address 1");
    }

    @Test
    public void vramAddressReflectsTheLastAddressWritten(){
        writeAddress(0x1234);

        assertEquals(0x1234, ppu.vramAddress());
    }

    @Test
    public void writeOnlyRegistersReadAsZero(){
        assertEquals(0, ppu.read(PPUCTRL));
        assertEquals(0, ppu.read(PPUMASK));
        assertEquals(0, ppu.read(OAMADDR));
        assertEquals(0, ppu.read(PPUSCROLL));
        assertEquals(0, ppu.read(PPUADDR));
    }

    @Test
    public void chrReadsAndWritesPassThroughToTheCartridge(){
        writeAddress(0x0ABC);
        ppu.write(PPUDATA, 0x42);

        verifyChrByte(0x0ABC, 0x42);
    }

    @Test
    public void readBufferQuirkDelaysNonPaletteReadsByOneRead(){
        writeAddress(0x0100);
        ppu.write(PPUDATA, 0xAA);
        writeAddress(0x0101);
        ppu.write(PPUDATA, 0xBB);

        writeAddress(0x0100);
        final int primed = ppu.read(PPUDATA); //returns stale pre-existing buffer content, not 0xAA
        assertEquals(0, primed, "a fresh PPU's read buffer starts at 0, not the byte just addressed");
        assertEquals(0xAA, ppu.read(PPUDATA), "this read now returns the byte buffered by the previous read");
    }

    @Test
    public void paletteReadsAreImmediateWithNoBufferDelay(){
        ppu.write(PPUADDR, 0x3F);
        ppu.write(PPUADDR, 0x05);
        ppu.write(PPUDATA, 0x2C);

        writeAddress(0x3F05);
        assertEquals(0x2C, ppu.read(PPUDATA), "palette reads bypass the read-buffer delay entirely");
    }

    @Test
    public void paletteBackdropColourIsMirroredEveryFourEntries(){
        writeAddress(0x3F00);
        ppu.write(PPUDATA, 0x0F);

        writeAddress(0x3F10);
        assertEquals(0x0F, ppu.read(PPUDATA), "$3F10 mirrors $3F00's backdrop colour");

        writeAddress(0x3F14);
        ppu.write(PPUDATA, 0x21);
        writeAddress(0x3F04);
        assertEquals(0x21, ppu.read(PPUDATA), "the mirror also holds writing the other way round");
    }

    @Test
    public void paletteEntriesOtherThanTheBackdropAreNotMirrored(){
        writeAddress(0x3F01);
        ppu.write(PPUDATA, 0x11);
        writeAddress(0x3F11);
        ppu.write(PPUDATA, 0x22);

        writeAddress(0x3F01);
        assertEquals(0x11, ppu.read(PPUDATA), "$3F01 and $3F11 are independent, non-backdrop entries");
    }

    @Test
    public void horizontalMirroringAliasesTheTopTwoLogicalNametablesTogether(){
        mapper.mirroring = Mirroring.HORIZONTAL;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x55);

        writeAddress(0x2400); //horizontal mirroring: $2000 and $2400 are the same physical nametable
        ppu.read(PPUDATA); //prime
        assertEquals(0x55, ppu.read(PPUDATA), "$2400 should alias $2000 under horizontal mirroring");
    }

    @Test
    public void horizontalMirroringDoesNotAliasTopAndBottomNametables(){
        mapper.mirroring = Mirroring.HORIZONTAL;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x55);

        writeAddress(0x2800); //different physical nametable under horizontal mirroring
        ppu.read(PPUDATA); //prime
        assertEquals(0, ppu.read(PPUDATA), "$2800 must not alias $2000 under horizontal mirroring");
    }

    @Test
    public void verticalMirroringAliasesTheLeftTwoLogicalNametablesTogether(){
        mapper.mirroring = Mirroring.VERTICAL;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x66);

        writeAddress(0x2800); //vertical mirroring: $2000 and $2800 are the same physical nametable
        ppu.read(PPUDATA); //prime
        assertEquals(0x66, ppu.read(PPUDATA), "$2800 should alias $2000 under vertical mirroring");
    }

    @Test
    public void verticalMirroringDoesNotAliasLeftAndRightNametables(){
        mapper.mirroring = Mirroring.VERTICAL;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x66);

        writeAddress(0x2400); //different physical nametable under vertical mirroring
        ppu.read(PPUDATA); //prime
        assertEquals(0, ppu.read(PPUDATA), "$2400 must not alias $2000 under vertical mirroring");
    }

    @Test
    public void singleScreenLowerMirroringAliasesAllFourLogicalNametablesToTheFirstPhysicalTable(){
        mapper.mirroring = Mirroring.SINGLE_SCREEN_LOWER;

        writeAddress(0x2C00);
        ppu.write(PPUDATA, 0x77);

        writeAddress(0x2000);
        ppu.read(PPUDATA); //prime
        assertEquals(0x77, ppu.read(PPUDATA), "every logical nametable maps to the same physical table");
    }

    @Test
    public void singleScreenUpperMirroringUsesTheSecondPhysicalTableInsteadOfTheFirst(){
        mapper.mirroring = Mirroring.SINGLE_SCREEN_UPPER;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x88);

        mapper.mirroring = Mirroring.SINGLE_SCREEN_LOWER;
        writeAddress(0x2000);
        ppu.read(PPUDATA); //prime
        assertEquals(0, ppu.read(PPUDATA), "single-screen-upper's physical table must differ from lower's");
    }

    @Test
    public void nametableMirrorRegionAboveThreeThousandFoldsDownToTwoThousand(){
        mapper.mirroring = Mirroring.HORIZONTAL;

        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x99);

        writeAddress(0x3000); //$3000-$3EFF mirrors $2000-$2EFF
        ppu.read(PPUDATA); //prime
        assertEquals(0x99, ppu.read(PPUDATA), "$3000 should mirror $2000");
    }

    @Test
    public void ctrlWriteLatchesNametableSelectBitsIntoT(){
        ppu.write(PPUCTRL, 0x03); //NN = 11, and t started at 0 - so t must end up as exactly 0x03<<10

        assertEquals(0x03 << 10, ppu.temporaryVramAddress());
    }

    @Test
    public void ctrlWriteOnlyLatchesTheLowTwoNametableSelectBitsFromValue(){
        //every bit *except* NN (bits 0-1) set - a mutant that ORs the value in instead of masking it
        //to 2 bits first would smear these other bits into t as well, not just NN into bits 10-11
        ppu.write(PPUCTRL, 0xFC);

        assertEquals(0, ppu.temporaryVramAddress(), "NN=00 here, so t must be completely untouched");
    }

    @Test
    public void scrollFirstWriteClearsAnyPriorCoarseXBitsOfT(){
        //t starts at 0, so a mutant that ORs COARSE_X_CLEAR_MASK into t instead of ANDing it would
        //leave t non-zero even though this write's own coarse X (from value 0) is itself zero
        ppu.write(PPUSCROLL, 0x00);

        assertEquals(0, ppu.temporaryVramAddress());
    }

    @Test
    public void addressFirstWriteClearsTheUnusedFifteenthBitOfT(){
        ppu.write(PPUADDR, 0xFF); //top 2 bits of this byte must be discarded, not just the value written

        assertEquals(0x3F00, ppu.temporaryVramAddress() & 0x7F00, "only bits 8-13 may be set by the first write");
    }

    @Test
    public void paletteReadBoundaryIsExactlyAtThreeFHundred(){
        writeAddress(0x3EFF); //still nametable-mirrored space - buffered (delayed) read
        ppu.write(PPUDATA, 0x11);
        writeAddress(0x3F00); //first immediate (unbuffered) palette address
        ppu.write(PPUDATA, 0x22);

        writeAddress(0x3EFF);
        final int primed = ppu.read(PPUDATA); //buffered path: returns stale content, not 0x11 yet
        assertEquals(0, primed);
        writeAddress(0x3EFF); //the read above auto-incremented v past $3EFF - reset it before reading again
        assertEquals(0x11, ppu.read(PPUDATA), "$3EFF must still use the buffered (delayed) read path");

        writeAddress(0x3F00);
        assertEquals(0x22, ppu.read(PPUDATA), "$3F00 must use the immediate (unbuffered) read path");
    }

    @Test
    public void readMemoryRoutesExactlyThreeFHundredToPaletteNotNametable(){
        writeAddress(0x3F00);
        ppu.write(PPUDATA, 0x2A);

        writeAddress(0x3F00);
        assertEquals(0x2A, ppu.read(PPUDATA), "exactly $3F00 must be palette RAM, read immediately");
    }

    @Test
    public void paletteReadRefreshesTheBufferFromTheNametableByteUnderneathNotAbove(){
        //"underneath" $3F00 is $2F00 (address - $1000) - a mutant using address + $1000 instead would
        //instead read from an unrelated location, distinguishable via these two different sentinel values
        writeAddress(0x2F00);
        ppu.write(PPUDATA, 0x5A); //the correct "underneath" nametable byte
        writeAddress(0x3F00);
        ppu.write(PPUDATA, 0x99); //palette value itself - must differ from the sentinel above

        writeAddress(0x3F00);
        ppu.read(PPUDATA); //immediate palette read; its side effect (buffer refresh) is what's under test

        writeAddress(0x0000); //any non-palette address, just to surface the current buffer via a plain read
        assertEquals(0x5A, ppu.read(PPUDATA), "the read buffer must be refreshed from $2F00, not $3F00 + $1000");
    }

    @Test
    public void writeOamDmaCopiesTheWholePageStartingAtTheCurrentOamAddressWrappingModTwoFiftySix(){
        ppu.write(OAMADDR, 0xFE); //non-zero, near the wrap boundary

        final int[] page = new int[0x100];
        for (int i = 0; i < page.length; i++){
            page[i] = i;
        }
        ppu.writeOamDma(page);

        ppu.write(OAMADDR, 0xFE);
        assertEquals(0, ppu.read(OAMDATA), "byte 0 of the page should land at the pre-DMA OAM address");
        ppu.write(OAMADDR, 0xFF);
        assertEquals(1, ppu.read(OAMDATA));
        ppu.write(OAMADDR, 0x00); //wrapped around
        assertEquals(2, ppu.read(OAMDATA), "OAM address should wrap mod 256 partway through the page");
    }

    @Test
    public void consumeOamDmaStallCyclesIsOneShotFiringOnlyAfterADma(){
        assertEquals(0, ppu.consumeOamDmaStallCycles(), "no DMA has happened yet");

        ppu.writeOamDma(new int[0x100]);

        assertEquals(514, ppu.consumeOamDmaStallCycles());
        assertEquals(0, ppu.consumeOamDmaStallCycles(), "the same DMA must not be reported twice");
    }

    @Test
    public void vblankFlagReflectsTheFlagWithoutTheReadAndClearSideEffect(){
        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.vblankFlag(), "first peek should observe it set");
        assertTrue(ppu.vblankFlag(), "unlike read($2002), peeking must not clear it - still set on a second peek");
        assertEquals(VBLANK_BIT, ppu.read(PPUSTATUS) & VBLANK_BIT, "the real register read must still see it too");
    }

    @Test
    public void vblankFlagStartsFalse(){
        assertFalse(ppu.vblankFlag());
    }

    @Test
    public void oamAddressReflectsTheLastAddressWritten(){
        ppu.write(OAMADDR, 0x42);

        assertEquals(0x42, ppu.oamAddress());
    }

    @Test
    public void nametableSnapshotReflectsWrittenData(){
        writeAddress(0x2000);
        ppu.write(PPUDATA, 0x37);

        assertEquals(0x37, ppu.nametableSnapshot()[0]);
    }

    @Test
    public void nametableSnapshotIsADefensiveCopy(){
        final int[] snapshot = ppu.nametableSnapshot();
        snapshot[0] = 0xFF;

        assertEquals(0, ppu.nametableSnapshot()[0], "mutating a returned snapshot must not affect the PPU's own state");
    }

    @Test
    public void oamSnapshotReflectsWrittenData(){
        ppu.write(OAMADDR, 0x00);
        ppu.write(OAMDATA, 0x64);

        assertEquals(0x64, ppu.oamSnapshot()[0]);
    }

    @Test
    public void oamSnapshotIsADefensiveCopy(){
        final int[] snapshot = ppu.oamSnapshot();
        snapshot[0] = 0xFF;

        assertEquals(0, ppu.oamSnapshot()[0], "mutating a returned snapshot must not affect the PPU's own state");
    }

    @Test
    public void frameReadyFiresAtVblankStart(){
        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeFrameReady());
    }

    @Test
    public void frameReadyDoesNotFireBeforeVblankStart(){
        tick(TICKS_UNTIL_VBLANK_START - 1);

        assertFalse(ppu.consumeFrameReady());
    }

    @Test
    public void consumeFrameReadyIsOneShot(){
        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeFrameReady());
        assertFalse(ppu.consumeFrameReady(), "the same frame must not be reported ready twice");
    }

    @Test
    public void frameReadyFiresAgainEveryFrame(){
        tick(TICKS_UNTIL_VBLANK_START);
        assertTrue(ppu.consumeFrameReady());

        //ceiling division, same reasoning as TICKS_UNTIL_VBLANK_START/END: 341*262 dots isn't a multiple
        //of 3, so round up to guarantee the tick whose 3-dot span reaches (or passes) the next vblank start
        final int dotsPerFrame = SCANLINES_PER_FRAME * DOTS_PER_SCANLINE;
        tick((dotsPerFrame + DOTS_PER_CPU_CYCLE - 1) / DOTS_PER_CPU_CYCLE);

        assertTrue(ppu.consumeFrameReady(), "the next frame's vblank start should fire a fresh frame-ready signal");
    }

    @Test
    public void framebufferStartsAllZero(){
        for (final int paletteIndex : ppu.framebuffer()){
            assertEquals(0, paletteIndex);
        }
    }

    @Test
    public void framebufferIsTheDocumentedSize(){
        assertEquals(PPU.FRAMEBUFFER_WIDTH * PPU.FRAMEBUFFER_HEIGHT, ppu.framebuffer().length);
    }

    @Test
    public void framebufferIsADefensiveCopy(){
        final int[] snapshot = ppu.framebuffer();
        snapshot[0] = 0xFF;

        assertEquals(0, ppu.framebuffer()[0], "mutating a returned snapshot must not affect the PPU's own state");
    }

    // --- Background rendering (Phase 3) ---

    @Test
    public void backgroundPixelsDecodeTileDataAtFineXZero(){
        writeChrTilePixelRow0(0, 0xF0, 0xCC); //pixel values, left to right: 3,3,1,1,2,2,0,0
        writeNametableTile(0, 0, 0);
        writeBackgroundPaletteEntry(0, 0, 0x3F); //backdrop (pixel value 0)
        writeBackgroundPaletteEntry(0, 1, 0x11);
        writeBackgroundPaletteEntry(0, 2, 0x22);
        writeBackgroundPaletteEntry(0, 3, 0x33);
        writeAddress(0); //reset v/t: the CHR/palette writes above left currentVramAddress pointing elsewhere
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        final int[] fb = ppu.framebuffer();
        assertEquals(0x33, fb[0]);
        assertEquals(0x33, fb[1]);
        assertEquals(0x11, fb[2]);
        assertEquals(0x11, fb[3]);
        assertEquals(0x22, fb[4]);
        assertEquals(0x22, fb[5]);
        assertEquals(0x3F, fb[6], "pixel value 0 falls back to the backdrop colour");
        assertEquals(0x3F, fb[7]);
    }

    @Test
    public void tileBoundaryAtColumnEightShowsTheNextTile(){
        writeChrTilePixelRow0(0, 0x00, 0x00); //tile 0: pixel value 0 (backdrop) everywhere
        writeChrTilePixelRow0(1, 0xFF, 0x00); //tile 1: pixel value 1 everywhere
        writeNametableTile(0, 0, 0);
        writeNametableTile(1, 0, 1);
        writeBackgroundPaletteEntry(0, 0, 0x05);
        writeBackgroundPaletteEntry(0, 1, 0x15);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        final int[] fb = ppu.framebuffer();
        assertEquals(0x05, fb[7], "still tile 0's backdrop pixel");
        assertEquals(0x15, fb[8], "tile 1's first pixel, not tile 0's or a stale fetch");
    }

    @Test
    public void fetchPipelineFetchesTwoTilesAheadNotTheWrongTile(){
        writeChrTilePixelRow0(0, 0xFF, 0x00); //pixel value 1
        writeChrTilePixelRow0(1, 0x00, 0xFF); //pixel value 2
        writeChrTilePixelRow0(2, 0xFF, 0xFF); //pixel value 3
        writeNametableTile(0, 0, 0);
        writeNametableTile(1, 0, 1);
        writeNametableTile(2, 0, 2);
        writeBackgroundPaletteEntry(0, 1, 0x01);
        writeBackgroundPaletteEntry(0, 2, 0x02);
        writeBackgroundPaletteEntry(0, 3, 0x03);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        final int[] fb = ppu.framebuffer();
        for (int x = 0; x < 8; x++){
            assertEquals(0x01, fb[x], "column 0-7 must show tile 0's own colour, not a tile being pre-fetched");
        }
        for (int x = 8; x < 16; x++){
            assertEquals(0x02, fb[x]);
        }
        for (int x = 16; x < 24; x++){
            assertEquals(0x03, fb[x]);
        }
    }

    @Test
    public void attributeQuadrantsWithinOneAttributeCellSelectDifferentPaletteGroups(){
        writeChrTilePixelRow0(0, 0xFF, 0x00); //pixel value 1 everywhere
        for (int row = 0; row < 4; row++){
            for (int col = 0; col < 4; col++){
                writeNametableTile(col, row, 0);
            }
        }
        writeAttributeByte(0, 0, 0xE4); //TL=group0, TR=group1, BL=group2, BR=group3 (see javadoc derivation)
        writeBackgroundPaletteEntry(0, 1, 0xA);
        writeBackgroundPaletteEntry(1, 1, 0xB);
        writeBackgroundPaletteEntry(2, 1, 0xC);
        writeBackgroundPaletteEntry(3, 1, 0xD);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 16);

        final int[] fb = ppu.framebuffer();
        for (int x = 0; x < 8; x++){
            assertEquals(0xA, fb[x], "top-left quadrant (all 8 pixels of its tile) must use group 0");
        }
        assertEquals(0xB, fb[16], "top-right quadrant (x=16,y=0) uses group 1");
        assertEquals(0xC, fb[16 * PPU.FRAMEBUFFER_WIDTH], "bottom-left quadrant (x=0,y=16) uses group 2");
        assertEquals(0xD, fb[16 * PPU.FRAMEBUFFER_WIDTH + 16], "bottom-right quadrant (x=16,y=16) uses group 3");
    }

    @Test
    public void universalBackgroundColourOverridesANonZeroAttributeGroup(){
        writeChrTilePixelRow0(0, 0x00, 0x00); //pixel value 0 (transparent) everywhere
        writeNametableTile(0, 0, 0);
        writeAttributeByte(0, 0, 0x55); //group1 for every quadrant
        writeBackgroundPaletteEntry(0, 0, 0x09); //the real backdrop, $3F00
        writeBackgroundPaletteEntry(1, 0, 0x15); //decoy at $3F04 - must never be selected
        writeBackgroundPaletteEntry(2, 0, 0x16); //decoy at $3F08
        writeBackgroundPaletteEntry(3, 0, 0x17); //decoy at $3F0C
        writeAddress(0); //reset v/t: the palette writes above left currentVramAddress pointing elsewhere
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        assertEquals(0x09, ppu.framebuffer()[0], "a transparent pixel always shows $3F00, regardless of the active palette group");
    }

    @Test
    public void disablingBackgroundRenderingShowsTheBackdropEverywhere(){
        writeChrTilePixelRow0(0, 0xFF, 0xFF); //pixel value 3 - would be clearly visible if shown
        writeNametableTile(0, 0, 0);
        writeBackgroundPaletteEntry(0, 0, 0x07);
        writeBackgroundPaletteEntry(0, 3, 0x2A);
        ppu.write(PPUMASK, 0); //background rendering left off

        tickThroughScanline(1, 0);

        final int[] fb = ppu.framebuffer();
        for (int x = 0; x < PPU.FRAMEBUFFER_WIDTH; x++){
            assertEquals(0x07, fb[x]);
        }
    }

    @Test
    public void leftEightPixelClipHidesTheBackgroundOnlyInThatRegion(){
        writeChrTilePixelRow0(0, 0xFF, 0xFF); //tile 0: pixel value 3
        writeChrTilePixelRow0(1, 0xFF, 0xFF); //tile 1: pixel value 3
        writeNametableTile(0, 0, 0);
        writeNametableTile(1, 0, 1);
        writeBackgroundPaletteEntry(0, 0, 0x01);
        writeBackgroundPaletteEntry(0, 3, 0x2B);
        writeAddress(0);
        ppu.write(PPUMASK, SHOW_BACKGROUND); //enabled, but left-8px clip bit left off

        tickThroughScanline(1, 0);

        final int[] fb = ppu.framebuffer();
        assertEquals(0x01, fb[7], "still clipped to the backdrop");
        assertEquals(0x2B, fb[8], "past the clip region, the real tile shows");
    }

    @Test
    public void fineXScrollShiftsTheTileBoundaryEarlierAcrossTiles(){
        writeChrTilePixelRow0(0, 0b10000000, 0x00); //tile 0: only pixel 0 is value 1, rest value 0
        writeChrTilePixelRow0(1, 0x00, 0xFF); //tile 1: pixel value 2 everywhere
        writeNametableTile(0, 0, 0);
        writeNametableTile(1, 0, 1);
        writeBackgroundPaletteEntry(0, 2, 0x2C);
        writeAddress(0); //reset v/t before staging the scroll below
        ppu.write(PPUSCROLL, 1); //fineX=1, coarseX=0
        ppu.write(PPUSCROLL, 0);
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        assertEquals(0x2C, ppu.framebuffer()[7],
                "fineX=1 shifts the tile-0/tile-1 boundary one pixel earlier, so column 7 already shows tile 1");
    }

    @Test
    public void coarseXScrollFromDollarTwoZeroZeroFiveSelectsADifferentStartingTile(){
        writeChrTilePixelRow0(5, 0xFF, 0x00); //tile A: pixel value 1
        writeChrTilePixelRow0(6, 0x00, 0xFF); //tile B: pixel value 2
        writeNametableTile(0, 0, 5);
        writeNametableTile(1, 0, 6);
        writeBackgroundPaletteEntry(0, 2, 0x0B);
        writeAddress(0); //reset v/t before staging the scroll below - v itself only picks it up via the pre-render hori-copy
        ppu.write(PPUSCROLL, 8); //coarseX=1: the viewport's left edge starts at nametable column 1
        ppu.write(PPUSCROLL, 0);
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        assertEquals(0x0B, ppu.framebuffer()[0], "screen column 0 should show tile B (nametable column 1), not tile A");
    }

    @Test
    public void coarseXIncrementWrapsAtThirtyOneAndTogglesTheNametableXBit(){
        writeAddress(31); //v = t = coarseX 31, everything else 0
        enableBackgroundRendering();

        tickTo(0, 0, 9); //past dot 8's coarse-X increment

        assertEquals(0, ppu.vramAddress() & 0x1F, "coarse X wraps to 0");
        assertTrue((ppu.vramAddress() & 0x0400) != 0, "the nametable-X-select bit toggles");
    }

    @Test
    public void fineYIncrementBelowSevenLeavesCoarseYUntouched(){
        writeAddress(10 * 32); //coarseY=10, fineY=0
        enableBackgroundRendering();

        tickThroughScanline(0, 0); //one dot-256 crossing

        assertEquals(1, (ppu.vramAddress() >> 12) & 0x07, "fine Y incremented");
        assertEquals(10, (ppu.vramAddress() >> 5) & 0x1F, "coarse Y untouched");
    }

    @Test
    public void fineYWrapAtCoarseYTwentyNineTogglesTheNametableYBit(){
        writeAddress(29 * 32); //coarseY=29, fineY=0
        enableBackgroundRendering();

        tickThroughScanline(0, 7); //8 dot-256 crossings: fineY 0->7, then the 8th wraps coarseY

        assertEquals(0, (ppu.vramAddress() >> 5) & 0x1F, "coarse Y wraps to 0");
        assertEquals(0, (ppu.vramAddress() >> 12) & 0x07, "fine Y resets to 0");
        assertTrue((ppu.vramAddress() & 0x0800) != 0, "row 29 is the real last row of tiles: the nametable-Y bit toggles");
    }

    @Test
    public void fineYWrapAtCoarseYThirtyOneDoesNotToggleTheNametableYBit(){
        writeAddress(31 * 32); //coarseY=31 (an out-of-bounds value a game deliberately set), fineY=0
        enableBackgroundRendering();

        tickThroughScanline(0, 7); //8 dot-256 crossings

        assertEquals(0, (ppu.vramAddress() >> 5) & 0x1F, "coarse Y wraps to 0");
        assertEquals(0, ppu.vramAddress() & 0x0800, "row 31 was never really on the next nametable, so no toggle");
    }

    @Test
    public void fineYWrapAtAMidRangeCoarseYJustIncrementsCoarseY(){
        //fine Y's top bit (worth 4) is unreachable via a direct $2006 write (real hardware always clears
        //it there too) - so to reach fineY=7, let it accumulate through 7 real dot-256 increments from a
        //reachable fineY=0 starting point, exactly like the coarseY 29/31 wrap tests below; the 8th
        //crossing (scanline 7) is the one that actually wraps
        writeAddress(10 * 32); //coarseY=10, fineY=0
        enableBackgroundRendering();

        tickThroughScanline(0, 7);

        assertEquals(11, (ppu.vramAddress() >> 5) & 0x1F, "a non-special coarse Y just increments");
        assertEquals(0, (ppu.vramAddress() >> 12) & 0x07, "fine Y resets to 0");
        assertEquals(0, ppu.vramAddress() & 0x0800, "not one of the special wrap rows, so no nametable toggle");
    }

    @Test
    public void horizontalCopyAtDotTwoFiveSevenCopiesOnlyCoarseXAndTheNametableXBit(){
        writeAddress(0x31E0); //v = t: coarseX=0, coarseY=15, fineY=3, nametable select=0
        ppu.write(PPUCTRL, 1); //t: nametable-X bit set
        ppu.write(PPUSCROLL, 40); //t: coarseX=5 (fineX=0) - v itself is untouched by $2005/$2000
        final int tAfterSetup = ppu.temporaryVramAddress();
        enableBackgroundRendering();

        tickTo(0, 0, 258); //just past dot 257's hori(v)=hori(t) - dot 256's own fine-Y increment also
        //legitimately fires in this window (see the dedicated fine-Y tests above), so this deliberately
        //doesn't assert fine Y stays put - only the fields hori-copy itself is documented to touch

        assertEquals(tAfterSetup & HORIZONTAL_SCROLL_MASK, ppu.vramAddress() & HORIZONTAL_SCROLL_MASK,
                "coarse X and the nametable-X bit are copied from t");
        assertEquals(15, (ppu.vramAddress() >> 5) & 0x1F, "coarse Y untouched by hori-copy");
    }

    @Test
    public void verticalCopyDoesNotHappenOnAnOrdinaryVisibleScanline(){
        writeAddress(0x31E0); //coarseY=15, nametable-Y bit=0
        ppu.write(PPUCTRL, 2); //t: nametable-Y bit set - if vert-copy wrongly fired here, v's would flip too
        ppu.write(PPUSCROLL, 0);
        ppu.write(PPUSCROLL, 163); //t: coarseY=20 (deliberately different from v's 15), fineY=3
        enableBackgroundRendering();

        tickThroughScanline(0, 0); //scanline 0's own dot 257 fires hori-copy, but never vert-copy

        //coarseY/nametable-Y are immune to a single non-wrapping dot-256 crossing (only fine Y visibly
        //moves there - see the fine-Y tests above), so unlike fine Y, these two staying at v's ORIGINAL
        //values (not jumping to t's deliberately-different ones) is a genuine proof that no
        //vert(v)=vert(t) copy happened
        assertEquals(15, (ppu.vramAddress() >> 5) & 0x1F, "coarse Y unchanged");
        assertEquals(0, ppu.vramAddress() & 0x0800, "nametable-Y bit unchanged");
    }

    @Test
    public void verticalCopyDuringPreRenderCopiesFineYCoarseYAndTheNametableYBit(){
        writeAddress(0x31E0);
        ppu.write(PPUCTRL, 2);
        ppu.write(PPUSCROLL, 0);
        ppu.write(PPUSCROLL, 123); //t: coarseY=15, fineY=3
        final int tAfterSetup = ppu.temporaryVramAddress();
        enableBackgroundRendering();

        tickTo(0, VBLANK_END_SCANLINE, 305); //just past pre-render's dot-280-304 vert(v)=vert(t) window

        //the copy unconditionally overwrites the whole vertical field from t, so it doesn't matter that
        //240 scanlines' worth of natural fine-Y/coarse-Y churn happened to v in between
        assertEquals(tAfterSetup & VERTICAL_SCROLL_MASK, ppu.vramAddress() & VERTICAL_SCROLL_MASK);
    }

    @Test
    public void renderingIsEnabledByEitherShowBackgroundOrShowSpritesNotJustBackground(){
        //real, non-blank tile data - not just relying on CHR/nametable defaults - so a bug that made
        //background pixels show up despite bit 3 being off would actually be visible in the assertion
        //below, rather than trivially matching an all-zero framebuffer either way
        writeChrTilePixelRow0(0, 0xFF, 0xFF); //pixel value 3
        writeNametableTile(0, 0, 0);
        writeBackgroundPaletteEntry(0, 3, 0x2E);
        writeAddress(0);
        ppu.write(PPUMASK, SHOW_SPRITES); //background itself is off, but "rendering" is still enabled

        tickThroughScanline(1, 0);

        assertEquals(0, ppu.framebuffer()[0], "background pixels stay backdrop since bit 3 is off, even though the shift registers hold real tile data");
    }

    @Test
    public void renderingDisabledEntirelyFreezesTheVRegister(){
        writeAddress(31); //coarseX=31, ready to wrap on the very first fetch group if the machinery ran
        ppu.write(PPUMASK, 0); //neither show-background nor show-sprites

        tickTo(0, 0, 9); //past where the coarse-X increment would fire if rendering were enabled

        assertEquals(31, ppu.vramAddress() & 0x1F, "coarse X must not move - the whole v-register pipeline is frozen");
    }

    @Test
    public void lastVisibleScanlineTwoThirtyNineStillRenders(){
        writeChrTileUniform(0, 0xFF, 0xFF); //pixel value 3 - tile 0 covers every nametable cell by default (0)
        writeBackgroundPaletteEntry(0, 3, 0x2F);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 239);

        assertEquals(0x2F, ppu.framebuffer()[239 * PPU.FRAMEBUFFER_WIDTH], "scanline 239 is still a visible scanline, not off-by-one excluded");
    }

    @Test
    public void patternByteAddressUsesTheCorrectFineYRowNotAnAdjacentOne(){
        //row 0 and row 1 deliberately different - a fine-Y address miscomputation (e.g. the wrong shift
        //direction) would fetch the wrong row's bytes instead of just returning coincidentally-matching data
        writeAddress(0);
        ppu.write(PPUDATA, 0xFF); //tile 0 row 0 low plane: pixel value 1
        writeAddress(8);
        ppu.write(PPUDATA, 0x00); //row 0 high plane
        writeAddress(1);
        ppu.write(PPUDATA, 0x00); //tile 0 row 1 low plane
        writeAddress(9);
        ppu.write(PPUDATA, 0xFF); //row 1 high plane: pixel value 2
        writeBackgroundPaletteEntry(0, 1, 0x31);
        writeBackgroundPaletteEntry(0, 2, 0x32);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 1);

        assertEquals(0x31, ppu.framebuffer()[0], "row 0's own pixel value");
        assertEquals(0x32, ppu.framebuffer()[PPU.FRAMEBUFFER_WIDTH], "row 1 must use row 1's CHR bytes, not row 0's");
    }

    @Test
    public void attributeAddressUsesTheCorrectCellNotAnAdjacentOne(){
        //two different 32x32px attribute cells (0,0) and (1,0), each with a tile referencing the same
        //pattern but a different palette group - an attribute-address miscomputation (e.g. the wrong
        //shift direction on the coarse X/Y terms) would read the wrong cell's byte
        writeChrTilePixelRow0(0, 0xFF, 0x00); //pixel value 1
        writeNametableTile(0, 0, 0); //cell (0,0): tile columns 0-3
        writeNametableTile(4, 0, 0); //cell (1,0): tile columns 4-7
        writeAttributeByte(0, 0, 0x00); //cell (0,0): group 0 everywhere
        writeAttributeByte(1, 0, 0xFF); //cell (1,0): group 3 everywhere
        writeBackgroundPaletteEntry(0, 1, 0x21);
        writeBackgroundPaletteEntry(3, 1, 0x23);
        writeAddress(0);
        enableBackgroundRendering();

        tickThroughScanline(1, 0);

        assertEquals(0x21, ppu.framebuffer()[0], "cell (0,0)'s own group");
        assertEquals(0x23, ppu.framebuffer()[32], "cell (1,0) (tile column 4, x=32) must use its own attribute byte, not cell (0,0)'s");
    }

    @Test
    public void frameReadyAndFramebufferAreBothCorrectWithRenderingActive(){
        //every one of tile 0's 8 rows (not just fine-Y 0): row 5's framebuffer pixel below is fed by
        //fine Y 5's own CHR row, not row 0's
        writeChrTileUniform(0, 0xFF, 0xFF);
        writeNametableTile(0, 0, 0);
        writeBackgroundPaletteEntry(0, 3, 0x2D);
        writeAddress(0);
        enableBackgroundRendering();

        tick(TICKS_UNTIL_VBLANK_START);

        assertTrue(ppu.consumeFrameReady());
        //row 5, not row 0: the very first frame's row 0 has no preceding pre-render prefetch to seed
        //it (a real, harmless startup artifact - see the cross-scanline continuity reasoning elsewhere
        //in this file), so pick a row that's guaranteed to already be correctly seeded by this point
        assertEquals(0x2D, ppu.framebuffer()[5 * PPU.FRAMEBUFFER_WIDTH]);
    }

    private void verifyChrByte(final int address, final int expectedValue){
        writeAddress(address);
        ppu.read(PPUDATA); //prime the read buffer
        assertEquals(expectedValue, ppu.read(PPUDATA));
    }

    private void writeAddress(final int address){
        ppu.write(PPUADDR, (address >> 8) & 0xFF);
        ppu.write(PPUADDR, address & 0xFF);
    }

    /** Writes one 8x8 tile's fine-Y-0 row (both bitplane bytes) via real $2006/$2007 writes - the only row these tests need. */
    private void writeChrTilePixelRow0(final int tileId, final int lowPlaneByte, final int highPlaneByte){
        final int tileBase = tileId * 16;
        writeAddress(tileBase);
        ppu.write(PPUDATA, lowPlaneByte);
        writeAddress(tileBase + 8);
        ppu.write(PPUDATA, highPlaneByte);
    }

    /** Writes the same pixel row to all 8 fine-Y rows of a tile - for tests that check a scanline whose fine Y isn't 0. */
    private void writeChrTileUniform(final int tileId, final int lowPlaneByte, final int highPlaneByte){
        final int tileBase = tileId * 16;
        for (int fineY = 0; fineY < 8; fineY++){
            writeAddress(tileBase + fineY);
            ppu.write(PPUDATA, lowPlaneByte);
            writeAddress(tileBase + 8 + fineY);
            ppu.write(PPUDATA, highPlaneByte);
        }
    }

    /** Writes a tile ID into logical nametable 0 at (col, row). */
    private void writeNametableTile(final int col, final int row, final int tileId){
        writeAddress(0x2000 + row * 32 + col);
        ppu.write(PPUDATA, tileId);
    }

    /** Writes an attribute byte for logical nametable 0's (attrCol, attrRow) 32x32px cell (0-7 each). */
    private void writeAttributeByte(final int attrCol, final int attrRow, final int value){
        writeAddress(0x23C0 + attrRow * 8 + attrCol);
        ppu.write(PPUDATA, value);
    }

    /** Writes one background palette entry: group 0-3, entry 0-3 (entry 0 of every group aliases $3F00, see the universal-backdrop tests). */
    private void writeBackgroundPaletteEntry(final int group, final int entry, final int colorIndex){
        writeAddress(0x3F00 + group * 4 + entry);
        ppu.write(PPUDATA, colorIndex);
    }

    private void enableBackgroundRendering(){
        ppu.write(PPUMASK, SHOW_BACKGROUND | SHOW_BACKGROUND_LEFT);
    }

    /**
     * Ticks from a fresh (dot 0, scanline 0) start to at-or-just-past the given absolute frame/
     * scanline/dot - safe to call more than once per test, each call only ticking the remaining delta.
     * Landing a dot or two past the exact target (ceiling division, same reasoning as
     * {@code TICKS_UNTIL_VBLANK_START}) is fine everywhere this is used: every call site targets "has
     * this dot's effect already happened", never an exact mid-scanline instant - the PPU's own dot
     * granularity is only ever observable in multiples of {@link com.rox.ppu.PPU.FrameTiming#DOTS_PER_CPU_CYCLE}
     * anyway, exactly like a real CPU polling it.
     */
    private void tickTo(final int frame, final int scanline, final int dot){
        final long targetDot = (long) SCANLINES_PER_FRAME * DOTS_PER_SCANLINE * frame
                + (long) scanline * DOTS_PER_SCANLINE + dot;
        final long targetTicks = (targetDot + DOTS_PER_CPU_CYCLE - 1) / DOTS_PER_CPU_CYCLE;
        final long delta = targetTicks - ticksIssued;
        if (delta > 0){
            tick((int) delta);
            ticksIssued = targetTicks;
        }
    }

    /** Ticks past the end of the given scanline (dot 341), guaranteeing every one of its pixels (dots 1-256) was drawn. */
    private void tickThroughScanline(final int frame, final int scanline){
        tickTo(frame, scanline + 1, 0);
    }
}
