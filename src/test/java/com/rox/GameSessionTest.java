package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.debug.breakpoints.Breakpoints;
import com.rox.debug.breakpoints.WatchKind;
import com.rox.save.SaveFileFormat;
import com.rox.save.SavePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.LineUnavailableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameSessionTest {
    @TempDir
    Path directory;

    private Path rom(){
        return directory.resolve("game.nes");
    }

    @Test
    void startPowersTheNesOnOnItsOwnThreadAndCloseWaitsForItToEnd() throws Exception {
        final AtomicReference<Thread> poweredOnBy = new AtomicReference<>();
        final NES nes = BlockingNes.create(poweredOnBy);

        final GameSession session = GameSession.start(rom(), TestRoms.nrom().cartridge(), cartridge -> nes);

        verify(nes, timeout(5000)).powerOn(false);
        assertNotSame(Thread.currentThread(), poweredOnBy.get());
        assertSame(nes, session.nes());
        assertEquals(rom(), session.romPath());

        session.close();

        verify(nes).powerOff();
        assertFalse(poweredOnBy.get().isAlive());
    }

    @Test
    void theNesIsBuiltFromTheGivenCartridge() throws Exception {
        final Cartridge cartridge = TestRoms.nrom().cartridge();
        final AtomicReference<Cartridge> builtFrom = new AtomicReference<>();

        GameSession.start(rom(), cartridge, c -> {
            builtFrom.set(c);
            return BlockingNes.create();
        }).close();

        assertSame(cartridge, builtFrom.get());
    }

    @Test
    void closingFlushesTheBatterySave() throws Exception {
        final Cartridge cartridge = TestRoms.nrom().battery().cartridge();
        final GameSession session = GameSession.start(rom(), cartridge, c -> BlockingNes.create());
        cartridge.write(0x6000, 0x42);

        session.close();

        assertEquals(0x42, SaveFileFormat.readBatterySave(SavePaths.batterySaveFile(rom())).orElseThrow().prgRam()[0]);
    }

    @Test
    void startLoadsAnExistingBatterySave() throws Exception {
        final Cartridge first = TestRoms.nrom().battery().cartridge();
        final GameSession session = GameSession.start(rom(), first, c -> BlockingNes.create());
        first.write(0x6000, 0x42);
        session.close();
        final Cartridge second = TestRoms.nrom().battery().cartridge();

        GameSession.start(rom(), second, c -> BlockingNes.create()).close();

        assertEquals(0x42, second.read(0x6000));
    }

    @Test
    void aCartridgeWithoutABatteryWritesNoSave() throws Exception {
        final Cartridge cartridge = TestRoms.nrom().cartridge();
        final GameSession session = GameSession.start(rom(), cartridge, c -> BlockingNes.create());
        cartridge.write(0x6000, 0x42);

        session.close();

        assertTrue(SaveFileFormat.readBatterySave(SavePaths.batterySaveFile(rom())).isEmpty());
    }

    @Test
    void aNesThatCannotBeBuiltStopsTheBatterySaverAndIsRethrown(){
        final Cartridge cartridge = TestRoms.nrom().battery().cartridge();
        final LineUnavailableException noAudio = new LineUnavailableException("no audio");

        assertSame(noAudio, assertThrows(LineUnavailableException.class,
                () -> GameSession.start(rom(), cartridge, c -> { throw noAudio; })));

        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(
                thread -> thread.getName().equals("BatterySaveManager-flush")), "no saver left running");
    }

    @Test
    void aNesThatCannotBeBuiltForACartridgeWithoutABatteryIsRethrown(){
        final IllegalStateException broken = new IllegalStateException("broken");

        assertSame(broken, assertThrows(IllegalStateException.class,
                () -> GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> { throw broken; })));
    }

    @Test
    void breakpointsSetForARomAreBackWhenItsLoadedAgain() throws Exception {
        final NES first = BlockingNes.create();
        GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> first).close();
        first.breakpoints().addBreakpoint(0xC123);
        first.breakpoints().addWatch(0x2000, WatchKind.WRITE);
        final NES second = BlockingNes.create();

        GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> second).close();

        assertEquals(List.of(0xC123), List.copyOf(second.breakpoints().breakpoints()));
        assertEquals(Map.of(0x2000, WatchKind.WRITE), second.breakpoints().watches());
    }

    @Test
    void anotherRomStartsWithItsOwnBreakpoints() throws Exception {
        final NES first = BlockingNes.create();
        GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> first).close();
        first.breakpoints().addBreakpoint(0xC123);
        final NES other = BlockingNes.create();

        GameSession.start(directory.resolve("other.nes"), TestRoms.nrom().cartridge(), c -> other).close();

        assertTrue(other.breakpoints().breakpoints().isEmpty());
    }

    @Test
    void anUnreadableBreakpointsFileStillStartsTheGame() throws Exception {
        final Path file = SavePaths.breakpointsFile(rom());
        Files.createDirectories(file.getParent());
        Files.writeString(file, "break $C123\nnonsense\n");
        final NES nes = BlockingNes.create();

        GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> nes).close();

        assertEquals(List.of(0xC123), List.copyOf(nes.breakpoints().breakpoints()));
    }

    @Test
    void aSessionCanPowerOnPaused() throws Exception {
        final NES nes = BlockingNes.create();

        GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> nes, true).close();

        verify(nes).powerOn(true);
    }

    @Test
    void closeKeepsWaitingThroughAnInterruptThenRestoresIt() throws Exception {
        final AtomicReference<Thread> poweredOnBy = new AtomicReference<>();
        final NES nes = mock(NES.class);
        when(nes.breakpoints()).thenReturn(new Breakpoints());
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            poweredOnBy.set(Thread.currentThread());
            release.await();
            return null;
        }).when(nes).powerOn(anyBoolean());
        final GameSession session = GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> nes);
        verify(nes, timeout(5000)).powerOn(false);
        //powerOff doesn't end powerOn here - only this, once close is blocked joining after the interrupt
        final Thread closer = Thread.currentThread();
        final Thread releaser = new Thread(() -> {
            while (closer.getState() != Thread.State.WAITING){
                Thread.onSpinWait();
            }
            release.countDown();
        });
        releaser.start();

        Thread.currentThread().interrupt();
        session.close();

        assertTrue(Thread.interrupted(), "interrupt restored");
        assertFalse(poweredOnBy.get().isAlive());
    }
}
