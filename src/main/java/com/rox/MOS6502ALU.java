package com.rox;

public class MOS6502ALU {
    private final int[] BIT = {
            0b0000_0001,
            0b0000_0010,
            0b0000_0100,
            0b0000_1000,
            0b0001_0000,
            0b0010_0000,
            0b0100_0000,
            0b1000_0000
    };

    private final MOS6502.Environment environment;

    public MOS6502ALU(final MOS6502.Environment environment){
        this.environment = environment;
    }

    public int adc(final int a, final int b){
        int result = a + b + (environment.carry?1:0);
        environment.n = (result & BIT[7]) != 0; //bit 7 is set
        environment.z = result == 0; //result is zero
        environment.v = (~(a ^ b) & (a ^ result) & BIT[7]) != 0; //matching signs result in mismatched sign
        environment.carry = result > 0xFF; //result is > 255
        return result & 0xFF;
    }
}
