package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.save.SaveFileFormat;
import com.rox.save.SavePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.LineUnavailableException;
import java.nio.file.Path;
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

        verify(nes, timeout(5000)).powerOn();
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
    void closeKeepsWaitingThroughAnInterruptThenRestoresIt() throws Exception {
        final AtomicReference<Thread> poweredOnBy = new AtomicReference<>();
        final NES nes = mock(NES.class);
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            poweredOnBy.set(Thread.currentThread());
            release.await();
            return null;
        }).when(nes).powerOn();
        final GameSession session = GameSession.start(rom(), TestRoms.nrom().cartridge(), c -> nes);
        verify(nes, timeout(5000)).powerOn();
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
