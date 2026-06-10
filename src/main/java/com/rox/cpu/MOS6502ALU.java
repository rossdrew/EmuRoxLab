package com.rox.cpu;

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

    private final MOS6502Environment environment;

    public MOS6502ALU(final MOS6502Environment environment){
        this.environment = environment;
    }

    public void adc(final int b){
        int a = environment.getA();
        int result = a + b + (environment.carry?1:0);


        environment.setV((~(a ^ b) & (a ^ result) & BIT[7]) != 0); //matching signs result in mismatched sign
        environment.setCarry(result > 0xFF); //result is > 255
        environment.setA(result & 0xFF);
    }

    public void and(int b) {
        int a = environment.getA();
        int result = a & b;

        environment.setA(result & 0xFF);
    }

    public void setStaticFlags(final int basedOn) {
        environment.setN((basedOn & BIT[7]) != 0); //bit 7 is set
        environment.setZ(basedOn == 0); //result is zero
    }
}
