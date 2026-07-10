package com.rox.cpu.mos6502;

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

    public int adc(final int b){
        int a = environment.getA();
        int result = a + b + (environment.getCarry()?1:0);

        environment.setV((~(a ^ b) & (a ^ result) & BIT[7]) != 0); //matching signs result in mismatched sign
        environment.setCarry(result > 0xFF); //result is > 255
        environment.setA(result & 0xFF);
        return (result & 0xFF);
    }

    public int and(int b) {
        int a = environment.getA();
        int result = a & b;

        environment.setA(result & 0xFF);
        return (result & 0xFF);
    }

    public int ora(int b) {
        int a = environment.getA();
        int result = a | b;

        environment.setA(result & 0xFF);
        return (result & 0xFF);
    }

    public int eor(int b) {
        int a = environment.getA();
        int result = a ^ b;

        environment.setA(result & 0xFF);
        return (result & 0xFF);
    }

    public int sbc(int b) {
        return adc(~b & 0xFF);
    }

    public void cmp(int operand) {
        int result = environment.getA() - operand;

        environment.setCarry(result >= 0);
        setStaticFlags(result & 0xFF);
    }

    public void cpx(int operand) {
        int result = environment.getX() - operand;

        environment.setCarry(result >= 0);
        setStaticFlags(result & 0xFF);
    }

    public void cpy(int operand) {
        int result = environment.getY() - operand;

        environment.setCarry(result >= 0);
        setStaticFlags(result & 0xFF);
    }

    public void bit(final int operand) {
        environment.setN((operand & BIT[7]) != 0);
        environment.setV((operand & BIT[6]) != 0);
        environment.setZ((environment.getA() & operand) == 0);
    }

    public int asl(final int operand) {
        environment.setCarry((operand & BIT[7]) != 0);
        return (operand << 1) & 0xFF;
    }

    public int lsr(final int operand) {
        environment.setCarry((operand & BIT[0]) != 0);
        return (operand >> 1) & 0xFF;
    }

    public int rol(final int operand) {
        final boolean oldCarry = environment.getCarry();

        environment.setCarry((operand & BIT[7]) != 0);
        int result = (operand << 1) & 0xFF;
        if (oldCarry) {
            result |= BIT[0];
        }
        return result;
    }

    public int ror(final int operand) {
        final boolean oldCarry = environment.getCarry();

        environment.setCarry((operand & BIT[0]) != 0);
        int result = (operand >> 1) & 0xFF;
        if (oldCarry) {
            result |= BIT[7];
        }
        return result;
    }

    public void setStaticFlags(final int basedOn) {
        environment.setN((basedOn & BIT[7]) != 0); //bit 7 is set
        environment.setZ(basedOn == 0); //result is zero
    }
}
