package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;

/**
 * WIP
 */
class MOS6502Environment {
     // Status Flags
     // Bit:   7 6 5 4 3 2 1 0
     //       +---------------+
     //       |N|V|1|B|D|I|Z|C|
     //       +---------------+
    public boolean negative;         //0x80
    public boolean signedOverflow;   //0x40
    public boolean breakFlag;        //0x10
    public boolean d;                //0x08
    public boolean i;                //0x04
    public boolean zero;             //0x02
    public boolean carry;            //0x01

    private int pc;                  //16 bit program counter
    private int ir;                  //instruction register
    private int adl;                 //(1/2) address data low byte
    private int adh;                 //(2/2) address data high byte
    private int a;                   //accumulator
    private int x;                   //X register
    private int y;                   //Y registers
    private int stackPointer = 0xFF; //Low byte of the stack pointer $01:XX

    private boolean onGoingExpensiveOp = false;

    MOS6502Environment(){
        this(false, false, false, false, 0,0,0,0,0, 0);
    }

    MOS6502Environment(boolean c, boolean z, boolean n, boolean v,
                       int pc, int ir, int adl, int adh, int a, int x){
        this.carry = c;
        this.zero = z;
        this.negative = n;
        this.signedOverflow = v;
        this.pc = pc;
        this.ir = ir;
        this.adl = adl;
        this.adh = adh;
        this.a = a;
        this.x = x;
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

    public int getAD(){
        return (getADH() << 8 | getADL()) & 0xFFFF;
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

    public void setPC(final int newPC) {
        this.pc = newPC;
    }

    public void setPCL(final int newPCL) {
        this.pc = (this.pc & 0xFF00) | (newPCL & 0xFF);
    }

    public void setPCH(final int newPCH) {
        this.pc = (this.pc & 0x00FF) | ((newPCH << 8) & 0xFF00);
    }

    public int getX() {
        return this.x;
    }

    public void setX(int newX) {
        this.x = newX;
    }

    public void setY(int newY) {
        this.y = newY;
    }

    public int getY() {
        return y;
    }

    public boolean getCarry() {
        return carry;
    }

    public void setV(final boolean newV){
        this.signedOverflow = newV;
    }

    public boolean getV() {
        return signedOverflow;
    }

    public void setN(final boolean newN){
        this.negative = newN;
    }

    public boolean getN() {
        return negative;
    }

    public void setZ(final boolean newZ) {
        this.zero = newZ;
    }

    public boolean getZ() {
        return zero;
    }

    public void setD(boolean newD) {
        this.d = newD;
    }

    public boolean getD(){
        return d;
    }

    public void setI(boolean newI) {
        this.i = newI;
    }

    public boolean getI(){
        return i;
    }

    public void setCarry(boolean carry) {
        this.carry = carry;
    }

    public void setStackPointer(final int newStackLowByteValue){
        this.stackPointer = newStackLowByteValue & 0xFF;
    }

    public int getStackPointer(){
        return this.stackPointer;
    }

    /**
     * Get the current state of the status register
     * <pre>
     * Bit:   7 6 5 4 3 2 1 0
     *       +---------------+
     *       |N|V|1|B|D|I|Z|C|
     *       +---------------+
     * </pre>
     */
    public int getStatus(boolean breakFlag) {
        return (getN() ? 0x80 : 0)
                | (getV() ? 0x40 : 0)
                | 0x20                     // bit 5 always set for now
                | (breakFlag ? 0x10 : 0)
                | (getD() ? 0x08 : 0)
                | (getI() ? 0x04 : 0)
                | (getZ() ? 0x02 : 0)
                | (getCarry() ? 0x01 : 0);
    }

    public boolean additionalTickPending(){
        //XXX (1/4) Workaround for opcodes which have varying cycles
        return onGoingExpensiveOp;
    }

    private MOS6502Operation pendingOperation;
    public void requestAdditionalOp(MOS6502Operation operation) {
        //XXX (2/4) Workaround for opcodes which have varying cycles
        onGoingExpensiveOp = true;
        this.pendingOperation = operation;
    }

    public MOS6502Operation getPendingOperation(){
        //XXX (3/4) Workaround for opcodes which have varying cycles
        return this.pendingOperation;
    }

    public void additionalTickCompleted(){
        //XXX (4/4) Workaround for opcodes which have varying cycles
        onGoingExpensiveOp = false;
    }


    /** Overflow safe PC + increment */
    public int getAndIncrementPC(){
        int cached_pc = pc;
        pc = (pc + 1) & 0xFFFF;
        return cached_pc;
    }

    public MOS6502Environment clone(){
        return new MOS6502Environment(carry, zero, negative, signedOverflow, pc, ir, adl, adh, a, x);
    }

    @Override
    public String toString() {
        return "pc:"+pc+", ir:"+ir+", ad["+adh+":"+adl+"], a:"+a+", x:"+x+" | F[c:"+carry+", z:"+ zero +", n:"+ negative +", v:"+ signedOverflow +"]";
    }
}