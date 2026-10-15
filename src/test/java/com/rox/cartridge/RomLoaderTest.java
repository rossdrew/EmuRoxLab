package com.rox.cartridge;

import com.rox.TestRoms;
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
        return TestRoms.mapper(mapperNumber).prg(0, 0x77).bytes(); //first PRG-ROM byte, distinctive enough to prove real NROM mapping ran
    }

    @Test
    public void mapperZeroDispatchesToAWorkingNromMapper(){
        final Cartridge cartridge = RomLoader.fromBytes(mapperRom(0));

        assertEquals(0x77, cartridge.read(0x8000), "should read the actual PRG-ROM byte via NROM mapping");
        cartridge.write(0x8000, 0x00);
        assertEquals(0x77, cartridge.read(0x8000), "NROM has no bank registers - $8000+ writes must be no-ops");
    }

    @Test
    public void mapperOneDispatchesToAWorkingMmc1Mapper(){
        //2 PRG banks so a real MMC1 bank switch (vs. NROM-style ignored write) is observable
        final Cartridge cartridge = TestRoms.mapper(1).prgSize(2 * 0x4000)
                .prg(0, 0x11) //first byte of bank 0
                .prg(0x4000, 0x22) //first byte of bank 1
                .cartridge();

        assertEquals(0x11, cartridge.read(0x8000), "reset state (PRG mode 3): $8000 starts on bank 0");
        cartridge.write(0xE000, 1); //5-write shift sequence, LSB first: 1,0,0,0,0 -> latches 00001 = bank 1
        cartridge.write(0xE000, 0);
        cartridge.write(0xE000, 0);
        cartridge.write(0xE000, 0);
        cartridge.write(0xE000, 0);
        assertEquals(0x22, cartridge.read(0x8000), "after selecting bank 1, $8000 should read its first byte");
    }

    @Test
    public void mapperFourDispatchesToAWorkingMmc3Mapper(){
        //4 PRG banks so R6/fixed-second-last-bank banking is observable
        final Cartridge cartridge = TestRoms.mapper(4).prgSize(4 * 0x2000)
                .prg(3 * 0x2000, 0x33) //first byte of PRG bank 3 (the last bank)
                .cartridge();

        assertEquals(0x33, cartridge.read(0xE000), "$E000-$FFFF should be fixed to the last PRG bank");
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
