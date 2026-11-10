package com.rox;

import com.rox.cartridge.Cartridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameSwitcherTest {
    @TempDir
    Path directory;

    private final List<NES> built = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final GameSwitcher.Listener listener = new GameSwitcher.Listener(){
        @Override
        public void closing(final GameSession session){
            events.add("closing " + session.romPath().getFileName());
        }

        @Override
        public void started(final GameSession session){
            events.add("started " + session.romPath().getFileName());
        }
    };

    /** Ends every game's emulation thread, closed by the test or not. */
    @AfterEach
    void powerOffEverything(){
        built.forEach(NES::powerOff);
    }

    private GameSession start(final Path rom, final Cartridge cartridge) throws LineUnavailableException {
        return GameSession.start(rom, cartridge, c -> {
            final NES nes = BlockingNes.create();
            built.add(nes);
            return nes;
        });
    }

    private GameSwitcher switcher(final GameSwitcher.RomReader reader){
        return new GameSwitcher(reader, this::start, listener);
    }

    private GameSwitcher switcher(){
        return switcher(rom -> TestRoms.nrom().cartridge());
    }

    @Test
    void nothingIsLoadedAtFirst(){
        assertEquals(Optional.empty(), switcher().current());
    }

    @Test
    void openingARomStartsItsGame() throws Exception {
        final GameSwitcher switcher = switcher();

        switcher.open(directory.resolve("a.nes"));

        assertEquals(directory.resolve("a.nes"), switcher.current().orElseThrow().romPath());
        verify(built.get(0), timeout(5000)).powerOn(false);
        assertEquals(List.of("started a.nes"), events);
    }

    @Test
    void openingAnotherClosesTheFirstBeforeStartingIt() throws Exception {
        final GameSwitcher switcher = switcher();
        switcher.open(directory.resolve("a.nes"));

        switcher.open(directory.resolve("b.nes"));

        assertEquals(directory.resolve("b.nes"), switcher.current().orElseThrow().romPath());
        verify(built.get(0)).powerOff();
        verify(built.get(1), never()).powerOff();
        assertEquals(List.of("started a.nes", "closing a.nes", "started b.nes"), events);
    }

    @Test
    void theOldGameIsPoweredOffBeforeTheNewOneStarts() throws Exception {
        final List<Boolean> previousPoweredOff = new ArrayList<>();
        final GameSwitcher switcher = new GameSwitcher(rom -> TestRoms.nrom().cartridge(), (rom, cartridge) -> {
            if (!built.isEmpty()){
                previousPoweredOff.add(mockingDetails(built.get(built.size() - 1)).getInvocations().stream()
                        .anyMatch(invocation -> invocation.getMethod().getName().equals("powerOff")));
            }
            return start(rom, cartridge);
        }, listener);
        switcher.open(directory.resolve("a.nes"));

        switcher.open(directory.resolve("b.nes"));

        assertEquals(List.of(true), previousPoweredOff);
    }

    @Test
    void aRomThatCannotBeReadLeavesTheCurrentGameRunning() throws Exception {
        final IllegalArgumentException unsupported = new IllegalArgumentException("Unsupported mapper: 7");
        final GameSwitcher switcher = switcher(rom -> {
            if (rom.endsWith("battletoads.nes")){
                throw unsupported;
            }
            return TestRoms.nrom().cartridge();
        });
        switcher.open(directory.resolve("a.nes"));

        assertSame(unsupported, assertThrows(IllegalArgumentException.class, () -> switcher.open(directory.resolve("battletoads.nes"))));

        assertEquals(directory.resolve("a.nes"), switcher.current().orElseThrow().romPath());
        verify(built.get(0), never()).powerOff();
        assertEquals(List.of("started a.nes"), events);
    }

    @Test
    void anUnreadableFileLeavesTheCurrentGameRunning() throws Exception {
        final IOException missing = new IOException("no such file");
        final GameSwitcher switcher = switcher(rom -> {
            if (rom.endsWith("gone.nes")){
                throw missing;
            }
            return TestRoms.nrom().cartridge();
        });
        switcher.open(directory.resolve("a.nes"));

        assertSame(missing, assertThrows(IOException.class, () -> switcher.open(directory.resolve("gone.nes"))));

        assertTrue(switcher.current().isPresent());
    }

    @Test
    void aGameThatCannotStartLeavesNoGameLoaded() throws Exception {
        final LineUnavailableException noAudio = new LineUnavailableException("no audio");
        final GameSwitcher switcher = new GameSwitcher(rom -> TestRoms.nrom().cartridge(), (rom, cartridge) -> {
            if (rom.endsWith("b.nes")){
                throw noAudio;
            }
            return start(rom, cartridge);
        }, listener);
        switcher.open(directory.resolve("a.nes"));

        assertSame(noAudio, assertThrows(LineUnavailableException.class, () -> switcher.open(directory.resolve("b.nes"))));

        assertEquals(Optional.empty(), switcher.current());
        verify(built.get(0)).powerOff();
        assertEquals(List.of("started a.nes", "closing a.nes"), events);
    }

    @Test
    void closeEndsTheCurrentGame() throws Exception {
        final GameSwitcher switcher = switcher();
        switcher.open(directory.resolve("a.nes"));

        switcher.close();

        assertEquals(Optional.empty(), switcher.current());
        verify(built.get(0)).powerOff();
        assertEquals(List.of("started a.nes", "closing a.nes"), events);
    }

    @Test
    void closingWithNothingLoadedDoesNothing(){
        final GameSwitcher switcher = switcher();

        switcher.close();

        assertEquals(List.of(), events);
    }

    @Test
    void aListenerNeedNotHandleEitherEvent() throws Exception {
        final GameSwitcher switcher = new GameSwitcher(rom -> TestRoms.nrom().cartridge(), this::start, new GameSwitcher.Listener(){
        });

        switcher.open(directory.resolve("a.nes"));
        switcher.close();

        verify(built.get(0)).powerOff();
    }

    @Test
    void shutDownClosesTheCurrentGameAndNoneOpensAfter() throws Exception {
        final GameSwitcher switcher = switcher();
        switcher.open(directory.resolve("a.nes"));

        switcher.shutDown();
        switcher.open(directory.resolve("b.nes"));

        assertEquals(Optional.empty(), switcher.current());
        assertEquals(1, built.size());
        verify(built.get(0)).powerOff();
        assertEquals(List.of("started a.nes", "closing a.nes"), events);
    }

    @Test
    void shutDownWithNothingLoadedStillStopsLaterOpens() throws Exception {
        final GameSwitcher switcher = switcher(rom -> {
            throw new AssertionError("read after shut down");
        });

        switcher.shutDown();
        switcher.open(directory.resolve("a.nes"));

        assertEquals(Optional.empty(), switcher.current());
    }
}
