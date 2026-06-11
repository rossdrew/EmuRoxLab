package com.rox.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
