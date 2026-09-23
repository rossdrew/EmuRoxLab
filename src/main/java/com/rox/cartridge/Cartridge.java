package com.rox.cartridge;

import com.rox.mem.MemoryBus;

/**
 * A loaded ROM: the parsed {@link INesRom} plus whichever {@link Mapper} its header's mapper number
 * selects. Implements {@link MemoryBus} by delegating straight to the mapper - {@link INesRom} is
 * kept around for metadata (mirroring, mapper number) that later phases (PPU nametable mirroring,
 * a CLI printing what got loaded) need, not for address decoding.
 */
public final class Cartridge implements MemoryBus {
    //PRG-RAM on both currently-supported boards (NROM, MMC1) occupies $6000-$7FFF, PRG-ROM starting
    //at $8000 - safe to hardcode here rather than asking Mapper, since a Mapper's own read/write are
    //only ever called with addresses already known to be in $6000-$FFFF (see Mapper's class doc)
    private static final int PRG_ROM_START_ADDRESS = 0x8000;

    private final INesRom rom;
    private final Mapper mapper;
    //no-op default so cartridges nobody ever wires a listener onto (i.e. every non-battery-backed
    //cartridge) pay zero cost - set by BatterySaveManager for battery-backed carts only
    private Runnable onPrgRamWrite = () -> { };

    public Cartridge(final INesRom rom, final Mapper mapper){
        this.rom = rom;
        this.mapper = mapper;
    }

    @Override
    public int read(final int address){
        return mapper.read(address);
    }

    @Override
    public void write(final int address, final int value){
        mapper.write(address, value);
        if (address < PRG_ROM_START_ADDRESS){
            onPrgRamWrite.run();
        }
    }

    /** Notified (on whatever thread called {@link #write}) after every write to the {@code $6000-$7FFF} PRG-RAM window. */
    public void setOnPrgRamWrite(final Runnable listener){
        this.onPrgRamWrite = listener;
    }

    public int[] prgRam(){
        return mapper.prgRam();
    }

    public void restorePrgRam(final int[] prgRam){
        mapper.restorePrgRam(prgRam);
    }

    public int readChr(final int address){
        return mapper.readChr(address);
    }

    public void writeChr(final int address, final int value){
        mapper.writeChr(address, value);
    }

    public Mirroring nametableMirroring(){
        return mapper.nametableMirroring();
    }

    public INesRom rom(){
        return rom;
    }
}
