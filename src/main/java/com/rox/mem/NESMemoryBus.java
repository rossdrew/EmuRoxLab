package com.rox.mem;

/**
 * Routes the NES $2000-$3FFF PPU register range to a PPU bus, $4000-$4017 I/O range to a device bus
 * (e.g. the APU), $6000-$FFFF to a cartridge bus (PRG-RAM/PRG-ROM via a mapper), everything else to
 * the wrapped RAM-backed bus.
 *
 * Simplification: all writes in the I/O range are routed to the device, but only $4015 is a
 * meaningful read (the rest of the range, including $4017 which is also controller-2 read on real
 * hardware, has no controller emulated here and stubs to 0).
 *
 * {@code $4014} (OAM DMA) is special-cased out of the I/O range: it reads 256 bytes back out of
 * {@code this} bus (whatever the source page actually maps to - normally internal RAM) starting at
 * {@code value << 8}, and hands them to the PPU bus in one shot via {@link OamDmaBus#writeOamDma}
 * rather than going through the byte-at-a-time OAMDATA register.
 */
public class NESMemoryBus implements MemoryBus {
    public static final int PPU_START_ADDRESS = 0x2000;
    public static final int PPU_END_ADDRESS = 0x3FFF;
    public static final int IO_START_ADDRESS = 0x4000;
    public static final int IO_END_ADDRESS = 0x4017;
    public static final int STATUS_REGISTER_ADDRESS = 0x4015;
    public static final int OAM_DMA_ADDRESS = 0x4014;
    public static final int CARTRIDGE_START_ADDRESS = 0x6000;
    private static final int OAM_DMA_PAGE_SIZE = 0x100;

    private final MemoryBus ramBus;
    private final MemoryBus apuBus;
    private final MemoryBus cartridgeBus;
    private final OamDmaBus ppuBus;

    public NESMemoryBus(final MemoryBus ramBus,
                        final MemoryBus apuBus,
                        final MemoryBus cartridgeBus,
                        final OamDmaBus ppuBus){
        this.ramBus = ramBus;
        this.apuBus = apuBus;
        this.cartridgeBus = cartridgeBus;
        this.ppuBus = ppuBus;
    }

    @Override
    public int read(final int address) {
        if (address == STATUS_REGISTER_ADDRESS) {
            return apuBus.read(address);
        }
        if (isInPpuRange(address)) {
            return ppuBus.read(address);
        }
        if (isInIORange(address)) {
            return 0;
        }
        if (isInCartridgeRange(address)) {
            return cartridgeBus.read(address);
        }
        return ramBus.read(address);
    }

    @Override
    public void write(final int address, final int value) {
        if (isInPpuRange(address)) {
            ppuBus.write(address, value);
            return;
        }
        if (address == OAM_DMA_ADDRESS) {
            final int pageStart = (value & 0xFF) << 8;
            final int[] pageBytes = new int[OAM_DMA_PAGE_SIZE];
            for (int i = 0; i < OAM_DMA_PAGE_SIZE; i++){
                pageBytes[i] = read(pageStart + i);
            }
            ppuBus.writeOamDma(pageBytes);
            return;
        }
        if (isInIORange(address)) {
            apuBus.write(address, value);
            return;
        }
        if (isInCartridgeRange(address)) {
            cartridgeBus.write(address, value);
            return;
        }
        ramBus.write(address, value);
    }

    private static boolean isInPpuRange(final int address){
        return address >= PPU_START_ADDRESS && address <= PPU_END_ADDRESS;
    }

    private static boolean isInIORange(final int address){
        return address >= IO_START_ADDRESS && address <= IO_END_ADDRESS;
    }

    private static boolean isInCartridgeRange(final int address){
        return address >= CARTRIDGE_START_ADDRESS;
    }
}
