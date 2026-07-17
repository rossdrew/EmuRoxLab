package com.rox.mem;

/**
 * Routes the NES $4000-$4017 I/O range to a device bus (e.g. the APU), everything else to the
 * wrapped RAM-backed bus. No mapper/cartridge decoding.
 *
 * Simplification: all writes in range are routed to the device, but only $4015 is a meaningful
 * read (the rest of the range, including $4017 which is also controller-2 read on real hardware,
 * has no controller emulated here and stubs to 0).
 */
public class NESMemoryBus implements MemoryBus {
    public static final int IO_START_ADDRESS = 0x4000;
    public static final int IO_END_ADDRESS = 0x4017;
    public static final int STATUS_REGISTER_ADDRESS = 0x4015;

    private final MemoryBus ramBus;
    private final MemoryBus apuBus;

    public NESMemoryBus(final MemoryBus ramBus,
                        final MemoryBus apuBus){
        this.ramBus = ramBus;
        this.apuBus = apuBus;
    }

    @Override
    public int read(final int address) {
        if (address == STATUS_REGISTER_ADDRESS) {
            return apuBus.read(address);
        }
        if (isInIORange(address)) {
            return 0;
        }
        return ramBus.read(address);
    }

    @Override
    public void write(final int address, final int value) {
        if (isInIORange(address)) {
            apuBus.write(address, value);
            return;
        }
        ramBus.write(address, value);
    }

    private static boolean isInIORange(final int address){
        return address >= IO_START_ADDRESS && address <= IO_END_ADDRESS;
    }
}
