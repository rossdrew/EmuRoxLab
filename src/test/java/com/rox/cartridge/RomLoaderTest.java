package com.rox.cartridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RomLoaderTest {

    private static byte[] mapperRom(final int mapperNumber){
        final byte[] fileBytes = new byte[16 + 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 0x01;
        fileBytes[6] = (byte) ((mapperNumber & 0x0F) << 4);
        fileBytes[7] = (byte) (mapperNumber & 0xF0);
        fileBytes[16] = 0x77; //first PRG-ROM byte, distinctive enough to prove real NROM mapping ran
        return fileBytes;
    }

    @Test
    public void mapperZeroDispatchesToAWorkingNromMapper(){
        final Cartridge cartridge = RomLoader.fromBytes(mapperRom(0));

        assertEquals(0x77, cartridge.read(0x8000), "should read the actual PRG-ROM byte via NROM mapping");
        cartridge.write(0x8000, 0x00);
        assertEquals(0x77, cartridge.read(0x8000), "NROM has no bank registers - $8000+ writes must be no-ops");
    }

    @Test
    public void unsupportedMapperNumberThrowsWithTheNumberInTheMessage(){
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RomLoader.fromBytes(mapperRom(99)));

        assertTrue(exception.getMessage().contains("99"), "message should name the unsupported mapper number");
    }

    @Test
    public void loadReadsAFileFromDisk(@TempDir final Path tempDir) throws IOException {
        final Path romFile = tempDir.resolve("test.nes");
        Files.write(romFile, mapperRom(0));

        final Cartridge cartridge = RomLoader.load(romFile);

        assertEquals(0, cartridge.rom().mapperNumber());
    }
}
