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
        cpu.tick(); //Fetch zero page address to ADL
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
         // A       Op     Expected   Z      N
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
        cpu.tick(); //Fetch argument 1 (Zero page address)
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
        cpu.tick(); //Get arguemnt - zero page address
        cpu.tick(); //Fetch value from zero page and perform ORA

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
        cpu.tick(); //Get argument - zero page address
        cpu.tick(); //Add X to zero page address
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
        cpu.tick(); //Fetch argument 1 (Zero page address)
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
        cpu.tick(); //Fetch argument 1 (Zero Page address) to address bus
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

    //TODO is it worth testing actual operations at this level? ADC, AND...
}