package com.rox.cartridge;

import com.rox.mem.MemoryBus;

/**
 * A loaded ROM: the parsed {@link INesRom} plus whichever {@link Mapper} its header's mapper number
 * selects. Implements {@link MemoryBus} by delegating straight to the mapper - {@link INesRom} is
 * kept around for metadata (mirroring, mapper number) that later phases (PPU nametable mirroring,
 * a CLI printing what got loaded) need, not for address decoding.
 */
public final class Cartridge implements MemoryBus {
    private final INesRom rom;
    private final Mapper mapper;

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
    }

    public INesRom rom(){
        return rom;
    }
}
