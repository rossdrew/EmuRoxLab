package com.rox.save;

import com.rox.apu.APU;
import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import com.rox.ppu.PPU;

/** Shared test fixture: a {@link SystemSnapshot} built from real components, for save/report tests. */
public final class SystemSnapshots {
    private SystemSnapshots(){
    }

    /** A real (if mostly power-on) snapshot of every component, with some non-default values throughout. */
    public static SystemSnapshot sample(){
        final byte[] fileBytes = new byte[16 + 2 * 0x4000];
        System.arraycopy(new byte[]{'N', 'E', 'S', 0x1A, 2, 0, 0x40, 0}, 0, fileBytes, 0, 8); //MMC3, CHR-RAM
        final Cartridge cartridge = RomLoader.fromBytes(fileBytes);
        cartridge.write(0x6000, 0x12);
        cartridge.write(0x8000, 0xC6);
        cartridge.write(0x8001, 0x05);
        cartridge.writeChr(0x0010, 0x34);
        final PPU ppu = new PPU(cartridge);
        ppu.write(0x2000, 0x80);
        ppu.write(0x2003, 0x10);
        ppu.write(0x2004, 0x56);
        for (int i = 0; i < 1000; i++){
            ppu.tick();
        }
        final RAM ram = new RAM(0x10000);
        ram.write(0x0123, 0x78);
        final APU apu = new APU(new MemoryBus8Bit(ram));
        apu.write(0x4015, 0x0F);
        apu.write(0x4003, 0x08);
        final MOS6502Snapshot cpu = new MOS6502Snapshot(0x8123, 1, 2, 3, 0xFD, 0x4C, 0x23, 0x81,
                true, false, true, false, true, false, true, false, true);
        return new SystemSnapshot(0xDEADBEEFL, cpu, ppu.snapshot(), apu.snapshot(), ram.snapshot(), cartridge.snapshot());
    }
}
