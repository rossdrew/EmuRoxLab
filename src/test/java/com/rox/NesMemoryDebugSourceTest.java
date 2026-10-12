package com.rox;

import com.rox.audio.AudioOutput;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class NesMemoryDebugSourceTest {
    private static final int PRG_ROM_SIZE = 0x4000;

    /** NROM, reset vector -> $9000: INX; STX $10; JMP $9000. PRG-ROM byte at $8000 is $42. */
    private static NES nes(){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        fileBytes[header.length] = 0x42;
        final byte[] program = {(byte) 0xE8, (byte) 0x86, 0x10, 0x4C, 0x00, (byte) 0x90};
        System.arraycopy(program, 0, fileBytes, header.length + 0x1000, program.length);
        fileBytes[header.length + 0x3FFC] = 0x00;
        fileBytes[header.length + 0x3FFD] = (byte) 0x90;
        final Cartridge cartridge = RomLoader.fromBytes(fileBytes);
        final NES nes = new NES(mock(AudioOutput.class), cartridge, new ManuallyTickedClock());
        nes.cpu().reset();
        return nes;
    }

    private static void runLoops(final NES nes, final int loops){
        for (int i = 0; i < loops * 8; i++){
            nes.clock().tick();
        }
    }

    @Test
    public void peekReadsRamAsTheProgramLeftIt(){
        final NES nes = nes();
        runLoops(nes, 3);

        assertEquals(3, new NesMemoryDebugSource(nes).peek().applyAsInt(0x0010));
    }

    @Test
    public void peekReadsRamMirrorsCartridgeAndRegisterPlaceholder(){
        final NES nes = nes();
        runLoops(nes, 3);
        final IntUnaryOperator peek = new NesMemoryDebugSource(nes).peek();

        assertEquals(3, peek.applyAsInt(0x0810), "RAM mirror");
        assertEquals(0x42, peek.applyAsInt(0x8000), "PRG-ROM");
        assertEquals(DebugCapture.REGISTER_PLACEHOLDER_BYTE, peek.applyAsInt(0x2002), "PPUSTATUS must not be read");
    }

    @Test
    public void eachPeekIsAConsistentCopyTakenWhenAskedFor(){
        final NES nes = nes();
        runLoops(nes, 3);
        final NesMemoryDebugSource source = new NesMemoryDebugSource(nes);
        final IntUnaryOperator earlier = source.peek();

        runLoops(nes, 2);

        assertEquals(3, earlier.applyAsInt(0x0010), "an existing peek doesn't change under it");
        assertEquals(5, source.peek().applyAsInt(0x0010), "a new peek sees the new value");
    }
}
