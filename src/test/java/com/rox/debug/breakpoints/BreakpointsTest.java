package com.rox.debug.breakpoints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BreakpointsTest {
    private final Breakpoints breakpoints = new Breakpoints();

    @Test
    void nothingSetNeverHits(){
        breakpoints.recordAccess(0x0042, true, 1);

        assertEquals(Optional.empty(), breakpoints.check(true, 0xC000));
    }

    @Test
    void aBreakpointHitsWhenThePcReachesItBetweenInstructions(){
        breakpoints.addBreakpoint(0xC123);

        assertEquals(Optional.of(new BreakHit(BreakHit.Cause.BREAKPOINT, 0xC123, 0, 0xC123)), breakpoints.check(true, 0xC123));
    }

    @Test
    void aBreakpointDoesNotHitMidInstructionOrElsewhere(){
        breakpoints.addBreakpoint(0xC123);

        assertEquals(Optional.empty(), breakpoints.check(false, 0xC123));
        assertEquals(Optional.empty(), breakpoints.check(true, 0xC124));
    }

    @Test
    void aRemovedBreakpointNoLongerHits(){
        breakpoints.addBreakpoint(0xC123);

        breakpoints.removeBreakpoint(0xC123);

        assertEquals(Optional.empty(), breakpoints.check(true, 0xC123));
        assertTrue(breakpoints.breakpoints().isEmpty());
    }

    @Test
    void aWatchHitIsHeldUntilItsInstructionFinishesAndNamesThatInstruction(){
        breakpoints.addWatch(0x0042, WatchKind.WRITE);
        breakpoints.check(true, 0xC0F1); //STA $42 starts here

        breakpoints.recordAccess(0x0042, true, 0x05);

        assertEquals(Optional.empty(), breakpoints.check(false, 0xC0F3), "still mid-instruction");
        assertEquals(Optional.of(new BreakHit(BreakHit.Cause.WRITE, 0x0042, 0x05, 0xC0F1)), breakpoints.check(true, 0xC0F3));
        assertEquals(Optional.empty(), breakpoints.check(true, 0xC0F3), "reported once");
    }

    @Test
    void aWatchHitBeatsABreakpointOnTheNextInstruction(){
        breakpoints.addWatch(0x0042, WatchKind.READ);
        breakpoints.addBreakpoint(0xC0F3);
        breakpoints.check(true, 0xC0F1);

        breakpoints.recordAccess(0x0042, false, 0x07);

        assertEquals(BreakHit.Cause.READ, breakpoints.check(true, 0xC0F3).orElseThrow().cause());
        assertEquals(BreakHit.Cause.BREAKPOINT, breakpoints.check(true, 0xC0F3).orElseThrow().cause());
    }

    @Test
    void theFirstWatchHitInAnInstructionIsTheOneReported(){
        breakpoints.addWatch(0x0042, WatchKind.ACCESS);
        breakpoints.check(true, 0xC000);

        breakpoints.recordAccess(0x0042, false, 1); //INC $42: read...
        breakpoints.recordAccess(0x0042, true, 2);  //...then write

        assertEquals(new BreakHit(BreakHit.Cause.READ, 0x0042, 1, 0xC000), breakpoints.check(true, 0xC002).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({
            "READ, false, true",
            "READ, true, false",
            "WRITE, false, false",
            "WRITE, true, true",
            "ACCESS, false, true",
            "ACCESS, true, true"
    })
    void aWatchCatchesOnlyItsKindOfAccess(final WatchKind kind, final boolean write, final boolean caught){
        breakpoints.addWatch(0x0042, kind);

        breakpoints.recordAccess(0x0042, write, 0);

        assertEquals(caught, breakpoints.check(true, 0xC000).isPresent());
    }

    @Test
    void anAccessElsewhereIsNotCaught(){
        breakpoints.addWatch(0x0042, WatchKind.ACCESS);

        breakpoints.recordAccess(0x0043, true, 0);

        assertEquals(Optional.empty(), breakpoints.check(true, 0xC000));
    }

    @Test
    void addingAWatchAgainChangesItsKind(){
        breakpoints.addWatch(0x0042, WatchKind.READ);

        breakpoints.addWatch(0x0042, WatchKind.WRITE);

        assertEquals(Map.of(0x0042, WatchKind.WRITE), breakpoints.watches());
    }

    @Test
    void aRemovedWatchNoLongerCatches(){
        breakpoints.addWatch(0x0042, WatchKind.ACCESS);
        breakpoints.addBreakpoint(0xC000);

        breakpoints.removeWatch(0x0042);
        breakpoints.recordAccess(0x0042, true, 0);

        assertEquals(BreakHit.Cause.BREAKPOINT, breakpoints.check(true, 0xC000).orElseThrow().cause());
        assertTrue(breakpoints.watches().isEmpty());
    }

    @Test
    void aHitWhoseWatchIsRemovedBeforeItsInstructionFinishesIsNeverReported(){
        breakpoints.addWatch(0x0042, WatchKind.WRITE);
        breakpoints.check(true, 0xC0F1);
        breakpoints.recordAccess(0x0042, true, 0x05);

        breakpoints.removeWatch(0x0042); //the last one left
        assertEquals(Optional.empty(), breakpoints.check(true, 0xC0F3));
        breakpoints.addBreakpoint(0xC0F5);

        assertEquals(Optional.of(new BreakHit(BreakHit.Cause.BREAKPOINT, 0xC0F5, 0, 0xC0F5)), breakpoints.check(true, 0xC0F5));
    }

    @Test
    void aBreakpointKeepsTheWatchesAndAWatchKeepsTheBreakpoints(){
        breakpoints.addWatch(0x0042, WatchKind.WRITE);
        breakpoints.addBreakpoint(0xC000);
        breakpoints.addWatch(0x0043, WatchKind.READ);
        breakpoints.removeBreakpoint(0xD000);
        breakpoints.removeWatch(0x0099);

        assertEquals(List.of(0xC000), List.copyOf(breakpoints.breakpoints()));
        assertEquals(Map.of(0x0042, WatchKind.WRITE, 0x0043, WatchKind.READ), breakpoints.watches());
    }

    @Test
    void listsAreInAddressOrder(){
        breakpoints.addBreakpoint(0xD000);
        breakpoints.addBreakpoint(0x8000);
        breakpoints.addWatch(0x6000, WatchKind.READ);
        breakpoints.addWatch(0x0010, WatchKind.READ);

        assertEquals(List.of(0x8000, 0xD000), List.copyOf(breakpoints.breakpoints()));
        assertEquals(List.of(0x0010, 0x6000), List.copyOf(breakpoints.watches().keySet()));
    }

    @Test
    void listsCannotBeChangedFromOutside(){
        breakpoints.addBreakpoint(0x8000);
        breakpoints.addWatch(0x0010, WatchKind.READ);

        assertThrows(UnsupportedOperationException.class, () -> breakpoints.breakpoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> breakpoints.watches().clear());
    }

    @Test
    void aWatchOnRamCatchesItsMirrors(){
        breakpoints.addWatch(0x0842, WatchKind.WRITE);

        breakpoints.recordAccess(0x1842, true, 9);

        assertEquals(new BreakHit(BreakHit.Cause.WRITE, 0x0042, 9, 0), breakpoints.check(true, 0xC000).orElseThrow());
        assertEquals(Map.of(0x0042, WatchKind.WRITE), breakpoints.watches());
    }

    @Test
    void aWatchOnAPpuRegisterCatchesItsMirrors(){
        breakpoints.addWatch(0x2006, WatchKind.WRITE);

        breakpoints.recordAccess(0x3FFE, true, 0x20);

        assertEquals(0x2006, breakpoints.check(true, 0xC000).orElseThrow().address());
    }

    @ParameterizedTest
    @CsvSource({"0x0000, 0", "0x07FF, 0x07FF", "0x0800, 0", "0x1FFF, 0x07FF", "0x2000, 0x2000", "0x2008, 0x2000",
            "0x3FFF, 0x2007", "0x4000, 0x4000", "0x4016, 0x4016", "0x6000, 0x6000", "0xFFFF, 0xFFFF"})
    void canonicalIsTheLowestMirror(final String address, final String expected){
        assertEquals(Integer.decode(expected), Breakpoints.canonical(Integer.decode(address)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0x10000})
    void rejectsAnAddressOutsideTheCpusRange(final int address){
        assertThrows(IllegalArgumentException.class, () -> breakpoints.addBreakpoint(address));
        assertThrows(IllegalArgumentException.class, () -> breakpoints.addWatch(address, WatchKind.READ));
    }
}
