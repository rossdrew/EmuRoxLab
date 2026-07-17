package com.rox;

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
 * {@link NESMemoryBus} with a no-op device bus.
 */
public class NESMemoryBusIntegrationTest {

    private static final int MAX_TICKS = 50_000;

    private void writeProgram(final Memory ram, final AssembledProgram program) {
        for (int i = 0; i < program.length(); i++) {
            ram.write(program.startAddress() + i, program.bytes()[i]);
        }
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
        writeProgram(ram, program);

        final MemoryBus noOpDeviceBus = new MemoryBus() {
            @Override
            public int read(final int address) { return 0; }
            @Override
            public void write(final int address, final int value) { }
        };
        final MemoryBus nesMemoryBus = new NESMemoryBus(new MemoryBus8Bit(ram), noOpDeviceBus);
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
