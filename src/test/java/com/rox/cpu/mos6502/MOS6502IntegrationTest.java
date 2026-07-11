package com.rox.cpu.mos6502;

import com.rox.cpu.mos6502.assembler.AssembledProgram;
import com.rox.cpu.mos6502.assembler.Assembler;
import com.rox.mem.Latched8BitMemoryBus;
import com.rox.mem.LatchedMemoryBus;
import com.rox.mem.Memory;
import com.rox.mem.MemoryBus;
import com.rox.mem.MemoryBus8Bit;
import com.rox.mem.RAM;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MOS6502IntegrationTest {

    private static final int MAX_TICKS = 50_000;

    @Test
    public void simpleProgram(){
        final String simpleProgram = """
                        LDX #$00      ; X = 0
                        LDA #$09      ; A = value to store

                LOOP:   STA $0200,X   ; Store A at $0200 + X
                        INX           ; X = X + 1
                        CPX #$FF      ; Have we reached the end?
                        BNE LOOP      ; No, continue

                        BRK           ; Stop
                """;

        final AssembledProgram program = Assembler.assemble(simpleProgram, 0x8000);

        final Memory ram = new RAM(65536);
        for (int i = 0; i < program.length(); i++) {
            ram.write(program.startAddress() + i, program.bytes()[i]);
        }

        final MemoryBus bus = new MemoryBus8Bit(ram);
        final LatchedMemoryBus latchedBus = new Latched8BitMemoryBus(bus);
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(program.startAddress());
        final MOS6502 cpu = new MOS6502(latchedBus, env);

        runUntilBrk(cpu, env);

        assertEquals(0xFF, env.getX());
        assertEquals(0x09, env.getA());
        for (int address = 0x0200; address <= 0x02FE; address++) {
            assertEquals(0x09, ram.read(address), "mismatch at $" + Integer.toHexString(address));
        }
        assertEquals(0, ram.read(0x02FF), "loop should stop one byte short of $02FF");
    }

    private void runUntilBrk(final MOS6502 cpu, final MOS6502Environment env) {
        int ticks = 0;
        do {
            cpu.tick();
            ticks++;
        } while (env.getIR() != MOS6502OpCode.BRK_IMP.getId() && ticks < MAX_TICKS);

        assertEquals(MOS6502OpCode.BRK_IMP.getId(), env.getIR(), "Program did not reach BRK within the tick budget");
    }
}
