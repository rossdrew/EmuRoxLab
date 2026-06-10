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

        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedAnswer, env.getA());
        assertEquals(expectedOverflowFlag, env.getV());
        assertEquals(expectedCarryFlag, env.getCarry());
    }
}
