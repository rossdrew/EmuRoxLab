package com.rox.cartridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CartridgeTest {
    private INesRom rom;
    private Mapper mapper;
    private Cartridge cartridge;

    @BeforeEach
    public void setup(){
        final byte[] fileBytes = new byte[16 + 16384];
        fileBytes[0] = 'N';
        fileBytes[1] = 'E';
        fileBytes[2] = 'S';
        fileBytes[3] = 0x1A;
        fileBytes[4] = 0x01;
        rom = INesRom.parse(fileBytes);
        mapper = mock(Mapper.class);
        cartridge = new Cartridge(rom, mapper);
    }

    @Test
    public void readDelegatesToMapper(){
        when(mapper.read(0x8000)).thenReturn(0x42);

        assertEquals(0x42, cartridge.read(0x8000));
        verify(mapper).read(0x8000);
    }

    @Test
    public void writeDelegatesToMapper(){
        cartridge.write(0x6000, 0x11);

        verify(mapper).write(0x6000, 0x11);
    }

    @Test
    public void romReturnsTheParsedINesRom(){
        assertSame(rom, cartridge.rom());
    }
}
