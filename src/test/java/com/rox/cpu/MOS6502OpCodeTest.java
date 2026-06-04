package com.rox.cpu;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.rox.cpu.MOS6502OpCode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

//Uses the correct addressing mode and consumes the correct cycles
public class MOS6502OpCodeTest {
    Memory ram;
    MemoryBus bus;
    Latched8BitMemoryBus latchedBus;
    MOS6502Environment env;
    MOS6502 cpu;

    @BeforeEach
    public void setup() {
        ram = new RAM(65536);
        bus = new MemoryBus8Bit(ram);
        latchedBus = new Latched8BitMemoryBus(bus);
        env = new MOS6502Environment();
        cpu = new MOS6502(latchedBus, env);
    }

    @Test
    void adcZeroPageReadsFromZeroPageAndAddsWithCarry() {
        ram.write(0x8000, ADC_Z.getId()); // opcode: ADC_Z
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
    void adcImmediateReadsOperandFromPcAndAddsWithCarry() {
        ram.write(0x8000, ADC_I.getId()); // opcode: ADC_I
        ram.write(0x8001, 0x20);          // immediate operand value

        env.setPC(0x8000);
        env.setA(0x10);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // LOAD_PC_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcAbsoluteReadsFromAbsoluteAddressAndAddsWithCarry() {
        ram.write(0x8000, ADC_ABS.getId());       // opcode: ADC_ABS
        ram.write(0x8001, 0x34);            // low byte
        ram.write(0x8002, 0x12);            // high byte
        ram.write(0x1234, 0x20);            // operand value

        env.setPC(0x8000);
        env.setA(0x10);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // ADH_FROM_PC
        cpu.tick(); // LOAD_AD_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8003, env.getPC());
    }

    @Test
    void adcAbsoluteXUsesFourCyclesWhenNoPageCross() {
        ram.write(0x8000, ADC_ABS_X.getId()); // opcode: ADC_ABS_X
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
        ram.write(0x8000, ADC_ABS_X.getId()); // opcode: ADC_ABS_X
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
        ram.write(0x8000, ADC_ABS_X.getId());
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

    @Test
    void adcAbsoluteYReadsFromAbsoluteYAddressAndAddsWithCarry() {
        ram.write(0x8000, ADC_ABS_Y.getId());       // opcode: ADC_ABS_Y
        ram.write(0x8001, 0x34);              // low byte
        ram.write(0x8002, 0x12);              // high byte
        ram.write(0x1239, 0x20);              // $1234 + Y(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setY(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // ADH_FROM_PC + AD_PLUS_Y
        cpu.tick(); // LOAD_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8003, env.getPC());
    }

    @Test
    void adcAbsoluteYUsesExtraCycleWhenPageCrosses() {
        ram.write(0x8000, ADC_ABS_Y.getId());
        ram.write(0x8001, 0xFF);
        ram.write(0x8002, 0x12);
        ram.write(0x1304, 0x20); // $12FF + Y(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setY(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // ADL_FROM_PC
        cpu.tick(); // ADH_FROM_PC + AD_PLUS_Y

        assertEquals(0x10, env.getA()); // not executed yet

        cpu.tick(); // dummy read / page-cross fix

        assertEquals(0x10, env.getA()); // still not executed

        cpu.tick(); // LOAD_ADDRESS + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8003, env.getPC());
    }

    @Test
    void adcIndirectXReadsFromIndexedIndirectAddressAndAddsWithCarry() {
        ram.write(0x8000, ADC_IND_X.getId());       // opcode: ADC_IND_X
        ram.write(0x8001, 0x44);              // zero-page base pointer

        ram.write(0x0049, 0x34);              // low byte:  $44 + X(5)
        ram.write(0x004A, 0x12);              // high byte: $44 + X(5) + 1

        ram.write(0x1234, 0x20);              // operand value

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // fetch zero-page operand
        cpu.tick(); // add X, read effective address low byte
        cpu.tick(); // read effective address high byte
        cpu.tick(); // read operand
        cpu.tick(); // ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcIndirectXWrapsZeroPagePointerWhenAddingX() {
        ram.write(0x8000, ADC_IND_X.getId());
        ram.write(0x8001, 0xFE);

        ram.write(0x0003, 0x34); // ($FE + X(5)) & $FF = $03
        ram.write(0x0004, 0x12);

        ram.write(0x1234, 0x20);

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick();
        cpu.tick();
        cpu.tick();
        cpu.tick();
        cpu.tick();
        cpu.tick();

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcIndirectYReadsFromIndirectIndexedAddressAndAddsWithCarry() {
        ram.write(0x8000, ADC_IND_Y.getId());       // opcode: ADC_IND_Y
        ram.write(0x8001, 0x44);              // zero-page pointer

        ram.write(0x0044, 0x34);              // low byte
        ram.write(0x0045, 0x12);              // high byte

        ram.write(0x1239, 0x20);              // $1234 + Y(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setY(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // fetch zero-page pointer
        cpu.tick(); // read effective address low byte
        cpu.tick(); // read effective address high byte + add Y
        cpu.tick(); // read operand + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcIndirectYUsesExtraCycleWhenPageCrosses() {
        ram.write(0x8000, ADC_IND_Y.getId());
        ram.write(0x8001, 0x44);

        ram.write(0x0044, 0xFF); // base low
        ram.write(0x0045, 0x12); // base high

        ram.write(0x1304, 0x20); // $12FF + Y(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setY(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // fetch zero-page pointer
        cpu.tick(); // read effective address low byte
        cpu.tick(); // read effective address high byte + add Y

        assertEquals(0x10, env.getA());

        cpu.tick(); // dummy read / page-cross fix

        assertEquals(0x10, env.getA());

        cpu.tick(); // read operand + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcZeroPageXReadsFromZeroPageOffsetByXAndAddsWithCarry() {
        ram.write(0x8000, ADC_Z_X.getId());     // opcode: ADC_Z_X
        ram.write(0x8001, 0x44);          // zero-page base address

        ram.write(0x0049, 0x20);          // $44 + X(5)

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick(); // fetch opcode
        cpu.tick(); // fetch zero-page address
        cpu.tick(); // add X to address
        cpu.tick(); // read operand + ADC

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void adcZeroPageXWrapsAroundZeroPage() {
        ram.write(0x8000, ADC_Z_X.getId());
        ram.write(0x8001, 0xFE);

        ram.write(0x0003, 0x20); // ($FE + X(5)) & $FF = $03

        env.setPC(0x8000);
        env.setA(0x10);
        env.setX(0x05);
        env.setCarry(true);

        cpu.tick();
        cpu.tick();
        cpu.tick();
        cpu.tick();

        assertEquals(0x31, env.getA());
        assertEquals(0x8002, env.getPC());
    }

    @ParameterizedTest(name = "LDA_I #{index}: value={0}, Z={1}, N={2}")
    @CsvSource({
            //Value  Z      N
            "0x20, false, false",
            "0x00, true,  false",
            "0x80, false, true",
            "0xFF, false, true",
            "0x7F, false, false"
    })
    void ldaImmediateLoadsAccumulatorAndSetsFlags(int value,
                                                  boolean expectedZero,
                                                  boolean expectedNegative) {
        ram.write(0x8000, LDA_I.getId());
        ram.write(0x8001, value);

        env.setPC(0x8000);

        cpu.tick(); // fetch opcode
        cpu.tick(); // load A

        assertEquals(value & 0xFF, env.getA());
        assertEquals(expectedZero, env.getZ());
        assertEquals(expectedNegative, env.getN());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void ldaImmediateDoesNotLoadUntilSecondTick() {
        ram.write(0x8000, LDA_I.getId());
        ram.write(0x8001, 0x20);

        env.setPC(0x8000);

        cpu.tick();

        assertEquals(0x00, env.getA());

        cpu.tick();

        assertEquals(0x20, env.getA());
    }

    @ParameterizedTest(name = "LDA_Z value={0}")
    @CsvSource({
            "0x20, false, false",
            "0x00, true,  false",
            "0x80, false, true",
            "0xFF, false, true"
    })
    void ldaZeroPageLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
        ram.write(0x8000, LDA_Z.getId());
        ram.write(0x8001, 0x44);
        ram.write(0x0044, value);

        env.setPC(0x8000);

        cpu.tick(); //Get opcode
        cpu.tick(); //Get operand: zero page pointer
        cpu.tick(); //Load value at pointer to accumulator

        assertEquals(value & 0xFF, env.getA());
        assertEquals(zero, env.getZ());
        assertEquals(negative, env.getN());
        assertEquals(0x8002, env.getPC());
    }

    @Test
    void ldaZeroPageDoesNotLoadUntilThirdTick() {
        ram.write(0x8000, LDA_Z.getId());
        ram.write(0x8001, 0x44);
        ram.write(0x0044, 0x20);

        env.setPC(0x8000);

        cpu.tick();
        cpu.tick();
        assertEquals(0x00, env.getA());

        cpu.tick();
        assertEquals(0x20, env.getA());
    }
}
//    @ParameterizedTest(name = "LDA_Z value={0}")
//    @CsvSource({
//            "0x20, false, false",
//            "0x00, true,  false",
//            "0x80, false, true",
//            "0xFF, false, true"
//    })
//    void ldaZeroPageLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
//        ram.write(0x8000, LDA_Z.getId());
//        ram.write(0x8001, 0x44);
//        ram.write(0x0044, value);
//
//        env.setPC(0x8000);
//
//        cpu.tick();
//        cpu.tick();
//        cpu.tick();
//
//        assertEquals(value & 0xFF, env.getA());
//        assertEquals(zero, env.isZero());
//        assertEquals(negative, env.isNegative());
//        assertEquals(0x8002, env.getPC());
//    }
//
//    @Test
//    void ldaZeroPageDoesNotLoadUntilThirdTick() {
//        ram.write(0x8000, LDA_Z.getId());
//        ram.write(0x8001, 0x44);
//        ram.write(0x0044, 0x20);
//
//        env.setPC(0x8000);
//
//        cpu.tick();
//        cpu.tick();
//        assertEquals(0x00, env.getA());
//
//        cpu.tick();
//        assertEquals(0x20, env.getA());
//    }
//    @ParameterizedTest(name = "LDA_Z_X value={0}")
//    @CsvSource({
//            "0x20, false, false",
//            "0x00, true,  false",
//            "0x80, false, true",
//            "0xFF, false, true"
//    })
//    void ldaZeroPageXLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
//        ram.write(0x8000, LDA_Z_X.getId());
//        ram.write(0x8001, 0x44);
//        ram.write(0x0049, value);
//
//        env.setPC(0x8000);
//        env.setX(0x05);
//
//        cpu.tick();
//        cpu.tick();
//        cpu.tick();
//        cpu.tick();
//
//        assertEquals(value & 0xFF, env.getA());
//        assertEquals(zero, env.isZero());
//        assertEquals(negative, env.isNegative());
//        assertEquals(0x8002, env.getPC());
//    }
//
//        @Test
//        void ldaZeroPageXWrapsZeroPage() {
//            ram.write(0x8000, LDA_Z_X.getId());
//            ram.write(0x8001, 0xFE);
//            ram.write(0x0003, 0x20);
//
//            env.setPC(0x8000);
//            env.setX(0x05);
//
//            cpu.tick();
//            cpu.tick();
//            cpu.tick();
//            cpu.tick();
//
//            assertEquals(0x20, env.getA());
//            assertEquals(0x8002, env.getPC());
//        }
//    }
//    @ParameterizedTest(name = "LDA_ABS value={0}")
//    @CsvSource({
//            "0x20, false, false",
//            "0x00, true,  false",
//            "0x80, false, true",
//            "0xFF, false, true"
//    })
//    void ldaAbsoluteLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
//        ram.write(0x8000, LDA_ABS.getId());
//        ram.write(0x8001, 0x34);
//        ram.write(0x8002, 0x12);
//        ram.write(0x1234, value);
//
//        env.setPC(0x8000);
//
//        cpu.tick();
//        cpu.tick();
//        cpu.tick();
//        cpu.tick();
//
//        assertEquals(value & 0xFF, env.getA());
//        assertEquals(zero, env.isZero());
//        assertEquals(negative, env.isNegative());
//        assertEquals(0x8003, env.getPC());
//    }
//
//        @Test
//        void ldaAbsoluteDoesNotLoadUntilFourthTick() {
//            ram.write(0x8000, LDA_ABS.getId());
//            ram.write(0x8001, 0x34);
//            ram.write(0x8002, 0x12);
//            ram.write(0x1234, 0x20);
//
//            env.setPC(0x8000);
//
//            cpu.tick();
//            cpu.tick();
//            cpu.tick();
//            assertEquals(0x00, env.getA());
//
//            cpu.tick();
//            assertEquals(0x20, env.getA());
//        }
