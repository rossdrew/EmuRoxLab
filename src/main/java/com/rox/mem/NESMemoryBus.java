package com.rox.mem;

import com.rox.input.ControllerConfiguration;
import com.rox.input.ControllerPort;

/**
 * Routes the NES:
 * <ul>
 *     <li><b>$2000-$3FFF</b> PPU register range to a PPU bus</li>
 *     <li><b>$4000-$4017</b> I/O range to a device bus (e.g. the APU)</li>
 *     <li><b>$6000-$FFFF</b> to a cartridge bus (PRG-RAM/PRG-ROM via a mapper)</li>
 *     <li><b>Everything else...</b> to the wrapped RAM-backed bus</li>
 * </ul>
 * $4016/$4017 reads are routed to a {@link ControllerPort} each rather than the generic I/O device
 * bus. $4016 writes (the joypad strobe) drive <b>both</b> ports at once - real hardware wires the
 * strobe line to every controller port simultaneously - and never reach the device bus, since that
 * address has no meaning to the APU. $4017 writes are still routed to the device bus unchanged, since
 * that address is genuinely the APU's frame counter register on write.
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
    public static final int CONTROLLER_1_ADDRESS = 0x4016;
    public static final int CONTROLLER_2_ADDRESS = 0x4017;
    private static final int STROBE_BIT = 0x01;
    public static final int CARTRIDGE_START_ADDRESS = 0x6000;
    private static final int OAM_DMA_PAGE_SIZE = 0x100;

    private final MemoryBus ramBus;
    private final MemoryBus apuBus;
    private final MemoryBus cartridgeBus;
    private final OamDmaBus ppuBus;
    private final ControllerPort controllerPort1;
    private final ControllerPort controllerPort2;

    public NESMemoryBus(final MemoryBus ramBus,
                        final MemoryBus apuBus,
                        final MemoryBus cartridgeBus,
                        final OamDmaBus ppuBus,
                        final ControllerConfiguration controllers){
        this.ramBus = ramBus;
        this.apuBus = apuBus;
        this.cartridgeBus = cartridgeBus;
        this.ppuBus = ppuBus;
        this.controllerPort1 = new ControllerPort(controllers.player1(), controllers.player3(), controllers.fourScoreEnabled(),
                ControllerPort.FOUR_SCORE_PORT_1_SIGNATURE_BIT);
        this.controllerPort2 = new ControllerPort(controllers.player2(), controllers.player4(), controllers.fourScoreEnabled(),
                ControllerPort.FOUR_SCORE_PORT_2_SIGNATURE_BIT);
    }

    @Override
    public int read(final int address) {
        if (address == STATUS_REGISTER_ADDRESS) {
            return apuBus.read(address);
        }
        if (address == CONTROLLER_1_ADDRESS) {
            return controllerPort1.read();
        }
        if (address == CONTROLLER_2_ADDRESS) {
            return controllerPort2.read();
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
        if (address == CONTROLLER_1_ADDRESS) {
            //the joypad strobe - wired to both ports simultaneously on real hardware, and unlike
            //$4017 this address has no meaning to the APU, so (unlike everything else in the I/O
            //range) this must not reach the device bus
            final boolean strobeHigh = (value & STROBE_BIT) != 0;
            controllerPort1.strobe(strobeHigh);
            controllerPort2.strobe(strobeHigh);
            return;
        }
        if (isInPpuRange(address)) {
            ppuBus.write(address, value);
            return;
        }
        if (address == OAM_DMA_ADDRESS) {
            final int[] pageBytes = readDmaSourcePage(value);
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

    /**
     * @param page from which to getch DMA block
     * @return the block of {@link #OAM_DMA_PAGE_SIZE} bytes from the given `page`
     */
    private int[] readDmaSourcePage(final int page) {
        final int pageStart = (page & 0xFF) << 8;
        final int[] pageBytes = new int[OAM_DMA_PAGE_SIZE];
        for (int i = 0; i < OAM_DMA_PAGE_SIZE; i++){
            pageBytes[i] = read(pageStart + i);
        }
        return pageBytes;
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
