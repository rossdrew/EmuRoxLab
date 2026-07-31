package com.rox.ppu;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.INesRom;
import com.rox.cartridge.Mapper;
import com.rox.cartridge.Mirroring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rox.ppu.PPU.DOTS_PER_CPU_CYCLE;
import static com.rox.ppu.PPU.DOTS_PER_SCANLINE;
import static com.rox.ppu.PPU.SCANLINES_PER_FRAME;
import static com.rox.ppu.PPU.TICKS_UNTIL_VBLANK_END;
import static com.rox.ppu.PPU.TICKS_UNTIL_VBLANK_START;
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

    private void verifyChrByte(final int address, final int expectedValue){
        writeAddress(address);
        ppu.read(PPUDATA); //prime the read buffer
        assertEquals(expectedValue, ppu.read(PPUDATA));
    }

    private void writeAddress(final int address){
        ppu.write(PPUADDR, (address >> 8) & 0xFF);
        ppu.write(PPUADDR, address & 0xFF);
    }
}
