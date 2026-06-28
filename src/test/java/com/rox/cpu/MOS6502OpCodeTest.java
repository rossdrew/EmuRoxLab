package com.rox.cpu;

import com.rox.mem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.rox.cpu.MOS6502OpCode.*;
import static org.junit.jupiter.api.Assertions.*;

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

    @Nested
    class ADC {
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
            ram.write(0x8000, ADC_I.getId());  // opcode: ADC_I
            ram.write(0x8001, 0x20);     // immediate operand value

            env.setPC(0x8000);
            env.setA(0x10);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // LOAD_PC_ADDRESS + ADC

            assertEquals(0x31, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void adcImmediateCompletesOnSecondTick() {
            ram.write(0x8000, ADC_I.getId());
            ram.write(0x8001, 0x20);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setCarry(true);

            cpu.tick();

            assertEquals(0x10, env.getA());
            assertEquals(0x8001, env.getPC());

            cpu.tick();

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

            cpu.tick(); // Fetch opcode
            cpu.tick(); // Fetch zero-page operand
            cpu.tick(); // Add X offset
            cpu.tick(); // Fetch effective address low byte
            cpu.tick(); // Fetch effective address high byte
            cpu.tick(); // Perform ADC

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

        @ParameterizedTest(name = "ADC_Z_X A={0}, operand={1}, C={2}")
        @CsvSource({
                // A      Val   C-in   Expected    V       N       C
                "0x10, 0x20, true,  0x31,       false, false, false",
                "0x10, 0x20, false, 0x30,       false, false, false",
                "0xFF, 0x01, false, 0x00,       false, false, true",
                "0x7F, 0x01, false, 0x80,       true,  true,  false",
                "0x80, 0x80, false, 0x00,       true,  false, true"
        })
        void adcZeroPageXAddsWithCarryAndSetsFlags(int accumulator,
                                                   int value,
                                                   boolean carryIn,
                                                   int expected,
                                                   boolean expectedOverflow,
                                                   boolean expectedNegative,
                                                   boolean expectedCarry) {
            ram.write(0x8000, ADC_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value); // $44 + X(5)

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setX(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // ADDRESS_PC, MEM_TO_ADL
            cpu.tick(); // X_OFFSET_ADDRESS
            cpu.tick(); // ADDRESS_ADL, ADC

            assertEquals(expected, env.getA());
            assertEquals(expectedOverflow, env.getV());
            assertEquals(expectedNegative, env.getN());
            assertEquals(expectedCarry, env.getCarry());
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
    }

    @Nested
    class LDA {
        @ParameterizedTest(name = "LDA_I #{index}: value={0}, Z={1}, N={2}")
        @CsvSource({
                // Value  Z      N
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
                // Val    Z      N
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
            cpu.tick(); //Get operand: zero-page pointer
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

        @ParameterizedTest(name = "LDA_Z_X value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaZeroPageXLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch zero-page address to ADL
            cpu.tick(); //Add X to ADL (wrapping)
            cpu.tick(); //Read value at $00:ADL to A and set flags

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldaZeroPageXWrapsZeroPage() {
            ram.write(0x8000, LDA_Z_X.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x20);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x20, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "LDA_ABS value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaAbsoluteLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldaAbsoluteDoesNotLoadUntilFourthTick() {
            ram.write(0x8000, LDA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x20);

            env.setPC(0x8000);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x20, env.getA());
        }

        @ParameterizedTest(name = "LDA_ABS_X value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaAbsoluteXLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldaAbsoluteXUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, LDA_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x20);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x20, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "LDA_ABS_Y value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaAbsoluteYLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldaAbsoluteYUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, LDA_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x20);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x20, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "LDA_IND_X value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaIndirectXLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldaIndirectXWrapsZeroPagePointer() {
            ram.write(0x8000, LDA_IND_X.getId());
            ram.write(0x8001, 0xFE);

            ram.write(0x0003, 0x34);
            ram.write(0x0004, 0x12);
            ram.write(0x1234, 0x20);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x20, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "LDA_IND_Y value={0}")
        @CsvSource({
                // Val    Z      N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldaIndirectYLoadsAccumulatorAndSetsFlags(int value, boolean zero, boolean negative) {
            ram.write(0x8000, LDA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(value & 0xFF, env.getA());
            assertEquals(zero, env.getZ());
            assertEquals(negative, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldaIndirectYUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, LDA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);
            ram.write(0x1304, 0x20);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x00, env.getA());

            cpu.tick();

            assertEquals(0x20, env.getA());
            assertEquals(0x8002, env.getPC());
        }
    }

    @Nested
    class AND {
        @ParameterizedTest(name = "AND_I A={0}, operand={1}")
        @CsvSource({
                // A,    Operand, Result, Z,     N
                "0xAA,  0x0F,    0x0A,  false, false",
                "0xF0,  0x0F,    0x00,  true,  false",
                "0xFF,  0x80,    0x80,  false, true",
                "0x7F,  0x0F,    0x0F,  false, false",
                "0x00,  0xFF,    0x00,  true,  false"
        })
        void andImmediatePerformsBitwiseAndAndSetsFlags(int accumulator,
                                                        int operand,
                                                        int expectedResult,
                                                        boolean expectedZero,
                                                        boolean expectedNegative) {
            ram.write(0x8000, AND_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);
            env.setA(accumulator);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand + AND

            assertEquals(expectedResult & 0xFF, env.getA());
            assertEquals(expectedZero, env.getZ());
            assertEquals(expectedNegative, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void andImmediateDoesNotExecuteUntilSecondTick() {
            ram.write(0x8000, AND_I.getId());
            ram.write(0x8001, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);

            cpu.tick(); // fetch opcode

            assertEquals(0xAA, env.getA());

            cpu.tick(); // AND

            assertEquals(0x0A, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "AND_Z A={0}, operand={1}")
        @CsvSource({
              // A      Op     Expected   Z      N
                "0xAA,  0x0F,  0x0A,      false, false",
                "0xF0,  0x0F,  0x00,      true,  false",
                "0xFF,  0x80,  0x80,      false, true"
        })
        void andZeroPagePerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, value);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); //Get opcode
            cpu.tick(); //Get argument
            cpu.tick(); //Address argument and perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void andZeroPageDoesNotExecuteUntilThirdTick() {
            ram.write(0x8000, AND_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);

            cpu.tick();
            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_ABS A={0}, operand={1}")
        @CsvSource({
                // A      Val   Expected   Z      N
                "0xAA, 0x0F, 0x0A,      false, false",
                "0xF0, 0x0F, 0x00,      true,  false",
                "0xFF, 0x80, 0x80,      false, true"
        })
        void andAbsolutePerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (ADL)
            cpu.tick(); //Fetch argument 2 (ADH)
            cpu.tick(); //Address AD and perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void andAbsoluteDoesNotExecuteUntilFourthTick() {
            ram.write(0x8000, AND_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_Z_X A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z      N
                "0xAA, 0x0F,  0x0A,     false, false",
                "0xF0, 0x0F,  0x00,     true,  false",
                "0xFF, 0x80,  0x80,     false, true"
        })
        void andZeroPageXPerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument
            cpu.tick(); //Adding X offset to address bus (XXX no temporary register used)
            cpu.tick(); //Perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void andZeroPageXWrapsWithinZeroPage() {
            ram.write(0x8000, AND_Z_X.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_ABS_X A={0}, operand={1}")
        @CsvSource({
                // A      Val   Expected   Z      N
                "0xAA, 0x0F, 0x0A,      false, false",
                "0xF0, 0x0F, 0x00,      true,  false",
                "0xFF, 0x80, 0x80,      false, true"
        })
        void andAbsoluteXPerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (ADL)
            cpu.tick(); //Fetch argument 2 (ADH) & Add X offset
            cpu.tick(); //Perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void andAbsoluteXUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, AND_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_ABS_Y A={0}, operand={1}")
        @CsvSource({
                // A      Val   Expected   Z      N
                "0xAA, 0x0F, 0x0A,      false, false",
                "0xF0, 0x0F, 0x00,      true,  false",
                "0xFF, 0x80, 0x80,      false, true"
        })
        void andAbsoluteYPerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (ADL)
            cpu.tick(); //Fetch argument 2 (ADH) and Y offset
            cpu.tick(); //Perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void andAbsoluteYUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, AND_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_IND_X A={0}, operand={1}")
        @CsvSource({
                // A      Value Expected   Z      N
                "0xAA, 0x0F, 0x0A,      false, false",
                "0xF0, 0x0F, 0x00,      true,  false",
                "0xFF, 0x80, 0x80,      false, true"
        })
        void andIndirectXPerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (zero-page address)
            cpu.tick(); //Add X offset
            cpu.tick(); //Fetch effective address low byte
            cpu.tick(); //Fetch effective address high byte
            cpu.tick(); //Perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void andIndirectXWrapsZeroPagePointer() {
            ram.write(0x8000, AND_IND_X.getId());
            ram.write(0x8001, 0xFE);

            ram.write(0x0003, 0x34);
            ram.write(0x0004, 0x12);
            ram.write(0x1234, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x0A, env.getA());
        }

        @ParameterizedTest(name = "AND_IND_Y A={0}, operand={1}")
        @CsvSource({
                // A      Value   Expected   Z      N
                "0xAA, 0x0F,   0x0A,      false, false",
                "0xF0, 0x0F,   0x00,      true,  false",
                "0xFF, 0x80,   0x80,      false, true"
        })
        void andIndirectYPerformsAndAndSetsFlags(int a, int value, int expected, boolean z, boolean n) {
            ram.write(0x8000, AND_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1
            cpu.tick(); //Turn argument 1 into pointer and fetch ADL
            cpu.tick(); //Fetch argument 2 (ADH) & add Y offset
            cpu.tick(); //Perform AND

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void andIndirectYUsesExtraCycleWhenPageCrosses() {
            ram.write(0x8000, AND_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0xAA, env.getA());

            cpu.tick();
            assertEquals(0x0A, env.getA());
        }
    }

    @Nested
    class ORA {
        @ParameterizedTest(name = "ORA_I A={0}, operand={1}")
        @CsvSource({
                // A      Operand  Expected  Z      N
                "0x00, 0x00,    0x00,     true,  false",
                "0x00, 0xFF,    0xFF,     false, true",
                "0xAA, 0x0F,    0xAF,     false, true",
                "0x02, 0x01,    0x03,     false, false"
        })
        void oraImmediatePerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); //Get opcode
            cpu.tick(); //Get immediate value and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraImmediateDoesNotExecuteUntilSecondTick() {
            ram.write(0x8000, ORA_I.getId());
            ram.write(0x8001, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);

            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
        }

        @ParameterizedTest(name = "ORA_Z A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z       N
                "0x00, 0x00,  0x00,     true,   false",
                "0x00, 0xFF,  0xFF,     false,  true",
                "0xAA, 0x0F,  0xAF,     false,  true",
                "0x02, 0x01,  0x03,     false,  false"
        })
        void oraZeroPagePerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); //Get opcode
            cpu.tick(); //Get argument - zero-page address
            cpu.tick(); //Fetch value from zero-page and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraZeroPageDoesNotExecuteUntilThirdTick() {
            ram.write(0x8000, ORA_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);

            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
        }

        @ParameterizedTest(name = "ORA_Z_X A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z      N
                "0x00, 0x00,  0x00,     true,  false",
                "0x00, 0xFF,  0xFF,     false, true",
                "0xAA, 0x0F,  0xAF,     false, true",
                "0x02, 0x01,  0x03,     false, false"
        })
        void oraZeroPageXPerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Get opcode
            cpu.tick(); //Get argument - zero-page address
            cpu.tick(); //Add X to zero-page address
            cpu.tick(); //Get offset value and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraZeroPageXDoesNotExecuteUntilFourthTick() {
            ram.write(0x8000, ORA_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
        }

        @ParameterizedTest(name = "ORA_ABS A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z       N
                "0x00, 0x00,  0x00,     true,   false",
                "0x00, 0xFF,  0xFF,     false,  true",
                "0xAA, 0x0F,  0xAF,     false,  true",
                "0x02, 0x01,  0x03,     false,  false"
        })
        void oraAbsolutePerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); //get opcode
            cpu.tick(); //Get argument 1 - ADL
            cpu.tick(); //Get argument 2 - ADH
            cpu.tick(); //Fetch value from AD and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void oraAbsoluteDoesNotExecuteUntilFourthTick() {
            ram.write(0x8000, ORA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "ORA_ABS_X A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z      N
                "0x00, 0x00,  0x00,     true,  false",
                "0x00, 0xFF,  0xFF,     false, true",
                "0xAA, 0x0F,  0xAF,     false, true",
                "0x02, 0x01,  0x03,     false, false"
        })
        void oraAbsoluteXPerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch operand 1 (ADL)
            cpu.tick(); //Fetch operand 2 (ADH) & add X
            cpu.tick(); //Fetch value at final address and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void oraAbsoluteXDoesNotExecuteUntilFourthTickWithoutPageCross() {
            ram.write(0x8000, ORA_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void oraAbsoluteXDoesNotExecuteUntilFifthTickWithPageCross() {
            ram.write(0x8000, ORA_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "ORA_ABS_Y A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected Z       N
                "0x00, 0x00,  0x00,     true,  false",
                "0x00, 0xFF,  0xFF,     false, true",
                "0xAA, 0x0F,  0xAF,     false, true",
                "0x02, 0x01,  0x03,     false, false"
        })
        void oraAbsoluteYPerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch operand 1 (ADL)
            cpu.tick(); //Fetch operand 2 (ADH) & add Y
            cpu.tick(); //Fetch value at final address and perform ORA

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void oraAbsoluteYDoesNotExecuteUntilFourthTickWithoutPageCross() {
            ram.write(0x8000, ORA_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void oraAbsoluteYDoesNotExecuteUntilFifthTickWithPageCross() {
            ram.write(0x8000, ORA_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "ORA_IND_X A={0}, operand={1}")
        @CsvSource({
                "0x00, 0x00, 0x00, true,  false",
                "0x00, 0xFF, 0xFF, false, true",
                "0xAA, 0x0F, 0xAF, false, true",
                "0x02, 0x01, 0x03, false, false"
        })
        void oraIndirectXPerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (zero-page address)
            cpu.tick(); //Add X to the retrieved address
            cpu.tick(); //Fetch effective address low byte
            cpu.tick(); //Fetch next effective address high byte
            cpu.tick(); //Perform ORA on the value at the final address

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraIndirectXDoesNotExecuteUntilSixthTick() {
            ram.write(0x8000, ORA_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setX(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "ORA_IND_Y A={0}, operand={1}")
        @CsvSource({
                // A      Value  Expected  Z       N
                "0x00, 0x00,  0x00,     true,   false",
                "0x00, 0xFF,  0xFF,     false,  true",
                "0xAA, 0x0F,  0xAF,     false,  true",
                "0x02, 0x01,  0x03,     false,  false"
        })
        void oraIndirectYPerformsOrAndSetsFlags(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, ORA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); //Fetch opcode
            cpu.tick(); //Fetch argument 1 (zero-page address) to address bus
            cpu.tick(); //Fetch value at addressed location to ADL
            cpu.tick(); //Fetch value at next location to ADH, add Y offset
            cpu.tick(); //Perform ORA on addressed location

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraIndirectYDoesNotExecuteUntilFifthTickWithoutPageCross() {
            ram.write(0x8000, ORA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void oraIndirectYDoesNotExecuteUntilSixthTickWithPageCross() {
            ram.write(0x8000, ORA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xA0);
            env.setY(0x05);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xA0, env.getA());

            cpu.tick();

            assertEquals(0xAF, env.getA());
            assertEquals(0x8002, env.getPC());
        }
    }

    @Nested
    class EOR {
        @ParameterizedTest(name = "EOR_I A={0}, M={1}")
        @CsvSource({
                // A      Operand  Expected  Z      N
                "0xAA, 0x0F,    0xA5,     false, true",
                "0xFF, 0xFF,    0x00,     true,  false",
                "0x7F, 0xFF,    0x80,     false, true"
        })
        void eorImmediate(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "EOR_Z A={0}, M={1}")
        @CsvSource({
                // A      Value  Expected  Z      N
                "0xAA, 0x0F,  0xA5,     false, true",
                "0xFF, 0xFF,  0x00,     true,  false",
                "0x7F, 0xFF,  0x80,     false, true"
        })
        void eorZeroPage(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "EOR_Z_X A={0}, M={1}")
        @CsvSource({
                // A      Value  Expected  Z      N
                "0xAA, 0x0F,  0xA5,     false, true",
                "0xFF, 0xFF,  0x00,     true,  false",
                "0x7F, 0xFF,  0x80,     false, true"
        })
        void eorZeroPageX(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "EOR_ABS A={0}, M={1}")
        @CsvSource({
                // A      Value  Expected  Z       N
                "0xAA, 0x0F,  0xA5,     false,  true",
                "0xFF, 0xFF,  0x00,     true,   false",
                "0x7F, 0xFF,  0x80,     false,  true"
        })
        void eorAbsolute(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "EOR_ABS_X A={0}, M={1}")
        @CsvSource({
                // A       Value   Expected  Z      N
                "0xAA,  0x0F,   0xA5,     false, true",
                "0xFF,  0xFF,   0x00,     true,  false",
                "0x7F,  0xFF,   0x80,     false, true"
        })
        void eorAbsoluteX(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void eorAbsoluteXDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, EOR_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X

            assertEquals(0xAA, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0xAA, env.getA());

            cpu.tick(); // read operand, EOR, set flags

            assertEquals(0xA5, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "EOR_ABS_Y A={0}, M={1}")
        @CsvSource({
                // A       Value   Expected   Z      N
                "0xAA,  0x0F,   0xA5,      false, true",
                "0xFF,  0xFF,   0x00,      true,  false",
                "0x7F,  0xFF,   0x80,      false, true"
        })
        void eorAbsoluteY(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void eorAbsoluteYDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, EOR_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y

            assertEquals(0xAA, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0xAA, env.getA());

            cpu.tick(); // read operand, EOR, set flags

            assertEquals(0xA5, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "EOR_IND_X A={0}, M={1}")
        @CsvSource({
                // A      Value  Expected  Z       N
                "0xAA, 0x0F,  0xA5,     false,  true",
                "0xFF, 0xFF,  0x00,     true,   false",
                "0x7F, 0xFF,  0x80,     false,  true"
        })
        void eorIndirectX(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // add X to pointer address
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "EOR_IND_Y A={0}, M={1}")
        @CsvSource({
                // A       Value  Expected  Z      N
                "0xAA,  0x0F,  0xA5,     false, true",
                "0xFF,  0xFF,  0x00,     true,  false",
                "0x7F,  0xFF,  0x80,     false, true"
        })
        void eorIndirectY(int a, int operand, int expected, boolean z, boolean n) {
            ram.write(0x8000, EOR_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte, add Y
            cpu.tick(); // read operand, EOR, set flags

            assertEquals(expected, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void eorIndirectYDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, EOR_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);
            ram.write(0x1304, 0x0F);

            env.setPC(0x8000);
            env.setA(0xAA);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte, add Y

            assertEquals(0xAA, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0xAA, env.getA());

            cpu.tick(); // read operand, EOR, set flags

            assertEquals(0xA5, env.getA());
            assertEquals(0x8002, env.getPC());
        }
    }

    @Nested
    class SBC {
        @ParameterizedTest(name = "SBC_I A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Op    C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcImmediate(int a, int operand, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void sbcImmediateDoesNotExecuteUntilSecondTick() {
            ram.write(0x8000, SBC_I.getId());
            ram.write(0x8001, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setCarry(true);

            cpu.tick(); // fetch opcode

            assertEquals(0x10, env.getA());

            cpu.tick(); // fetch operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "SBC_Z A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcZeroPage(int a, int operand, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void sbcZeroPageDoesNotExecuteUntilThirdTick() {
            ram.write(0x8000, SBC_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "SBC_Z_X A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcZeroPageX(int a, int value, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void sbcZeroPageXDoesNotExecuteUntilFourthTick() {
            ram.write(0x8000, SBC_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setX(0x05);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "SBC_ABS A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcAbsolute(int a, int value, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void sbcAbsoluteDoesNotExecuteUntilFourthTick() {
            ram.write(0x8000, SBC_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "SBC_ABS_X A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcAbsoluteX(int a, int value, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void sbcAbsoluteXDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, SBC_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setX(0x05);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X

            assertEquals(0x10, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "SBC_ABS_Y A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcAbsoluteY(int a, int value, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void sbcAbsoluteYDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, SBC_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setY(0x05);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y

            assertEquals(0x10, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "SBC_IND_X A={0}, M={1}, C={2}")
        @CsvSource({
                // A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcIndirectX(int a, int operand, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // add X to pointer address
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void sbcIndirectXDoesNotExecuteUntilSixthTick() {
            ram.write(0x8000, SBC_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setX(0x05);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // add X to pointer address
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "SBC_IND_Y A={0}, M={1}, C={2}")
        @CsvSource({
                //  A     Val   C-in   Result  V      Z      N      C-out
                "0x10, 0x05, true,  0x0B,   false, false, false, true",
                "0x10, 0x05, false, 0x0A,   false, false, false, true",
                "0x00, 0x01, true,  0xFF,   false, false, true,  false",
                "0x80, 0x01, true,  0x7F,   true,  false, false, true",
                "0x05, 0x05, true,  0x00,   false, true,  false, true"
        })
        void sbcIndirectY(int a, int value, boolean carryIn, int expected,
        boolean v, boolean z, boolean n, boolean c) {
            ram.write(0x8000, SBC_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);
            env.setCarry(carryIn);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte, add Y
            cpu.tick(); // read operand, SBC, set flags

            assertEquals(expected, env.getA());
            assertEquals(v, env.getV());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void sbcIndirectYDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, SBC_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);
            ram.write(0x1304, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setY(0x05);
            env.setCarry(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte, add Y

            assertEquals(0x10, env.getA());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0x10, env.getA());

            cpu.tick(); // read operand, SBC, set flags

            assertEquals(0x0B, env.getA());
            assertEquals(0x8002, env.getPC());
        }
    }

    @Nested
    class JMP {
        @Test
        void jmpAbsoluteLoadsPcWithTargetAddress() {
            ram.write(0x8000, JMP_ABS.getId());
            ram.write(0x8001, 0x34); // low byte
            ram.write(0x8002, 0x12); // high byte

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low target byte
            cpu.tick(); // fetch high target byte, set PC

            assertEquals(0x1234, env.getPC());
        }
        @Test
        void jmpAbsoluteDoesNotSetPcUntilThirdTick() {
            ram.write(0x8000, JMP_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode

            assertEquals(0x8001, env.getPC());

            cpu.tick(); // fetch low target byte

            assertEquals(0x8002, env.getPC());

            cpu.tick(); // fetch high target byte, set PC

            assertEquals(0x1234, env.getPC());
        }

        @Test
        void jmpIndirectLoadsPcFromPointerAddress() {
            ram.write(0x8000, JMP_I.getId());
            ram.write(0x8001, 0x34); // pointer low byte
            ram.write(0x8002, 0x12); // pointer high byte

            ram.write(0x1234, 0xCD); // target low byte
            ram.write(0x1235, 0xAB); // target high byte

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch pointer low byte
            cpu.tick(); // fetch pointer high byte
            cpu.tick(); // read target low byte from pointer address
            cpu.tick(); // read target high byte from pointer+1, set PC

            assertEquals(0xABCD, env.getPC());
        }

        @Test
        void jmpIndirectSetsPcLowThenHigh() {
            ram.write(0x8000, JMP_I.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);

            ram.write(0x1234, 0xCD);
            ram.write(0x1235, 0xAB);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            assertEquals(0x8001, env.getPC());

            cpu.tick(); // fetch pointer low byte
            assertEquals(0x8002, env.getPC());

            cpu.tick(); // fetch pointer high byte
            assertEquals(0x8003, env.getPC());

            cpu.tick(); // read target low byte into PCL
            assertEquals(0x80CD, env.getPC());

            cpu.tick(); // read target high byte into PCH
            assertEquals(0xABCD, env.getPC());
        }

        @Test
        void jmpIndirectEmulatesPageBoundaryBug() {
            ram.write(0x8000, JMP_I.getId());
            ram.write(0x8001, 0xFF); // pointer low byte
            ram.write(0x8002, 0x12); // pointer high byte => pointer = $12FF

            ram.write(0x12FF, 0xCD); // target low byte
            ram.write(0x1200, 0xAB); // target high byte due to 6502 bug

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch pointer low byte
            cpu.tick(); // fetch pointer high byte
            cpu.tick(); // read target low byte from $12FF
            cpu.tick(); // read target high byte from $1200, set PC

            assertEquals(0xABCD, env.getPC());
        }
    }

    @Nested
    class CMP {
        @ParameterizedTest(name = "CMP_I A={0}, M={1}")
        @CsvSource({
                // A,   Op,    Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpImmediateComparesAccumulatorAndSetsFlags(int a, int operand,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "CMP_Z A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpZeroPageComparesAccumulatorAndSetsFlags(int a, int value,
                                                        boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, value);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "CMP_Z_X A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpZeroPageXComparesAccumulatorAndSetsFlags(int a, int value,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "CMP_ABS A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpAbsoluteComparesAccumulatorAndSetsFlags(int a, int value,
                                                        boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setA(a);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "CMP_ABS_X A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpAbsoluteXComparesAccumulatorAndSetsFlags(int a, int value,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "CMP_ABS_Y A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpAbsoluteYComparesAccumulatorAndSetsFlags(int a, int value,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "CMP_IND_X A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpIndirectXComparesAccumulatorAndSetsFlags(int a, int value,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34);
            ram.write(0x004A, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // add X to pointer address
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "CMP_IND_Y A={0}, M={1}")
        @CsvSource({
                // A,  Value,  Z,     N,     C
                "0x00, 0x00, true,  false, true",
                "0x10, 0x05, false, false, true",
                "0x05, 0x10, false, true,  false",
                "0x80, 0x80, true,  false, true"
        })
        void cmpIndirectYComparesAccumulatorAndSetsFlags(int a, int value,
                                                         boolean z, boolean n, boolean c) {
            ram.write(0x8000, CMP_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setA(a);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer operand
            cpu.tick(); // read effective address low byte
            cpu.tick(); // read effective address high byte, add Y
            cpu.tick(); // read operand, CMP, set flags

            assertEquals(a, env.getA());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(c, env.getCarry());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void cmpAbsoluteXDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, CMP_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x05);

            env.setPC(0x8000);
            env.setA(0x10);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X

            assertFalse(env.getZ());
            assertFalse(env.getN());
            assertFalse(env.getCarry());

            cpu.tick(); // dummy read / page-cross fix

            assertFalse(env.getZ());
            assertFalse(env.getN());
            assertFalse(env.getCarry());

            cpu.tick(); // read operand, CMP, set flags

            assertFalse(env.getZ());
            assertFalse(env.getN());
            assertTrue(env.getCarry());
            assertEquals(0x8003, env.getPC());
        }
    }

    @Nested
    class LDX {
        @ParameterizedTest(name = "LDX_I M={0}")
        @CsvSource({
                // Op,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldxImmediateLoadsXAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDX_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand, load X, set flags

            assertEquals(operand & 0xFF, env.getX());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldxImmediateDoesNotLoadUntilSecondTick() {
            ram.write(0x8000, LDX_I.getId());
            ram.write(0x8001, 0x20);

            env.setPC(0x8000);
            env.setX(0x10);

            cpu.tick(); // fetch opcode

            assertEquals(0x10, env.getX());

            cpu.tick(); // fetch operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8002, env.getPC());
        }
        @ParameterizedTest(name = "LDX_Z M={0}")
        @CsvSource({
                // Val,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldxZeroPageLoadsXAndSetsFlags(int value, boolean z, boolean n) {
            ram.write(0x8000, LDX_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, value);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // read operand, load X, set flags

            assertEquals(value & 0xFF, env.getX());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldxZeroPageDoesNotLoadUntilThirdTick() {
            ram.write(0x8000, LDX_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x20);

            env.setPC(0x8000);
            env.setX(0x10);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x10, env.getX());

            cpu.tick(); // read operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8002, env.getPC());
        }
        @ParameterizedTest(name = "LDX_Z_Y M={0}")
        @CsvSource({
                // Val,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldxZeroPageYLoadsXAndSetsFlags(int value, boolean z, boolean n) {
            ram.write(0x8000, LDX_Z_Y.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, value);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address
            cpu.tick(); // read operand, load X, set flags

            assertEquals(value & 0xFF, env.getX());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldxZeroPageYWrapsWithinZeroPage() {
            ram.write(0x8000, LDX_Z_Y.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x20);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address, wrapping within zero-page
            cpu.tick(); // read operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldxZeroPageYDoesNotLoadUntilFourthTick() {
            ram.write(0x8000, LDX_Z_Y.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x20);

            env.setPC(0x8000);
            env.setX(0x10);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address

            assertEquals(0x10, env.getX());

            cpu.tick(); // read operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8002, env.getPC());
        }
        @ParameterizedTest(name = "LDX_ABS M={0}")
        @CsvSource({
                // Val,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldxAbsoluteLoadsXAndSetsFlags(int value, boolean z, boolean n) {
            ram.write(0x8000, LDX_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // read operand, load X, set flags

            assertEquals(value & 0xFF, env.getX());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldxAbsoluteDoesNotLoadUntilFourthTick() {
            ram.write(0x8000, LDX_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x20);

            env.setPC(0x8000);
            env.setX(0x10);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x10, env.getX());

            cpu.tick(); // read operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8003, env.getPC());
        }
        @ParameterizedTest(name = "LDX_ABS_Y M={0}")
        @CsvSource({
                // Val,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldxAbsoluteYLoadsXAndSetsFlags(int value, boolean z, boolean n) {
            ram.write(0x8000, LDX_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, value);

            env.setPC(0x8000);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // read operand, load X, set flags

            assertEquals(value & 0xFF, env.getX());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldxAbsoluteYDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, LDX_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x20);

            env.setPC(0x8000);
            env.setX(0x10);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y

            assertEquals(0x10, env.getX());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0x10, env.getX());

            cpu.tick(); // read operand, load X, set flags

            assertEquals(0x20, env.getX());
            assertEquals(0x8003, env.getPC());
        }
    }

    @Nested
    class LDY {
        @ParameterizedTest(name = "LDY_I M={0}")
        @CsvSource({
                // M,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldyImmediateLoadsYAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDY_I.getId());
            ram.write(0x8001, operand);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch operand, load Y, set flags

            assertEquals(operand & 0xFF, env.getY());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldyImmediateDoesNotLoadUntilSecondTick() {
            ram.write(0x8000, LDY_I.getId());
            ram.write(0x8001, 0x20);

            env.setPC(0x8000);
            env.setY(0x10);

            cpu.tick(); // fetch opcode

            assertEquals(0x10, env.getY());

            cpu.tick(); // fetch operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "LDY_Z M={0}")
        @CsvSource({
                // M,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldyZeroPageLoadsYAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDY_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, operand);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // read operand, load Y, set flags

            assertEquals(operand & 0xFF, env.getY());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldyZeroPageDoesNotLoadUntilThirdTick() {
            ram.write(0x8000, LDY_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x20);

            env.setPC(0x8000);
            env.setY(0x10);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x10, env.getY());

            cpu.tick(); // read operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "LDY_Z_X M={0}")
        @CsvSource({
                // M,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldyZeroPageXLoadsYAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDY_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, operand);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address
            cpu.tick(); // read operand, load Y, set flags

            assertEquals(operand & 0xFF, env.getY());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldyZeroPageXWrapsWithinZeroPage() {
            ram.write(0x8000, LDY_Z_X.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x20);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address, wrapping within zero-page
            cpu.tick(); // read operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void ldyZeroPageXDoesNotLoadUntilFourthTick() {
            ram.write(0x8000, LDY_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x20);

            env.setPC(0x8000);
            env.setY(0x10);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address

            assertEquals(0x10, env.getY());

            cpu.tick(); // read operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "LDY_ABS M={0}")
        @CsvSource({
                // M,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldyAbsoluteLoadsYAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDY_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, operand);

            env.setPC(0x8000);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // read operand, load Y, set flags

            assertEquals(operand & 0xFF, env.getY());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldyAbsoluteDoesNotLoadUntilFourthTick() {
            ram.write(0x8000, LDY_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x20);

            env.setPC(0x8000);
            env.setY(0x10);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x10, env.getY());

            cpu.tick(); // read operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "LDY_ABS_X M={0}")
        @CsvSource({
                // M,    Z,     N
                "0x20, false, false",
                "0x00, true,  false",
                "0x80, false, true",
                "0xFF, false, true"
        })
        void ldyAbsoluteXLoadsYAndSetsFlags(int operand, boolean z, boolean n) {
            ram.write(0x8000, LDY_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, operand);

            env.setPC(0x8000);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X
            cpu.tick(); // read operand, load Y, set flags

            assertEquals(operand & 0xFF, env.getY());
            assertEquals(z, env.getZ());
            assertEquals(n, env.getN());
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void ldyAbsoluteXDoesNotExecuteBeforeExtraCycleWhenPageCrosses() {
            ram.write(0x8000, LDY_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x20);

            env.setPC(0x8000);
            env.setY(0x10);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add X

            assertEquals(0x10, env.getY());

            cpu.tick(); // dummy read / page-cross fix

            assertEquals(0x10, env.getY());

            cpu.tick(); // read operand, load Y, set flags

            assertEquals(0x20, env.getY());
            assertEquals(0x8003, env.getPC());
        }
    }

    @Nested
    class STA {
        @Test
        void staZeroPageStoresAccumulatorAtZeroPageAddress() {
            ram.write(0x8000, STA_Z.getId());
            ram.write(0x8001, 0x44);

            env.setPC(0x8000);
            env.setA(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // write A to zero-page address

            assertEquals(0x20, ram.read(0x0044));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staZeroPageDoesNotStoreUntilThirdTick() {
            ram.write(0x8000, STA_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x00);

            env.setPC(0x8000);
            env.setA(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x00, ram.read(0x0044));

            cpu.tick(); // write A to zero-page address

            assertEquals(0x20, ram.read(0x0044));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staZeroPageDoesNotAffectFlags() {
            ram.write(0x8000, STA_Z.getId());
            ram.write(0x8001, 0x44);

            env.setPC(0x8000);
            env.setA(0x00);

            env.setZ(false);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // write A to zero-page address

            assertEquals(0x00, ram.read(0x0044));
            assertFalse(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());
        }

        @Test
        void staZeroPageXStoresAAtZeroPageAddressPlusX() {
            ram.write(0x8000, STA_Z_X.getId());
            ram.write(0x8001, 0x40);

            env.setPC(0x8000);
            env.setA(0xAB);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // ADL_FROM_PC
            cpu.tick(); // ADL_PLUS_X
            cpu.tick(); // MEM_FROM_A

            assertEquals(0xAB, ram.read(0x0045));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staZeroPageXWrapsInZeroPage() {
            ram.write(0x8000, STA_Z_X.getId());
            ram.write(0x8001, 0xFF);

            env.setPC(0x8000);
            env.setA(0x42);
            env.setX(0x02);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x42, ram.read(0x0001));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staZeroPageXDoesNotChangeAOrX() {
            ram.write(0x8000, STA_Z_X.getId());
            ram.write(0x8001, 0x20);

            env.setPC(0x8000);
            env.setA(0x77);
            env.setX(0x10);

            cpu.tick();
            cpu.tick();
            cpu.tick();
            cpu.tick();

            assertEquals(0x77, env.getA());
            assertEquals(0x10, env.getX());
            assertEquals(0x77, ram.read(0x0030));
        }

        @Test
        void staAbsoluteStoresAccumulatorAtAbsoluteAddress() {
            ram.write(0x8000, STA_ABS.getId());
            ram.write(0x8001, 0x34); // low byte
            ram.write(0x8002, 0x12); // high byte

            env.setPC(0x8000);
            env.setA(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // write A to absolute address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteDoesNotStoreUntilFourthTick() {
            ram.write(0x8000, STA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x00);

            env.setPC(0x8000);
            env.setA(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x00, ram.read(0x1234));

            cpu.tick(); // write A to absolute address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteDoesNotAffectFlags() {
            ram.write(0x8000, STA_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);

            env.setPC(0x8000);
            env.setA(0x00);

            env.setZ(false);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // write A to absolute address

            assertEquals(0x00, ram.read(0x1234));
            assertFalse(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());
        }

        @ParameterizedTest(name = "STA_ABS_X A={0}, base=${2}{1}, X={3}")
        @CsvSource({
              // A,    Low,  High, X,    Address,  Value
                "0x20, 0x34, 0x12, 0x05, 0x1239,   0x99",
                "0x00, 0x10, 0x20, 0x00, 0x2010,   0x77",
                "0x80, 0xFE, 0x12, 0x01, 0x12FF,   0x55",
                "0xFF, 0xFF, 0x12, 0x05, 0x1304,   0x00"
        })
        void staAbsoluteXStoresAccumulatorAtAbsoluteAddressOffsetByX(int accumulator,
                                                                     int lowAddressByte,
                                                                     int highAddressByte,
                                                                     int x,
                                                                     int address,
                                                                     int value) {
            ram.write(0x8000, STA_ABS_X.getId());
            ram.write(0x8001, lowAddressByte);
            ram.write(0x8002, highAddressByte);
            ram.write(address, value);

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setX(x);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // add X to absolute address / dummy read
            cpu.tick(); // store A at effective address

            assertEquals(accumulator & 0xFF, ram.read(address));
            assertEquals(accumulator & 0xFF, env.getA());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteXDoesNotStoreUntilFifthTickWhenNoPageCrosses() {
            ram.write(0x8000, STA_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // add X to absolute address / dummy read

            assertEquals(0x99, ram.read(0x1239));

            cpu.tick(); // store A at $1234 + X

            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteXDoesNotStoreUntilFifthTickWhenPageCrosses() {
            ram.write(0x8000, STA_ABS_X.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // add X to absolute address, carrying into high byte / dummy read

            assertEquals(0x99, ram.read(0x1304));

            cpu.tick(); // store A at $12FF + X

            assertEquals(0x20, ram.read(0x1304));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteXDoesNotStoreToUnindexedBaseAddress() {
            ram.write(0x8000, STA_ABS_X.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x11);
            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // add X to absolute address / dummy read
            cpu.tick(); // store A at indexed address

            assertEquals(0x11, ram.read(0x1234));
            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "STA_ABS_Y A={0}, base=${2}{1}, Y={3}")
        @CsvSource({
              // A,    Low,  High, Y,    Address,   Value
                "0x20, 0x34, 0x12, 0x05, 0x1239,    0x99",
                "0x00, 0x10, 0x20, 0x00, 0x2010,    0x77",
                "0x80, 0xFE, 0x12, 0x01, 0x12FF,    0x55",
                "0xFF, 0xFF, 0x12, 0x05, 0x1304,    0x00"
        })
        void staAbsoluteYStoresAccumulatorAtAbsoluteAddressOffsetByY(int accumulator,
                                                                     int lowAddressByte,
                                                                     int highAddressByte,
                                                                     int y,
                                                                     int expectedAddress,
                                                                     int value) {
            ram.write(0x8000, STA_ABS_Y.getId());
            ram.write(0x8001, lowAddressByte);
            ram.write(0x8002, highAddressByte);
            ram.write(expectedAddress, value);

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setY(y);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // dummy read / indexing cycle
            cpu.tick(); // store A at effective address

            assertEquals(accumulator & 0xFF, ram.read(expectedAddress));
            assertEquals(accumulator & 0xFF, env.getA());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteYDoesNotStoreUntilFifthTickWhenNoPageCrosses() {
            ram.write(0x8000, STA_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // dummy read / indexing cycle

            assertEquals(0x99, ram.read(0x1239));

            cpu.tick(); // store A at $1234 + Y

            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteYDoesNotStoreUntilFifthTickWhenPageCrosses() {
            ram.write(0x8000, STA_ABS_Y.getId());
            ram.write(0x8001, 0xFF);
            ram.write(0x8002, 0x12);
            ram.write(0x1304, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // dummy read / indexing cycle

            assertEquals(0x99, ram.read(0x1304));

            cpu.tick(); // store A at $12FF + Y

            assertEquals(0x20, ram.read(0x1304));
            assertEquals(0x8003, env.getPC());
        }

        @Test
        void staAbsoluteYDoesNotStoreToUnindexedBaseAddress() {
            ram.write(0x8000, STA_ABS_Y.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x11);
            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte, add Y
            cpu.tick(); // dummy read / indexing cycle
            cpu.tick(); // store A at indexed address

            assertEquals(0x11, ram.read(0x1234));
            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8003, env.getPC());
        }

        @ParameterizedTest(name = "STA_IND_X A={0}, zp={1}, X={2}")
        @CsvSource({
                // A,  Operand, X,    Address Low,  Address High,  Effective Address, Value
                "0x20, 0x44,    0x05, 0x0049,       0x004A,        0x1234,            0x99",
                "0x00, 0x20,    0x00, 0x0020,       0x0021,        0x5678,            0x77",
                "0x80, 0xFE,    0x05, 0x0003,       0x0004,        0x2000,            0x55",
                "0xFF, 0xFA,    0x05, 0x00FF,       0x0000,        0x1304,            0x00"
        })
        void staIndirectXStoresAccumulatorAtIndexedIndirectAddress(int accumulator,
                                                                   int zeroPageOperand,
                                                                   int x,
                                                                   int pointerLowAddress,
                                                                   int pointerHighAddress,
                                                                   int effectiveAddress,
                                                                   int value) {
            ram.write(0x8000, STA_IND_X.getId());
            ram.write(0x8001, zeroPageOperand);

            ram.write(pointerLowAddress, effectiveAddress & 0xFF);
            ram.write(pointerHighAddress, (effectiveAddress >> 8) & 0xFF);
            ram.write(effectiveAddress, value);

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setX(x);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base pointer
            cpu.tick(); // add X to zero-page pointer
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte
            cpu.tick(); // store A at effective address

            assertEquals(accumulator & 0xFF, ram.read(effectiveAddress));
            assertEquals(accumulator & 0xFF, env.getA());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectXDoesNotStoreUntilSixthTick() {
            ram.write(0x8000, STA_IND_X.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0049, 0x34); // $44 + X(5)
            ram.write(0x004A, 0x12);

            ram.write(0x1234, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base pointer
            cpu.tick(); // add X to zero-page pointer
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte

            assertEquals(0x99, ram.read(0x1234));

            cpu.tick(); // store A at effective address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectXWrapsZeroPagePointerWhenAddingX() {
            ram.write(0x8000, STA_IND_X.getId());
            ram.write(0x8001, 0xFE);

            ram.write(0x0003, 0x34); // ($FE + X(5)) & $FF = $03
            ram.write(0x0004, 0x12);

            ram.write(0x1234, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base pointer
            cpu.tick(); // add X to zero-page pointer, wrapping within zero-page
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte
            cpu.tick(); // store A at effective address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectXWrapsHighBytePointerWithinZeroPage() {
            ram.write(0x8000, STA_IND_X.getId());
            ram.write(0x8001, 0xFA);

            ram.write(0x00FF, 0x04); // ($FA + X(5)) & $FF = $FF
            ram.write(0x0000, 0x13); // high byte wraps from $00FF to $0000

            ram.write(0x1304, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base pointer
            cpu.tick(); // add X to zero-page pointer, wrapping within zero-page
            cpu.tick(); // fetch effective address low byte from $00FF
            cpu.tick(); // fetch effective address high byte from $0000
            cpu.tick(); // store A at effective address

            assertEquals(0x20, ram.read(0x1304));
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "STA_IND_Y A={0}, zp={1}, Y={4}")
        @CsvSource({
              // A,    Address,  Address Low, Address High, Y,    Expected Address, Value
                "0x20, 0x44,     0x34,        0x12,         0x05, 0x1239,           0x99",
                "0x00, 0x20,     0x78,        0x56,         0x00, 0x5678,           0x77",
                "0x80, 0x44,     0xFE,        0x12,         0x01, 0x12FF,           0x55",
                "0xFF, 0x44,     0xFF,        0x12,         0x05, 0x1304,           0x00"
        })
        void staIndirectYStoresAccumulatorAtIndirectIndexedAddress(int accumulator,
                                                                   int address,
                                                                   int addressLowByte,
                                                                   int addressHighByte,
                                                                   int y,
                                                                   int expectedAddress,
                                                                   int value) {
            ram.write(0x8000, STA_IND_Y.getId());
            ram.write(0x8001, address);

            ram.write(address, addressLowByte);
            ram.write((address + 1) & 0xFF, addressHighByte);

            ram.write(expectedAddress, value);

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setY(y);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte, add Y
            cpu.tick(); // dummy read / indexing cycle
            cpu.tick(); // store A at effective address

            assertEquals(accumulator & 0xFF, ram.read(expectedAddress));
            assertEquals(accumulator & 0xFF, env.getA());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectYDoesNotStoreUntilSixthTickWhenNoPageCrosses() {
            ram.write(0x8000, STA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0x34);
            ram.write(0x0045, 0x12);

            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte, add Y
            cpu.tick(); // dummy read / indexing cycle

            assertEquals(0x99, ram.read(0x1239));

            cpu.tick(); // store A at $1234 + Y

            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectYDoesNotStoreUntilSixthTickWhenPageCrosses() {
            ram.write(0x8000, STA_IND_Y.getId());
            ram.write(0x8001, 0x44);

            ram.write(0x0044, 0xFF);
            ram.write(0x0045, 0x12);

            ram.write(0x1304, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer
            cpu.tick(); // fetch effective address low byte
            cpu.tick(); // fetch effective address high byte, add Y
            cpu.tick(); // dummy read / indexing cycle

            assertEquals(0x99, ram.read(0x1304));

            cpu.tick(); // store A at $12FF + Y

            assertEquals(0x20, ram.read(0x1304));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void staIndirectYWrapsHighBytePointerWithinZeroPage() {
            ram.write(0x8000, STA_IND_Y.getId());
            ram.write(0x8001, 0xFF);

            ram.write(0x00FF, 0x34);
            ram.write(0x0000, 0x12); // high byte wraps from $00FF to $0000

            ram.write(0x1239, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page pointer
            cpu.tick(); // fetch effective address low byte from $00FF
            cpu.tick(); // fetch effective address high byte from $0000, add Y
            cpu.tick(); // dummy read / indexing cycle
            cpu.tick(); // store A at effective address

            assertEquals(0x20, ram.read(0x1239));
            assertEquals(0x8002, env.getPC());
        }
    }

    @Nested
    class STX {
        @ParameterizedTest(name = "STX_Z X={0}")
        @CsvSource({
              // X,    Value
                "0x20, 0x99",
                "0x00, 0x77",
                "0x80, 0x55",
                "0xFF, 0x00"
        })
        void stxZeroPageStoresX(int x, int value) {
            ram.write(0x8000, STX_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, value);

            env.setPC(0x8000);
            env.setX(x);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // store X at zero-page address

            assertEquals(x & 0xFF, ram.read(0x0044));
            assertEquals(x & 0xFF, env.getX());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void stxZeroPageDoesNotStoreUntilThirdTick() {
            ram.write(0x8000, STX_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x99);

            env.setPC(0x8000);
            env.setX(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x99, ram.read(0x0044));

            cpu.tick(); // store X at zero-page address

            assertEquals(0x20, ram.read(0x0044));
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "STX_Z_Y X={0}, zp={1}, Y={2}")
        @CsvSource({
              // X,    Z-Address, Y,    Address,  Value
                "0x20, 0x44,      0x05, 0x0049,   0x99",
                "0x00, 0x20,      0x00, 0x0020,   0x77",
                "0x80, 0xFE,      0x01, 0x00FF,   0x55",
                "0xFF, 0xFE,      0x05, 0x0003,   0x00"
        })
        void stxZeroPageYStoresXAtZeroPageAddressOffsetByY(int x,
                                                           int zeroPageBaseAddress,
                                                           int y,
                                                           int address,
                                                           int value) {
            ram.write(0x8000, STX_Z_Y.getId());
            ram.write(0x8001, zeroPageBaseAddress);
            ram.write(address, value);

            env.setPC(0x8000);
            env.setX(x);
            env.setY(y);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address
            cpu.tick(); // store X at indexed zero-page address

            assertEquals(x & 0xFF, ram.read(address));
            assertEquals(x & 0xFF, env.getX());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void stxZeroPageYWrapsWithinZeroPage() {
            ram.write(0x8000, STX_Z_Y.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x99);

            env.setPC(0x8000);
            env.setX(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address, wrapping within zero-page
            cpu.tick(); // store X at indexed zero-page address

            assertEquals(0x20, ram.read(0x0003));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void stxZeroPageYDoesNotStoreUntilFourthTick() {
            ram.write(0x8000, STX_Z_Y.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x99);

            env.setPC(0x8000);
            env.setX(0x20);
            env.setY(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add Y to zero-page address

            assertEquals(0x99, ram.read(0x0049));

            cpu.tick(); // store X at indexed zero-page address

            assertEquals(0x20, ram.read(0x0049));
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "STX_ABS X={0}")
        @CsvSource({
              // X,    Value
                "0x20, 0x99",
                "0x00, 0x77",
                "0x80, 0x55",
                "0xFF, 0x00"
        })
        void stxAbsoluteStoresX(int x, int value) {
            ram.write(0x8000, STX_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setX(x);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // store X at absolute address

            assertEquals(x & 0xFF, ram.read(0x1234));
            assertEquals(x & 0xFF, env.getX());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8003, env.getPC());
        }

        @Test
        void stxAbsoluteDoesNotStoreUntilFourthTick() {
            ram.write(0x8000, STX_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x99);

            env.setPC(0x8000);
            env.setX(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x99, ram.read(0x1234));

            cpu.tick(); // store X at absolute address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8003, env.getPC());
        }
    }

    @Nested
    class STY {
        @ParameterizedTest(name = "STY_Z Y={0}")
        @CsvSource({
              // Y,    Value
                "0x20, 0x99",
                "0x00, 0x77",
                "0x80, 0x55",
                "0xFF, 0x00"
        })
        void styZeroPageStoresY(int y, int initialMemoryValue) {
            ram.write(0x8000, STY_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, initialMemoryValue);

            env.setPC(0x8000);
            env.setY(y);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address
            cpu.tick(); // store Y at zero-page address

            assertEquals(y & 0xFF, ram.read(0x0044));
            assertEquals(y & 0xFF, env.getY());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void styZeroPageDoesNotStoreUntilThirdTick() {
            ram.write(0x8000, STY_Z.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0044, 0x99);

            env.setPC(0x8000);
            env.setY(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page address

            assertEquals(0x99, ram.read(0x0044));

            cpu.tick(); // store Y at zero-page address

            assertEquals(0x20, ram.read(0x0044));
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "STY_Z_X Y={0}, zp={1}, X={2}")
        @CsvSource({
              // Y,    Z-Base,  X,    Address,   Value
                "0x20, 0x44,    0x05, 0x0049,    0x99",
                "0x00, 0x20,    0x00, 0x0020,    0x77",
                "0x80, 0xFE,    0x01, 0x00FF,    0x55",
                "0xFF, 0xFE,    0x05, 0x0003,    0x00"
        })
        void styZeroPageXStoresYAtZeroPageAddressOffsetByX(int y,
                                                           int zeroPageBaseAddress,
                                                           int x,
                                                           int address,
                                                           int value) {
            ram.write(0x8000, STY_Z_X.getId());
            ram.write(0x8001, zeroPageBaseAddress);
            ram.write(address, value);

            env.setPC(0x8000);
            env.setY(y);
            env.setX(x);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address
            cpu.tick(); // store Y at indexed zero-page address

            assertEquals(y & 0xFF, ram.read(address));
            assertEquals(y & 0xFF, env.getY());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8002, env.getPC());
        }

        @Test
        void styZeroPageXWrapsWithinZeroPage() {
            ram.write(0x8000, STY_Z_X.getId());
            ram.write(0x8001, 0xFE);
            ram.write(0x0003, 0x99);

            env.setPC(0x8000);
            env.setY(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address, wrapping within zero-page
            cpu.tick(); // store Y at indexed zero-page address

            assertEquals(0x20, ram.read(0x0003));
            assertEquals(0x8002, env.getPC());
        }

        @Test
        void styZeroPageXDoesNotStoreUntilFourthTick() {
            ram.write(0x8000, STY_Z_X.getId());
            ram.write(0x8001, 0x44);
            ram.write(0x0049, 0x99);

            env.setPC(0x8000);
            env.setY(0x20);
            env.setX(0x05);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch zero-page base address
            cpu.tick(); // add X to zero-page address

            assertEquals(0x99, ram.read(0x0049));

            cpu.tick(); // store Y at indexed zero-page address

            assertEquals(0x20, ram.read(0x0049));
            assertEquals(0x8002, env.getPC());
        }

        @ParameterizedTest(name = "STY_ABS Y={0}")
        @CsvSource({
              // Y,    Value
                "0x20, 0x99",
                "0x00, 0x77",
                "0x80, 0x55",
                "0xFF, 0x00"
        })
        void styAbsoluteStoresY(int y, int value) {
            ram.write(0x8000, STY_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, value);

            env.setPC(0x8000);
            env.setY(y);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte
            cpu.tick(); // store Y at absolute address

            assertEquals(y & 0xFF, ram.read(0x1234));
            assertEquals(y & 0xFF, env.getY());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8003, env.getPC());
        }

        @Test
        void styAbsoluteDoesNotStoreUntilFourthTick() {
            ram.write(0x8000, STY_ABS.getId());
            ram.write(0x8001, 0x34);
            ram.write(0x8002, 0x12);
            ram.write(0x1234, 0x99);

            env.setPC(0x8000);
            env.setY(0x20);

            cpu.tick(); // fetch opcode
            cpu.tick(); // fetch low address byte
            cpu.tick(); // fetch high address byte

            assertEquals(0x99, ram.read(0x1234));

            cpu.tick(); // store Y at absolute address

            assertEquals(0x20, ram.read(0x1234));
            assertEquals(0x8003, env.getPC());
        }
    }

    @Nested
    class INX {
        @ParameterizedTest(name = "INX_I X={0} -> {1}, Z={2}, N={3}")
        @CsvSource({
                // X,    Expected X, Z,     N
                "0x00, 0x01,       false, false",
                "0x7E, 0x7F,       false, false",
                "0x7F, 0x80,       false, true",
                "0xFE, 0xFF,       false, true",
                "0xFF, 0x00,       true,  false"
        })
        void inxIncrementsXAndSetsZeroAndNegativeFlags(int x,
                                                       int expectedX,
                                                       boolean expectedZero,
                                                       boolean expectedNegative) {
            ram.write(0x8000, INX_IMP.getId());

            env.setPC(0x8000);
            env.setX(x);

            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // increment X, set Z and N flags

            assertEquals(expectedX & 0xFF, env.getX());
            assertEquals(expectedZero, env.getZ());
            assertEquals(expectedNegative, env.getN());

            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8001, env.getPC());
        }

        @Test
        void inxDoesNotIncrementUntilSecondTick() {
            ram.write(0x8000, INX_IMP.getId());

            env.setPC(0x8000);
            env.setX(0x20);

            cpu.tick(); // fetch opcode

            assertEquals(0x20, env.getX());
            assertEquals(0x8001, env.getPC());

            cpu.tick(); // increment X, set Z and N flags

            assertEquals(0x21, env.getX());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void inxWrapsFromFfToZero() {
            ram.write(0x8000, INX_IMP.getId());

            env.setPC(0x8000);
            env.setX(0xFF);

            cpu.tick(); // fetch opcode
            cpu.tick(); // increment X, wrapping to zero, set Z and N flags

            assertEquals(0x00, env.getX());
            assertTrue(env.getZ());
            assertFalse(env.getN());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void inxDoesNotAffectAccumulatorYCarryOrOverflow() {
            ram.write(0x8000, INX_IMP.getId());

            env.setPC(0x8000);
            env.setA(0x44);
            env.setX(0x20);
            env.setY(0x55);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // increment X, set Z and N flags

            assertEquals(0x44, env.getA());
            assertEquals(0x21, env.getX());
            assertEquals(0x55, env.getY());

            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8001, env.getPC());
        }
    }

    @Nested
    class PHA {
        private int stackAddress(int stackPointer) {
            return 0x0100 | (stackPointer & 0xFF);
        }

        @ParameterizedTest(name = "PHA A={0}, SP={1}")
        @CsvSource({
                // A,  SP,   Value
                "0x20, 0xFF, 0x99",
                "0x00, 0x80, 0x77",
                "0x80, 0x10, 0x55",
                "0xFF, 0x00, 0x33"
        })
        void phaPushesAccumulatorToStackAndDecrementsStackPointer(int accumulator,
                                                                  int stackPointer,
                                                                  int initialStackValue) {
            ram.write(0x8000, PHA_IMP.getId());
            ram.write(stackAddress(stackPointer), initialStackValue);

            env.setPC(0x8000);
            env.setA(accumulator);
            env.setStackPointer(stackPointer);

            env.setZ(true);
            env.setN(true);
            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // write A to stack, decrement stack pointer

            assertEquals(accumulator & 0xFF, ram.read(stackAddress(stackPointer)));
            assertEquals((stackPointer - 1) & 0xFF, env.getStackPointer());

            assertEquals(accumulator & 0xFF, env.getA());

            assertTrue(env.getZ());
            assertTrue(env.getN());
            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phaDoesNotPushUntilThirdTick() {
            ram.write(0x8000, PHA_IMP.getId());
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setStackPointer(0xFF);

            cpu.tick(); // fetch opcode

            assertEquals(0x99, ram.read(0x01FF));
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());

            cpu.tick(); // internal stack operation

            assertEquals(0x99, ram.read(0x01FF));
            assertEquals(0xFF, env.getStackPointer());

            cpu.tick(); // write A to stack, decrement stack pointer

            assertEquals(0x20, ram.read(0x01FF));
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phaPushesToCurrentStackPointerBeforeDecrementing() {
            ram.write(0x8000, PHA_IMP.getId());
            ram.write(0x01FE, 0x11);
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setStackPointer(0xFF);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // write A to stack, decrement stack pointer

            assertNotEquals(0x99, ram.read(0x01FF), "Expected stack location unchanged from " + 0x99);
            assertEquals(0x20, ram.read(0x01FF));
            assertEquals(0x11, ram.read(0x01FE));
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phaWrapsStackPointerFromZeroToFf() {
            ram.write(0x8000, PHA_IMP.getId());
            ram.write(0x0100, 0x99);

            env.setPC(0x8000);
            env.setA(0x20);
            env.setStackPointer(0x00);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // write A to stack, decrement stack pointer with wrap

            assertEquals(0x20, ram.read(0x0100));
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phaDoesNotAffectAccumulatorIndexRegistersOrFlags() {
            ram.write(0x8000, PHA_IMP.getId());

            env.setPC(0x8000);
            env.setA(0x44);
            env.setX(0x55);
            env.setY(0x66);
            env.setStackPointer(0xFF);

            env.setZ(true);
            env.setN(false);
            env.setCarry(true);
            env.setV(false);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // write A to stack, decrement stack pointer

            assertEquals(0x44, env.getA());
            assertEquals(0x55, env.getX());
            assertEquals(0x66, env.getY());

            assertTrue(env.getZ());
            assertFalse(env.getN());
            assertTrue(env.getCarry());
            assertFalse(env.getV());

            assertEquals(0x44, ram.read(0x01FF));
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }
    }

    @Nested
    class PHP {
        private int expectedProcessorStatusForPhpPush(boolean n,
                                                      boolean v,
                                                      boolean d,
                                                      boolean i,
                                                      boolean z,
                                                      boolean c) {
            return (n ? 0x80 : 0x00)
                    | (v ? 0x40 : 0x00)
                    | 0x20              // unused/status bit 5 is pushed as set
                    | 0x10              // break flag is pushed as set by PHP
                    | (d ? 0x08 : 0x00)
                    | (i ? 0x04 : 0x00)
                    | (z ? 0x02 : 0x00)
                    | (c ? 0x01 : 0x00);
        }

        @ParameterizedTest(name = "PHP N={0}, V={1}, D={2}, I={3}, Z={4}, C={5}")
        @CsvSource({
                // N,     V,     D,     I,     Z,     C
                "false, false, false, false, false, false",
                "false, false, false, false, false, true",
                "false, false, false, false, true,  false",
                "false, false, false, true,  false, false",
                "false, false, true,  false, false, false",
                "false, true,  false, false, false, false",
                "true,  false, false, false, false, false",
                "true,  true,  true,  true,  true,  true"
        })
        void phpPushesProcessorStatusToStackAndDecrementsStackPointer(boolean n,
                                                                      boolean v,
                                                                      boolean d,
                                                                      boolean i,
                                                                      boolean z,
                                                                      boolean c) {
            ram.write(0x8000, PHP_IMP.getId());
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setStackPointer(0xFF);

            env.setN(n);
            env.setV(v);
            env.setD(d);
            env.setI(i);
            env.setZ(z);
            env.setCarry(c);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // push processor status to stack, decrement stack pointer

            assertEquals(expectedProcessorStatusForPhpPush(n, v, d, i, z, c), ram.read(0x01FF));
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phpDoesNotPushUntilThirdTick() {
            ram.write(0x8000, PHP_IMP.getId());
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setStackPointer(0xFF);

            env.setN(false);
            env.setV(false);
            env.setD(false);
            env.setI(false);
            env.setZ(false);
            env.setCarry(false);

            cpu.tick(); // fetch opcode

            assertEquals(0x99, ram.read(0x01FF));
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());

            cpu.tick(); // internal stack operation

            assertEquals(0x99, ram.read(0x01FF));
            assertEquals(0xFF, env.getStackPointer());

            cpu.tick(); // push processor status to stack, decrement stack pointer

            assertEquals(0x30, ram.read(0x01FF)); // unused bit + break bit
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phpPushesToCurrentStackPointerBeforeDecrementing() {
            ram.write(0x8000, PHP_IMP.getId());
            ram.write(0x01FE, 0x11);
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setStackPointer(0xFF);

            env.setN(false);
            env.setV(false);
            env.setD(false);
            env.setI(false);
            env.setZ(false);
            env.setCarry(false);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // push processor status to stack, decrement stack pointer

            assertEquals(0x30, ram.read(0x01FF));
            assertEquals(0x11, ram.read(0x01FE));
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phpWrapsStackPointerFromZeroToFf() {
            ram.write(0x8000, PHP_IMP.getId());
            ram.write(0x0100, 0x99);

            env.setPC(0x8000);
            env.setStackPointer(0x00);

            env.setN(false);
            env.setV(false);
            env.setD(false);
            env.setI(false);
            env.setZ(false);
            env.setCarry(false);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // push processor status to stack, decrement stack pointer with wrap

            assertEquals(0x30, ram.read(0x0100));
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void phpAlwaysPushesBreakAndUnusedBitsAsSet() {
            ram.write(0x8000, PHP_IMP.getId());
            ram.write(0x01FF, 0x99);

            env.setPC(0x8000);
            env.setStackPointer(0xFF);

            env.setN(false);
            env.setV(false);
            env.setD(false);
            env.setI(false);
            env.setZ(false);
            env.setCarry(false);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // push processor status to stack, decrement stack pointer

            int pushedStatus = ram.read(0x01FF);

            assertEquals(0x30, pushedStatus);
            assertTrue((pushedStatus & 0x20) != 0); // unused bit is set
            assertTrue((pushedStatus & 0x10) != 0); // break bit is set
        }

        @Test
        void phpDoesNotChangeRegistersOrFlags() {
            ram.write(0x8000, PHP_IMP.getId());

            env.setPC(0x8000);
            env.setA(0x44);
            env.setX(0x55);
            env.setY(0x66);
            env.setStackPointer(0xFF);

            env.setN(true);
            env.setV(false);
            env.setD(true);
            env.setI(false);
            env.setZ(true);
            env.setCarry(false);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // push processor status to stack, decrement stack pointer

            assertEquals(0x44, env.getA());
            assertEquals(0x55, env.getX());
            assertEquals(0x66, env.getY());

            assertTrue(env.getN());
            assertFalse(env.getV());
            assertTrue(env.getD());
            assertFalse(env.getI());
            assertTrue(env.getZ());
            assertFalse(env.getCarry());

            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }
    }

    @Nested
    class PLA {
        private int stackAddress(int stackPointer) {
            return 0x0100 | (stackPointer & 0xFF);
        }

        private int pulledFromAddress(int currentStackPointer) {
            return stackAddress((currentStackPointer + 1) & 0xFF);
        }

        @ParameterizedTest(name = "PLA pulls {1} from stack with SP={0}, Z={2}, N={3}")
        @CsvSource({
                // SP, Stack Value, Z,     N
                "0xFE, 0x20,        false, false",
                "0xFE, 0x00,        true,  false",
                "0xFE, 0x80,        false, true",
                "0xFE, 0xFF,        false, true"
        })
        void plaPullsAccumulatorFromStackAndSetsZeroAndNegativeFlags(int stackPointer,
                                                                     int stackValue,
                                                                     boolean expectedZero,
                                                                     boolean expectedNegative) {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(pulledFromAddress(stackPointer), stackValue);

            env.setPC(0x8000);
            env.setA(0x11);
            env.setStackPointer(stackPointer);

            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // increment stack pointer
            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(stackValue & 0xFF, env.getA());
            assertEquals((stackPointer + 1) & 0xFF, env.getStackPointer());

            assertEquals(expectedZero, env.getZ());
            assertEquals(expectedNegative, env.getN());

            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0x8001, env.getPC());
        }

        @Test
        void plaDoesNotPullUntilFourthTick() {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(0x01FF, 0x20);

            env.setPC(0x8000);
            env.setA(0x11);
            env.setStackPointer(0xFE);

            cpu.tick(); // fetch opcode

            assertEquals(0x11, env.getA());
            assertEquals(0xFE, env.getStackPointer());
            assertEquals(0x8001, env.getPC());

            cpu.tick(); // internal stack operation

            assertEquals(0x11, env.getA());
            assertEquals(0xFE, env.getStackPointer());

            cpu.tick(); // increment stack pointer

            assertEquals(0x11, env.getA());
            assertEquals(0xFF, env.getStackPointer());

            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(0x20, env.getA());
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void plaIncrementsStackPointerBeforeReading() {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(0x01FE, 0x11);
            ram.write(0x01FF, 0x20);

            env.setPC(0x8000);
            env.setA(0x99);
            env.setStackPointer(0xFE);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // increment stack pointer
            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(0x20, env.getA());
            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void plaWrapsStackPointerFromFfToZeroBeforeReading() {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(0x0100, 0x20);

            env.setPC(0x8000);
            env.setA(0x99);
            env.setStackPointer(0xFF);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // increment stack pointer with wrap
            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(0x20, env.getA());
            assertEquals(0x00, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }

        @Test
        void plaDoesNotRemoveValueFromMemory() {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(0x01FF, 0x20);

            env.setPC(0x8000);
            env.setA(0x99);
            env.setStackPointer(0xFE);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // increment stack pointer
            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(0x20, env.getA());
            assertEquals(0x20, ram.read(0x01FF));
            assertEquals(0xFF, env.getStackPointer());
        }

        @Test
        void plaDoesNotAffectIndexRegistersCarryOrOverflow() {
            ram.write(0x8000, PLA_IMP.getId());
            ram.write(0x01FF, 0x20);

            env.setPC(0x8000);
            env.setA(0x99);
            env.setX(0x55);
            env.setY(0x66);
            env.setStackPointer(0xFE);

            env.setCarry(true);
            env.setV(true);

            cpu.tick(); // fetch opcode
            cpu.tick(); // internal stack operation
            cpu.tick(); // increment stack pointer
            cpu.tick(); // pull value from stack into A, set Z and N flags

            assertEquals(0x20, env.getA());
            assertEquals(0x55, env.getX());
            assertEquals(0x66, env.getY());

            assertTrue(env.getCarry());
            assertTrue(env.getV());

            assertEquals(0xFF, env.getStackPointer());
            assertEquals(0x8001, env.getPC());
        }
    }

    //TODO PUSH_PROCESSOR_STATUS_WITH_BREAK
    //TODO PULL_PROCESSOR_STATUS
    //TODO INC_SP

    //TODO is it worth testing actual operations at this level? ADC, AND...
}