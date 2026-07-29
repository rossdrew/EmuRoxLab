package com.rox.mem;

/**
 * Routes the NES $4000-$4017 I/O range to a device bus (e.g. the APU), $6000-$FFFF to a cartridge
 * bus (PRG-RAM/PRG-ROM via a mapper), everything else to the wrapped RAM-backed bus.
 *
 * Simplification: all writes in the I/O range are routed to the device, but only $4015 is a
 * meaningful read (the rest of the range, including $4017 which is also controller-2 read on real
 * hardware, has no controller emulated here and stubs to 0). $2000-$3FFF (PPU registers) isn't
 * decoded yet either - falls through to RAM until a PPU exists.
 */
public class NESMemoryBus implements MemoryBus {
    public static final int IO_START_ADDRESS = 0x4000;
    public static final int IO_END_ADDRESS = 0x4017;
    public static final int STATUS_REGISTER_ADDRESS = 0x4015;
    public static final int CARTRIDGE_START_ADDRESS = 0x6000;

    private final MemoryBus ramBus;
    private final MemoryBus apuBus;
    private final MemoryBus cartridgeBus;

    public NESMemoryBus(final MemoryBus ramBus,
                        final MemoryBus apuBus,
                        final MemoryBus cartridgeBus){
        this.ramBus = ramBus;
        this.apuBus = apuBus;
        this.cartridgeBus = cartridgeBus;
    }

    @Override
    public int read(final int address) {
        if (address == STATUS_REGISTER_ADDRESS) {
            return apuBus.read(address);
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

    private static boolean isInIORange(final int address){
        return address >= IO_START_ADDRESS && address <= IO_END_ADDRESS;
    }

    private static boolean isInCartridgeRange(final int address){
        return address >= CARTRIDGE_START_ADDRESS;
    }
}
