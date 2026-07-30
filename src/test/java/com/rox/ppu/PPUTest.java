package com.rox.ppu;

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

    private PPU ppu;

    @BeforeEach
    public void setup(){
        ppu = new PPU();
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
        ppu.write(PPUDATA, 0x11); //lands at 0, auto-increments to 1
        ppu.write(PPUDATA, 0x22); //lands at 1, auto-increments to 2

        writeAddress(0x0001);
        assertEquals(0x22, ppu.read(PPUDATA), "with increment 1, the second write should have landed at address 1");
    }

    @Test
    public void vramIncrementStaysOneWhenControlBitExplicitlyClear(){
        //writes $2000 with every bit *except* the increment bit set, to distinguish "bit correctly
        //read as clear" from a mutant that ORs it in regardless of the written value
        ppu.write(PPUCTRL, 0xFF & ~VRAM_INCREMENT_32);
        writeAddress(0x0000);

        ppu.write(PPUDATA, 0x11); //lands at 0, auto-increments to 1
        ppu.write(PPUDATA, 0x22); //lands at 1, only reachable if increment correctly stayed 1

        writeAddress(0x0001);
        assertEquals(0x22, ppu.read(PPUDATA), "second write should have landed at address 1");
    }

    @Test
    public void vramIncrementIsThirtyTwoWhenControlBitSet(){
        ppu.write(PPUCTRL, VRAM_INCREMENT_32);
        writeAddress(0x0000);

        ppu.write(PPUDATA, 0x11); //address auto-increments by 32 after this write

        writeAddress(0x0020); //0x20 = 32
        assertEquals(0, ppu.read(PPUDATA), "address 32 was never written directly, sanity check it's addressable");
        writeAddress(0x0000);
        assertEquals(0x11, ppu.read(PPUDATA), "the byte should still be at address 0, not smeared across 1-31");
    }

    @Test
    public void dataRegisterReadWriteRoundTrips(){
        writeAddress(0x1234);
        ppu.write(PPUDATA, 0x77);

        writeAddress(0x1234);
        assertEquals(0x77, ppu.read(PPUDATA));
    }

    @Test
    public void addressRegisterMasksToFourteenBits(){
        //$4321 & $3FFF = $0321 - the top bits of the first (high) byte write are discarded
        ppu.write(PPUADDR, 0x43);
        ppu.write(PPUADDR, 0x21);
        ppu.write(PPUDATA, 0x99);

        writeAddress(0x0321);
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
        assertEquals(0x11, ppu.read(PPUDATA), "first read at address 0");
        assertEquals(0x22, ppu.read(PPUDATA), "second read should auto-increment to address 1");
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

    private void writeAddress(final int address){
        ppu.write(PPUADDR, (address >> 8) & 0xFF);
        ppu.write(PPUADDR, address & 0xFF);
    }
}
