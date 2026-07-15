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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MOS6502IntegrationTest {

    private static final int MAX_TICKS = 50_000;

    private void writeProgram(final Memory ram, final AssembledProgram program) {
        for (int i = 0; i < program.length(); i++) {
            ram.write(program.startAddress() + i, program.bytes()[i]);
        }
    }

    private void runUntil(final MOS6502 cpu, final java.util.function.BooleanSupplier condition) {
        int ticks = 0;
        while (!condition.getAsBoolean() && ticks < MAX_TICKS) {
            cpu.tick();
            ticks++;
        }
        assertTrue(condition.getAsBoolean(), "Condition not reached within the tick budget");
    }

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
        writeProgram(ram, program);

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

    @Test
    public void irqIsIgnoredWhileMaskedThenServicedAfterCLI(){
        final String mainProgram = """
                SEI
                NOP
                NOP
                NOP
                CLI
                NOP
                BRK
                """;
        final String irqHandler = """
                INX
                RTI
                """;

        final AssembledProgram main = Assembler.assemble(mainProgram, 0x8000);
        final AssembledProgram handler = Assembler.assemble(irqHandler, 0x9000);

        final Memory ram = new RAM(65536);
        writeProgram(ram, main);
        writeProgram(ram, handler);
        ram.write(0xFFFE, 0x00); //IRQ vector low -> $9000
        ram.write(0xFFFF, 0x90); //IRQ vector high

        final LatchedMemoryBus bus = new Latched8BitMemoryBus(new MemoryBus8Bit(ram));
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(main.startAddress());
        final MOS6502 cpu = new MOS6502(bus, env);

        env.setIRQLine(true);

        //SEI (2 ticks) + 3x NOP (2 ticks each) = 8 ticks; IRQ must still be masked throughout
        for (int i = 0; i < 8; i++) {
            cpu.tick();
        }
        assertEquals(0, env.getX(), "IRQ should still be masked by SEI");

        runUntil(cpu, () -> env.getX() == 1);
        assertTrue(env.getI(), "interrupt-disable flag should be set on entry to the handler");

        env.setIRQLine(false); //acknowledge, as a real device would once serviced
    }

    @Test
    public void nmiFiresEvenWhileInterruptDisableFlagIsSet(){
        final String mainProgram = """
                SEI
                NOP
                NOP
                BRK
                """;
        final String nmiHandler = """
                INX
                RTI
                """;

        final AssembledProgram main = Assembler.assemble(mainProgram, 0x8000);
        final AssembledProgram handler = Assembler.assemble(nmiHandler, 0x9100);

        final Memory ram = new RAM(65536);
        writeProgram(ram, main);
        writeProgram(ram, handler);
        ram.write(0xFFFA, 0x00); //NMI vector low -> $9100
        ram.write(0xFFFB, 0x91); //NMI vector high

        final LatchedMemoryBus bus = new Latched8BitMemoryBus(new MemoryBus8Bit(ram));
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(main.startAddress());
        final MOS6502 cpu = new MOS6502(bus, env);

        cpu.tick(); //fetch SEI
        cpu.tick(); //execute SEI
        assertTrue(env.getI(), "I should be set by SEI before the NMI is signalled");

        env.signalNMI();

        runUntil(cpu, () -> env.getX() == 1);
    }

    @Test
    public void hardwareInterruptPushesStatusWithBreakFlagClearUnlikeBRK(){
        final Memory ram = new RAM(65536);
        ram.write(0xFFFE, 0x00);
        ram.write(0xFFFF, 0x90); //IRQ vector -> $9000, contents irrelevant, we stop before it's used

        final LatchedMemoryBus bus = new Latched8BitMemoryBus(new MemoryBus8Bit(ram));
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(0x8000);
        final MOS6502 cpu = new MOS6502(bus, env);

        env.setIRQLine(true); //I defaults to false, so this is serviced immediately

        for (int i = 0; i < 7; i++) {
            cpu.tick(); //full 7-cycle hardware interrupt sequence
        }

        final int pushedStatusAddress = 0x0100 | ((env.getStackPointer() + 1) & 0xFF);
        final int pushedStatus = ram.read(pushedStatusAddress);
        assertEquals(0, pushedStatus & 0x10, "hardware interrupts must push status with the break flag clear");
    }

    @Test
    public void brkPushesStatusWithBreakFlagSet(){
        final AssembledProgram main = Assembler.assemble("BRK", 0x8000);

        final Memory ram = new RAM(65536);
        writeProgram(ram, main);
        ram.write(0xFFFE, 0x00);
        ram.write(0xFFFF, 0x90);

        final LatchedMemoryBus bus = new Latched8BitMemoryBus(new MemoryBus8Bit(ram));
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(main.startAddress());
        final MOS6502 cpu = new MOS6502(bus, env);

        for (int i = 0; i < 7; i++) {
            cpu.tick(); //BRK's fetch + 6 explicit ticks
        }

        final int pushedStatusAddress = 0x0100 | ((env.getStackPointer() + 1) & 0xFF);
        final int pushedStatus = ram.read(pushedStatusAddress);
        assertEquals(0x10, pushedStatus & 0x10, "BRK must push status with the break flag set");
    }

    @Test
    public void rtiResumesExactlyWhereTheInterruptedProgramLeftOff(){
        final String mainProgram = """
                NOP
                NOP
                NOP
                BRK
                """;
        final String irqHandler = """
                INX
                RTI
                """;

        final AssembledProgram main = Assembler.assemble(mainProgram, 0x8000);
        final AssembledProgram handler = Assembler.assemble(irqHandler, 0x9000);

        final Memory ram = new RAM(65536);
        writeProgram(ram, main);
        writeProgram(ram, handler);
        ram.write(0xFFFE, 0x00);
        ram.write(0xFFFF, 0x90);

        final LatchedMemoryBus bus = new Latched8BitMemoryBus(new MemoryBus8Bit(ram));
        final MOS6502Environment env = new MOS6502Environment();
        env.setPC(main.startAddress());
        final MOS6502 cpu = new MOS6502(bus, env);

        env.setIRQLine(true); //fires before the very first NOP, since I defaults to false

        runUntil(cpu, () -> env.getX() == 1);
        env.setIRQLine(false); //acknowledge so the 3 NOPs + BRK can run uninterrupted

        runUntilBrk(cpu, env);

        assertEquals(1, env.getX(), "handler should have run exactly once");
        assertEquals(0x8004, env.getPC(), "should have resumed at $8000 and executed exactly the 3 NOPs before reaching this BRK "
                + "(most of RAM is zero-initialised/BRK, so reaching *any* BRK isn't enough - the PC must land here precisely)");
    }
}
