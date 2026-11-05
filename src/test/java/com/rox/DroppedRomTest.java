package com.rox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DroppedRomTest {
    @ParameterizedTest
    @ValueSource(strings = {"/roms/loz.nes", "/roms/LOZ.NES", "relative/mario3.Nes"})
    void oneNesFileIsTheRom(final String file){
        assertEquals(Optional.of(Path.of(file)), DroppedRom.of(List.of(Path.of(file))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/roms/loz.zip", "/roms/notes.txt", "/roms/nes", "/roms/loz.nes.bak", "/"})
    void anythingElseIsNot(final String file){
        assertEquals(Optional.empty(), DroppedRom.of(List.of(Path.of(file))));
    }

    @Test
    void severalFilesAreNot(){
        assertEquals(Optional.empty(), DroppedRom.of(List.of(Path.of("/roms/a.nes"), Path.of("/roms/b.nes"))));
    }

    @Test
    void nothingIsNot(){
        assertEquals(Optional.empty(), DroppedRom.of(List.of()));
    }
}
