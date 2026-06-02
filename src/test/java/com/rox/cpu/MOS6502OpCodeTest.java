package com.rox.cpu;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

//Uses the correct addressing mode and consumes the correct cycles
public class MOS6502OpCodeTest {
    Memory ram;
    MemoryBus bus;
    Latched8BitMemoryBus latchedBus;
    MOS6502Environment env;
    MOS6502 cpu;

    @BeforeEach
    public void setup(){
        ram = new RAM(65536);
        bus = new MemoryBus8Bit(ram);
        latchedBus = new Latched8BitMemoryBus(bus);
        env = new MOS6502Environment();
        cpu = new MOS6502(latchedBus, env);
    }

    @Test
    void adcZeroPageReadsFromZeroPageAndAddsWithCarry() {
        ram.write(0x8000, 0x65); // opcode: ADC_Z
        ram.write(0x8001, 0x44); // operand: zero-page address
        ram.write(0x0044, 0x20); // operand value

        env.setPC(0x8000);
        env.setA(0x10);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // LOAD_ADL_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcAbsoluteXUsesFourCyclesWhenNoPageCross() {
        ram.write(0x8000, 0x7D); // opcode: ADC_ABS_X
        ram.write(0x8001, 0x34); // operand 1/2: low byte
        ram.write(0x8002, 0x12); // operand 2/2: high byte
        ram.write(0x1239, 0x20); // $1234 + X(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // ADH_FROM_PC + AD_PLUS_X
        cpu.tick(); // LOAD_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8003, env.getPC());
    }

    @Test
    void adcAbsoluteXUsesFiveCyclesWhenPageCrosses() {
        ram.write(0x8000, 0x7D); // opcode: ADC_ABS_X
        ram.write(0x8001, 0xFF); // operand 1/2: low byte
        ram.write(0x8002, 0x12); // operand 2/2: high byte
        ram.write(0x1304, 0x20); // $12FF + X(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // ADH_FROM_PC + AD_PLUS_X
        cpu.tick(); // dummy read + fix page
        cpu.tick(); // LOAD_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8003, env.getPC());
    }

    @Test
    void adcAbsoluteXDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
        ram.write(0x8000, 0x7D);
        ram.write(0x8001, 0xFF);
        ram.write(0x8002, 0x12);
        ram.write(0x1304, 0x20);

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick();
        cpu.tick();
        cpu.tick();

        assertEquals(0x10, env.getA()); // ADC not yet executed

        cpu.tick(); // extra page-cross tick

        assertEquals(0x10, env.getA()); // still not yet executed

        cpu.tick(); // actual ADC

        assertEquals(0x31, env.getA());
    }
}
