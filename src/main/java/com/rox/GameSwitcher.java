package com.rox;

import com.rox.cartridge.Cartridge;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** The game currently loaded, if any, and swapping it for another ROM's. */
final class GameSwitcher {
    @FunctionalInterface
    interface RomReader {
        /** @throws IllegalArgumentException for a file that isn't a ROM this emulator can run */
        Cartridge read(Path rom) throws IOException;
    }

    @FunctionalInterface
    interface SessionStarter {
        GameSession start(Path rom, Cartridge cartridge) throws LineUnavailableException;
    }

    /** Told just before a game closes and just after one starts, on the thread doing it. */
    interface Listener {
        default void closing(final GameSession session){
        }

        default void started(final GameSession session){
        }
    }

    private final RomReader reader;
    private final SessionStarter starter;
    private final Listener listener;
    private GameSession current;

    GameSwitcher(final RomReader reader, final SessionStarter starter, final Listener listener){
        this.reader = reader;
        this.starter = starter;
        this.listener = listener;
    }

    synchronized Optional<GameSession> current(){
        return Optional.ofNullable(current);
    }

    /**
     * Reads {@code rom} first, so one that can't be loaded leaves the current game running; then closes
     * the current game and starts {@code rom}'s.
     *
     * @throws IOException or {@link IllegalArgumentException} if {@code rom} can't be read - nothing changes
     * @throws LineUnavailableException if the new game can't start - no game is left loaded
     */
    synchronized void open(final Path rom) throws IOException, LineUnavailableException {
        final Cartridge cartridge = reader.read(rom);
        close();
        current = starter.start(rom, cartridge);
        listener.started(current);
    }

    /** Closes the current game, if any. */
    synchronized void close(){
        if (current == null){
            return;
        }
        final GameSession closing = current;
        current = null;
        listener.closing(closing);
        closing.close();
    }
}
