package com.rox;

import com.rox.cartridge.Cartridge;
import com.rox.cartridge.RomLoader;
import com.rox.cpu.mos6502.MOS6502;
import com.rox.cpu.mos6502.MOS6502Environment;
import com.rox.cpu.mos6502.MOS6502OpCode;
import com.rox.cpu.mos6502.assembler.AssembledProgram;
import com.rox.cpu.mos6502.assembler.Assembler;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.Memory;
import com.rox.mem.MemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.NESMemoryBus;
import com.rox.mem.RAM;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression check: existing CPU programs, none of which touch $4000-$4017, must run
 * identically whether the bus is a plain {@link MemoryBus8Bit} or one wrapped in
 * {@link NESMemoryBus} with a no-op device bus and the program loaded as a synthetic NROM cartridge.
 */
public class NESMemoryBusIntegrationTest {

    private static final int MAX_TICKS = 50_000;
    private static final int PRG_ROM_SIZE = 0x4000;

    /** Wraps an assembled program (loaded at $8000) as a minimal single-bank NROM (mapper 0) cartridge. */
    private Cartridge asNromCartridge(final AssembledProgram program){
        final byte[] header = {'N', 'E', 'S', 0x1A, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        final byte[] fileBytes = new byte[header.length + PRG_ROM_SIZE];
        System.arraycopy(header, 0, fileBytes, 0, header.length);
        final int[] programBytes = program.bytes();
        for (int i = 0; i < programBytes.length; i++){
            fileBytes[header.length + i] = (byte) programBytes[i];
        }
        return RomLoader.fromBytes(fileBytes);
    }

    @Test
    public void simpleProgramRunsIdenticallyThroughNESMemoryBus(){
        final String simpleProgram = """
                        LDX #$00      ; X = 0
                        LDA #$09      ; A = value to store

                LOOP:   STA $0200,X   ; Store A at $0200 + X
                        INX           ; X = X + 1
                        CPX #$FF      ; Have we reached the end?
                        BNE LOOP      ; No, continue

                        BRK           ; Stop
                """;

        final Memory ram = new RAM(65536);
        final AssembledProgram program = Assembler.assemble(simpleProgram, 0x8000);
        final Cartridge cartridge = asNromCartridge(program);

        final MemoryBus noOpDeviceBus = new MemoryBus() {
            @Override
            public int read(final int address) { return 0; }
            @Override
            public void write(final int address, final int value) { }
        };
        final MemoryBus nesMemoryBus = new NESMemoryBus(new MemoryBus8Bit(ram), noOpDeviceBus, cartridge);
        final MOS6502 cpu = new MOS6502(new Latched8BitMemoryBus(nesMemoryBus));
        cpu.setPC(program.startAddress());

        int ticks = 0;
        MOS6502Environment env;
        do {
            cpu.tick();
            env = cpu.getEnvironmentSnapshot();
            ticks++;
        } while (env.getIR() != MOS6502OpCode.BRK_IMP.getId() && ticks < MAX_TICKS);
        assertEquals(MOS6502OpCode.BRK_IMP.getId(), env.getIR(), "Program did not reach BRK within the tick budget");

        assertEquals(0xFF, env.getX());
        assertEquals(0x09, env.getA());
        for (int address = 0x0200; address <= 0x02FE; address++) {
            assertEquals(0x09, ram.read(address), "mismatch at $" + Integer.toHexString(address));
        }
        assertEquals(0, ram.read(0x02FF), "loop should stop one byte short of $02FF");
    }
}
