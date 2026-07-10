package com.rox.cpu.mos6502;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MOS6502ALUTest {
    MOS6502Environment env;
    MOS6502ALU alu;

    @BeforeEach
    public void setup(){
        env = new MOS6502Environment();
        alu = new MOS6502ALU(env);
    }

    @ParameterizedTest(name = "ADC: A({0})+M({1})+C({2}) = {3}")
    @CsvSource({
          // A,     O,    C,      Result, V,     N,     C
            "0,     0,    false,  0,      false, false, false",
            "0,     1,    false,  1,      false, false, false",
            "1,     1,    false,  2,      false, false, false",
            "1,     1,    true,   3,      false, false, false",

            // Carry out
            "255,   1,    false,  0,      false, false, true",
            "255,   0,    true,   0,      false, false, true",
            "200,   100,  false,  44,     false, false, true",

            // Negative result
            "0,     128,  false,  128,    false, true,  false",
            "127,   1,    false,  128,    true,  true,  false",
            "126,   1,    false,  127,    false, false, false",

            // Signed overflow
            "80,    80,   false,  160,    true,  true,  false",
            "127,   127,  false,  254,    true,  true,  false",
            "128,   255,  false,  127,    true,  false, true",
            "128,   128,  false,  0,      true,  false, true",

            // No overflow despite carry
            "255,   1,    false,  0,      false, false, true",
            "255,   255,  false,  254,    false, true,  true",

            // Carry-in edge cases
            "127,   0,    true,   128,    true,  true,  false",
            "254,   1,    true,   0,      false, false, true"
    })
    public void testADC(final int accumulator,
                        final int operand,
                        final boolean carryIn,
                        final int expectedAnswer,
                        final boolean expectedOverflowFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedCarryFlag){
        env.setA(accumulator);
        env.setCarry(carryIn);

        alu.adc(operand);
        alu.setStaticFlags(env.getA());

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedOverflowFlag, env.getV());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedCarryFlag, env.getCarry());
    }

    @ParameterizedTest(name = "AND: A({0}) & M({1}) = {2}")
    @CsvSource({
          // A      M     Result  Z      N
            "0,     0,    0,      true,  false",
            "0,     255,  0,      true,  false",
            "255,   0,    0,      true,  false",

            "255,   255,  255,    false, true",
            "170,   15,   10,     false, false", // AA & 0F = 0A
            "240,   15,   0,      true,  false", // F0 & 0F = 00

            "128,   255,  128,    false, true",
            "255,   128,  128,    false, true",
            "127,   255,  127,    false, false",

            "85,    170,  0,      true,  false", // 55 & AA = 00
            "204,   170,  136,    false, true",  // CC & AA = 88

            "1,     1,    1,      false, false",
            "2,     1,    0,      true,  false",
            "254,   127,  126,    false, false"
    })
    public void testAND(final int accumulator,
                        final int operand,
                        final int expectedAnswer,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag) {
        env.setA(accumulator);

        alu.and(operand);
        alu.setStaticFlags(env.getA());

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
    }

    @ParameterizedTest(name = "EOR: A({0}) ^ M({1}) = {2}")
    @CsvSource({
          // A      M     Result  Z      N
            "0,     0,    0,      true,  false",
            "0,     255,  255,    false, true",
            "255,   0,    255,    false, true",
            "255,   255,  0,      true,  false",

            "170,   15,   165,    false, true",  // AA ^ 0F = A5
            "240,   15,   255,    false, true",  // F0 ^ 0F = FF
            "128,   255,  127,    false, false", // 80 ^ FF = 7F
            "127,   255,  128,    false, true",  // 7F ^ FF = 80

            "85,    170,  255,    false, true",  // 55 ^ AA = FF
            "204,   170,  102,    false, false", // CC ^ AA = 66

            "1,     1,    0,      true,  false",
            "2,     1,    3,      false, false",
            "254,   127,  129,    false, true"
    })
    public void testEOR(final int accumulator,
                        final int operand,
                        final int expectedAnswer,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag) {
        env.setA(accumulator);

        alu.eor(operand);
        alu.setStaticFlags(env.getA());

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
    }

    @ParameterizedTest(name = "ORA: A({0}) | M({1}) = {2}")
    @CsvSource({
          // A      M     Result  Z      N
            "0,     0,    0,      true,  false",
            "0,     255,  255,    false, true",
            "255,   0,    255,    false, true",
            "255,   255,  255,    false, true",

            "170,   15,   175,    false, true",  // AA | 0F = AF
            "240,   15,   255,    false, true",  // F0 | 0F = FF
            "128,   1,    129,    false, true",  // 80 | 01 = 81
            "127,   128,  255,    false, true",  // 7F | 80 = FF

            "85,    170,  255,    false, true",  // 55 | AA = FF
            "204,   170,  238,    false, true",  // CC | AA = EE

            "1,     1,    1,      false, false",
            "2,     1,    3,      false, false",
            "126,   1,    127,    false, false"
    })
    public void testORA(final int accumulator,
                        final int operand,
                        final int expectedAnswer,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag) {
        env.setA(accumulator);

        alu.ora(operand);
        alu.setStaticFlags(env.getA());

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
    }

    @ParameterizedTest(name = "SBC: A({0})-M({1})-(1-C({2})) = {3}")
    @CsvSource({
          // A,     M,    C,      Result, V,     N,     C

            // Basic subtraction
            "0,     0,    true,   0,      false, false, true",
            "1,     0,    true,   1,      false, false, true",
            "1,     1,    true,   0,      false, false, true",
            "2,     1,    true,   1,      false, false, true",

            // Borrow cases
            "0,     1,    true,   255,    false, true,  false",
            "0,     0,    false,  255,    false, true,  false",
            "1,     1,    false,  255,    false, true,  false",

            // No borrow
            "255,   1,    true,   254,    false, true,  true",
            "200,   100,  true,   100,    true,  false, true",
            "100,   50,   false,  49,     false, false, true",

            // Zero result
            "5,     5,    true,   0,      false, false, true",
            "5,     4,    false,  0,      false, false, true",

            // Negative result
            "16,    32,   true,   240,    false, true,  false",
            "127,   128,  true,   255,    true,  true,  false",

            // Signed overflow
            "128,   1,    true,   127,    true,  false, true",
            "127,   255,  true,   128,    true,  true,  false",
            "128,   255,  false,  128,    false, true,  false",

            // Carry-in edge cases
            "10,    3,    true,   7,      false, false, true",
            "10,    3,    false,  6,      false, false, true"
    })
    public void testSBC(final int accumulator,
                        final int operand,
                        final boolean carryIn,
                        final int expectedAnswer,
                        final boolean expectedOverflowFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedCarryFlag) {
        env.setA(accumulator);
        env.setCarry(carryIn);

        alu.sbc(operand);
        alu.setStaticFlags(env.getA());

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedOverflowFlag, env.getV());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedCarryFlag, env.getCarry());
    }


    @ParameterizedTest(name = "CMP: A({0})-M({1})")
    @CsvSource({
            // A,     M,    Z,     N,     C
            "0,      0,    true,  false, true",
            "1,      1,    true,  false, true",
            "2,      1,    false, false, true",
            "1,      2,    false, true,  false",

            // Equal
            "255,    255,  true,  false, true",
            "128,    128,  true,  false, true",

            // A greater than M
            "255,    1,    false, true,  true",
            "127,    1,    false, false, true",
            "128,    1,    false, false, true",

            // A less than M
            "0,      1,    false, true,  false",
            "1,      255,  false, false, false",
            "127,    128,  false, true,  false"
    })
    public void testCMP(final int accumulator,
                        final int operand,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedCarryFlag) {
        env.setA(accumulator);

        alu.cmp(operand);

        assertEquals(accumulator, env.getA(), "CMP appears to have modified the accumulator and it shoulnd't");
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedCarryFlag, env.getCarry());
    }

    @ParameterizedTest(name = "CPX: X({0})-M({1})")
    @CsvSource({
            // X,     M,    Z,     N,     C
            "0,      0,    true,  false, true",
            "1,      1,    true,  false, true",
            "2,      1,    false, false, true",
            "1,      2,    false, true,  false",

            // Equal
            "255,    255,  true,  false, true",
            "128,    128,  true,  false, true",

            // X greater than M
            "255,    1,    false, true,  true",
            "127,    1,    false, false, true",
            "128,    1,    false, false, true",

            // X less than M
            "0,      1,    false, true,  false",
            "1,      255,  false, false, false",
            "127,    128,  false, true,  false"
    })
    public void testCPX(final int xRegister,
                        final int operand,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedCarryFlag) {
        env.setX(xRegister);

        alu.cpx(operand);

        assertEquals(xRegister, env.getX(), "CPX appears to have modified the X register and it shouldn't");
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedCarryFlag, env.getCarry());
    }

    @ParameterizedTest(name = "CPY: Y({0})-M({1})")
    @CsvSource({
            // Y,     M,    Z,     N,     C
            "0,      0,    true,  false, true",
            "1,      1,    true,  false, true",
            "2,      1,    false, false, true",
            "1,      2,    false, true,  false",

            // Equal
            "255,    255,  true,  false, true",
            "128,    128,  true,  false, true",

            // Y greater than M
            "255,    1,    false, true,  true",
            "127,    1,    false, false, true",
            "128,    1,    false, false, true",

            // Y less than M
            "0,      1,    false, true,  false",
            "1,      255,  false, false, false",
            "127,    128,  false, true,  false"
    })
    public void testCPY(final int yRegister,
                        final int operand,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedCarryFlag) {
        env.setY(yRegister);

        alu.cpy(operand);

        assertEquals(yRegister, env.getY(), "CPY appears to have modified the Y register and it shouldn't");
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedCarryFlag, env.getCarry());
    }

    @ParameterizedTest(name = "BIT: A({0}) & M({1})")
    @CsvSource({
            // A,     M,    Z,     N,     V
            "0xFF,   0x00,  true,  false, false",
            "0x00,   0xFF,  true,  true,  true",
            "0x01,   0x01,  false, false, false",

            // N/V come from the operand's bits 7/6, independent of the AND result
            "0xFF,   0x80,  false, true,  false",
            "0xFF,   0x40,  false, false, true",
            "0x00,   0x40,  true,  false, true",
            "0x00,   0x80,  true,  true,  false",
            "0xC0,   0xC0,  false, true,  true"
    })
    public void testBIT(final int accumulator,
                        final int operand,
                        final boolean expectedZeroFlag,
                        final boolean expectedNegativeFlag,
                        final boolean expectedOverflowFlag) {
        env.setA(accumulator);

        alu.bit(operand);

        assertEquals(accumulator, env.getA(), "BIT appears to have modified the accumulator and it shouldn't");
        assertEquals(expectedZeroFlag, env.getZ());
        assertEquals(expectedNegativeFlag, env.getN());
        assertEquals(expectedOverflowFlag, env.getV());
    }

    @ParameterizedTest(name = "ASL: {0} -> {1}, C={2}")
    @CsvSource({
            // Operand, Result, Carry
            "0x01,      0x02,   false",
            "0x40,      0x80,   false",
            "0x80,      0x00,   true",
            "0xFF,      0xFE,   true",
            "0x00,      0x00,   false"
    })
    public void testASL(final int operand, final int expectedResult, final boolean expectedCarry) {
        assertEquals(expectedResult, alu.asl(operand));
        assertEquals(expectedCarry, env.getCarry());
    }

    @ParameterizedTest(name = "LSR: {0} -> {1}, C={2}")
    @CsvSource({
            // Operand, Result, Carry
            "0x02,      0x01,   false",
            "0x01,      0x00,   true",
            "0xFF,      0x7F,   true",
            "0x80,      0x40,   false",
            "0x00,      0x00,   false"
    })
    public void testLSR(final int operand, final int expectedResult, final boolean expectedCarry) {
        assertEquals(expectedResult, alu.lsr(operand));
        assertEquals(expectedCarry, env.getCarry());
    }

    @ParameterizedTest(name = "ROL: {0}, C_in={1} -> {2}, C_out={3}")
    @CsvSource({
            // Operand, CarryIn, Result, CarryOut
            "0x01,      false,   0x02,   false",
            "0x80,      false,   0x00,   true",
            "0x40,      true,    0x81,   false",
            "0xFF,      true,    0xFF,   true",
            "0x00,      true,    0x01,   false"
    })
    public void testROL(final int operand, final boolean carryIn,
                        final int expectedResult, final boolean expectedCarryOut) {
        env.setCarry(carryIn);

        assertEquals(expectedResult, alu.rol(operand));
        assertEquals(expectedCarryOut, env.getCarry());
    }

    @ParameterizedTest(name = "ROR: {0}, C_in={1} -> {2}, C_out={3}")
    @CsvSource({
            // Operand, CarryIn, Result, CarryOut
            "0x02,      false,   0x01,   false",
            "0x01,      false,   0x00,   true",
            "0x80,      true,    0xC0,   false",
            "0xFF,      true,    0xFF,   true",
            "0x00,      true,    0x80,   false"
    })
    public void testROR(final int operand, final boolean carryIn,
                        final int expectedResult, final boolean expectedCarryOut) {
        env.setCarry(carryIn);

        assertEquals(expectedResult, alu.ror(operand));
        assertEquals(expectedCarryOut, env.getCarry());
    }

    @Test
    public void rolChainedRotatePropagatesCarryThroughSuccessiveCalls() {
        env.setCarry(false);

        int result = alu.rol(0x80); // bit7 set -> carry out true; carry in was false
        assertEquals(0x00, result);
        assertTrue(env.getCarry());

        result = alu.rol(result); // bit7 clear -> carry out false; carry in (from above) rotates into bit0
        assertEquals(0x01, result);
        assertFalse(env.getCarry());
    }

    @Test
    public void rorChainedRotatePropagatesCarryThroughSuccessiveCalls() {
        env.setCarry(false);

        int result = alu.ror(0x01); // bit0 set -> carry out true; carry in was false
        assertEquals(0x00, result);
        assertTrue(env.getCarry());

        result = alu.ror(result); // bit0 clear -> carry out false; carry in (from above) rotates into bit7
        assertEquals(0x80, result);
        assertFalse(env.getCarry());
    }
}
