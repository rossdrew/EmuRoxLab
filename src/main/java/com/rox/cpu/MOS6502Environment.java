package com.rox.cpu;

/**
 * WIP
 */
class MOS6502Environment {
    public boolean carry; //FLAG: Carry
    public boolean z; //FLAG: Zero
    public boolean n; //FLAG: Negative
    public boolean v; //FLAG: Signed Overflow

    private int pc; //program counter
    private int ir; //instruction register
    private int adl; //(1/2) address data low byte
    private int adh; //(2/2) address data high byte
    private int a;  //accumulator

    MOS6502Environment(){
        this(false, false, false, false, 0,0,0,0,0);
    }

    MOS6502Environment(boolean c, boolean z, boolean n, boolean v,
                       int pc, int ir, int adl, int adh, int a){
        this.carry = c;
        this.z = z;
        this.n = n;
        this.v = v;
        this.pc = pc;
        this.ir = ir;
        this.adl = adl;
        this.adh = adh;
        this.a = a;
    }

    public int getIR(){
        return this.ir;
    }

    public void setIR(final int value){
        this.ir = value & 0xFF;
    }

    public int getADL(){
        return this.adl & 0xFF;
    }

    public void setADL(final int value){
        this.adl = value & 0xFF;
    }

    public int getADH() {
        return this.adh & 0xFF;
    }

    public void setADH(final int value) {
        this.adh = value & 0xFF;
    }

    public void setA(final int value){
        this.a = value & 0xFF;
    }

    public int getA() {
        return a & 0xFF;
    }

    public int getPC() {
        return pc;
    }

    /** Overflow safe PC + increment */
    public int pc(){
        int cached_pc = pc;
        pc = (pc + 1) & 0xFFFF;
        return cached_pc;
    }

    public MOS6502Environment clone(){
        return new MOS6502Environment(carry, z, n, v, pc, ir, adl, adh, a);
    }

    @Override
    public String toString() {
        return "pc:"+pc+", ir:"+ir+", ad["+adh+":"+adl+"], a:"+a+" | F[c:"+carry+", z:"+z+", n:"+n+", v:"+v+"]";
    }
}