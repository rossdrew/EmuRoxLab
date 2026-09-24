package com.rox.cartridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    public void readChrDelegatesToMapper(){
        when(mapper.readChr(0x0042)).thenReturn(0x99);

        assertEquals(0x99, cartridge.readChr(0x0042));
        verify(mapper).readChr(0x0042);
    }

    @Test
    public void writeChrDelegatesToMapper(){
        cartridge.writeChr(0x0100, 0x55);

        verify(mapper).writeChr(0x0100, 0x55);
    }

    @Test
    public void nametableMirroringDelegatesToMapper(){
        when(mapper.nametableMirroring()).thenReturn(Mirroring.VERTICAL);

        assertEquals(Mirroring.VERTICAL, cartridge.nametableMirroring());
    }

    @Test
    public void prgRamDelegatesToMapper(){
        when(mapper.prgRam()).thenReturn(new int[]{0x11, 0x22});

        assertArrayEquals(new int[]{0x11, 0x22}, cartridge.prgRam());
    }

    @Test
    public void restorePrgRamDelegatesToMapper(){
        final int[] prgRam = {0x33};

        cartridge.restorePrgRam(prgRam);

        verify(mapper).restorePrgRam(prgRam);
    }

    @Test
    public void onPrgRamWriteFiresForAWriteBelow0x8000(){
        final Runnable listener = mock(Runnable.class);
        cartridge.setOnPrgRamWrite(listener);

        cartridge.write(0x7FFF, 0x11);

        verify(listener).run();
    }

    @Test
    public void onPrgRamWriteDoesNotFireForAWriteAt0x8000OrAbove(){
        final Runnable listener = mock(Runnable.class);
        cartridge.setOnPrgRamWrite(listener);

        cartridge.write(0x8000, 0x11);

        verify(listener, never()).run();
    }

    @Test
    public void writeWithNoListenerRegisteredIsStillFine(){
        //the default no-op listener must not NPE - this is the common case (every non-battery-backed cartridge)
        cartridge.write(0x6000, 0x11);

        verify(mapper).write(0x6000, 0x11);
    }

    @Test
    public void isIrqAssertedDelegatesToMapper(){
        when(mapper.isIrqAsserted()).thenReturn(true);

        assertTrue(cartridge.isIrqAsserted());
    }

    @Test
    public void debugStateDelegatesToMapper(){
        final Map<String, String> state = Map.of("Control", "$15");
        when(mapper.debugState()).thenReturn(state);

        assertEquals(state, cartridge.debugState());
    }
}
