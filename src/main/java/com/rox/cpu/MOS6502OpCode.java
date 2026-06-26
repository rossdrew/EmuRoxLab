package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rox.cpu.MOS6502MicroOp.*;

public enum MOS6502OpCode {
    // NOTE: The first cycle is the FETCH cycle!!

    //XXX Possible bug, some instructions might not incremenet the pc, meaning the next instruction will read from the wrong place

    BRK(0x00, clockTicks(
//  1	Fetch opcode
//  2	Read and discard the next byte (increment PC again)
            opsInTick(DUMMY_READ, INC_PC),
//  3	Push PCH onto the stack
            opsInTick(), //TODO
//  4	Push PCL onto the stack
            opsInTick(), //TODO
//  5	Push processor status (with B flag set), set I flag
            opsInTick(), //TODO
//  6	Read interrupt vector low byte from $FFFE
            opsInTick(), //TODO
//  7	Read interrupt vector high byte from $FFFF, load PC
            opsInTick() //TODO
    )),

    NOP(0x6E, clockTicks(
            opsInTick() //Do nothing
    )),

    /** Zero Page Addressed ADC (ADd with Carry) */
    ADC_Z(0x65, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, ADC, SET_FLAGS_ON_A)
    )),

    /** Immediate Addressed ADC (ADd with Carry) */
    ADC_I(0x69, clockTicks(
            opsInTick(ADDRESS_PC, ADC, SET_FLAGS_ON_A)
    )),

    /** A ← A + Memory[ADH:ADL] + Carry */
    ADC_ABS(0x6D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, ADC, SET_FLAGS_ON_A)
    )),

    /** A ← A + Memory[$1234 + X] + Carry */
    ADC_ABS_X(0x7D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(ADC, SET_FLAGS_ON_A))
    ),

    ADC_ABS_Y(0x79, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_IND_X(0x61, clockTicks(
            opsInTick(ADDRESS_PC), //XXX store in temporary location we can add to?
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_IND_Y(0x71, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_Z_X(0x75, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    LDA_I(0xA9, clockTicks(
            opsInTick(ADDRESS_PC, A_FROM_MEM, SET_FLAGS_ON_A)
    )),

    LDA_Z(0xA5, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_Z_X(0xB5, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADL_PLUS_X),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS(0xAD, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS_X(0xBD, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS_Y(0xB9, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_IND_X(0xA1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_IND_Y(0xB1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDX_I(0xA2, clockTicks(
            opsInTick(ADDRESS_PC, X_FROM_MEM, SET_FLAGS_ON_X)
    )),

    LDX_Z(0xA6, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, X_FROM_AD, SET_FLAGS_ON_X)
    )),

    LDX_Z_Y(0xB6, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADL_PLUS_Y),
            opsInTick(ADDRESS_ADL, X_FROM_AD, SET_FLAGS_ON_X)
    )),

    LDX_ABS(0xAE, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, X_FROM_AD, SET_FLAGS_ON_X)
    )),

    LDX_ABS_Y(0xBE, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(X_FROM_AD, SET_FLAGS_ON_X)
    )),

    LDY_I(0xA0, clockTicks(
            opsInTick(ADDRESS_PC, Y_FROM_MEM, SET_FLAGS_ON_Y)
    )),

    LDY_Z(0xA4, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, Y_FROM_AD, SET_FLAGS_ON_Y)
    )),

    LDY_Z_X(0xB4, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADL_PLUS_X),
            opsInTick(ADDRESS_ADL, Y_FROM_AD, SET_FLAGS_ON_Y)
    )),

    LDY_ABS(0xAC, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, Y_FROM_AD, SET_FLAGS_ON_Y)
    )),

    LDY_ABS_X(0xBC, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(Y_FROM_AD, SET_FLAGS_ON_Y)
    )),

    AND_I(0x29, clockTicks(
            opsInTick(ADDRESS_PC, AND, SET_FLAGS_ON_A)
    )),

    AND_Z(0x25, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, AND, SET_FLAGS_ON_A)
    )),

    AND_Z_X(0x35,clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_ABS(0x2D,clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, AND, SET_FLAGS_ON_A)
    )),

    AND_ABS_X(0x3D,clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_ABS_Y(0x39,clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_IND_X(0x21,clockTicks(
            opsInTick(ADDRESS_PC), //XXX store in temporary location we can add to?
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_IND_Y(0x31,clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    ORA_I(0x09, clockTicks(
            opsInTick(ADDRESS_PC, ORA, SET_FLAGS_ON_A)
    )),

    ORA_Z(0x05, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, ORA, SET_FLAGS_ON_A)
    )),

    ORA_Z_X(0x15, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_ABS(0x0D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, ORA, SET_FLAGS_ON_A)
    )),

    ORA_ABS_X(0x1D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_ABS_Y(0x19, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_IND_X(0x01, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_IND_Y(0x11, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    EOR_I(0x49, clockTicks(
            opsInTick(ADDRESS_PC, EOR, SET_FLAGS_ON_A)
    )),

    EOR_Z(0x45, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, EOR, SET_FLAGS_ON_A)
    )),

    EOR_Z_X(0x55, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(EOR, SET_FLAGS_ON_A)
    )),

    EOR_ABS(0x4D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, EOR, SET_FLAGS_ON_A)
    )),

    EOR_ABS_X(0x5D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(EOR, SET_FLAGS_ON_A)
    )),

    EOR_ABS_Y(0x59, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(EOR, SET_FLAGS_ON_A)
    )),

    EOR_IND_X(0x41, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(EOR, SET_FLAGS_ON_A)
    )),

    EOR_IND_Y(0x51, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(EOR, SET_FLAGS_ON_A)
    )),

    SBC_I(0xE9, clockTicks(
            opsInTick(ADDRESS_PC, SBC, SET_FLAGS_ON_A)
    )),

    SBC_Z(0xE5, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, SBC, SET_FLAGS_ON_A)
    )),

    SBC_Z_X(0xF5, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(SBC, SET_FLAGS_ON_A)
    )),

    SBC_ABS(0xED, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, SBC, SET_FLAGS_ON_A)
    )),

    SBC_ABS_X(0xFD, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(SBC, SET_FLAGS_ON_A)
    )),

    SBC_ABS_Y(0xF9, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(SBC, SET_FLAGS_ON_A)
    )),

    SBC_IND_X(0xE1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(SBC, SET_FLAGS_ON_A)
    )),

    SBC_IND_Y(0xF1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(SBC, SET_FLAGS_ON_A)
    )),

    CMP_I(0xC9, clockTicks(
            opsInTick(ADDRESS_PC, CMP)
    )),

    CMP_Z(0xC5, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, CMP)
    )),

    CMP_Z_X(0xD5, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(CMP)
    )),

    CMP_ABS(0xCD, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, CMP)
    )),

    CMP_ABS_X(0xDD, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X_AND_PAGE_CROSS),
            opsInTick(CMP)
    )),

    CMP_ABS_Y(0xD9, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(CMP)
    )),

    CMP_IND_X(0xC1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(CMP)
    )),

    CMP_IND_Y(0xD1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y_AND_PAGE_CROSS),
            opsInTick(CMP)
    )),

    JMP_ABS(0x4C, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, AD_TO_PC)
    )),

    JMP_I(0x6C, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, PCL_FROM_MEM),
            opsInTick(INC_ADL, ADDRESS_AD , PCH_FROM_MEM)
    )),

    STA_Z(0x85, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, MEM_FROM_A)
    )),

    STA_Z_X(0x95, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_FROM_A)
    )),

    STA_ABS(0x8D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, MEM_FROM_A)
    )),

    STA_ABS_X(0x9D, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_X),
            opsInTick(DUMMY_READ),
            opsInTick(MEM_FROM_A)
    )),

    STA_ABS_Y(0x99, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM, ADDRESS_AD_PLUS_Y),
            opsInTick(DUMMY_READ),
            opsInTick(MEM_FROM_A)
    )),

    STA_IND_X(0x81, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(MEM_FROM_A)
    )),

    STA_IND_Y(0x91, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, ADL_FROM_MEM),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD_PLUS_Y),
            opsInTick(DUMMY_READ),
            opsInTick(MEM_FROM_A)
    )),

    STX_Z(0x86, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, MEM_FROM_X)
    )),

    STX_Z_Y(0x96, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADL_PLUS_Y),
            opsInTick(ADDRESS_ADL, MEM_FROM_X)
    )),

    STX_ABS(0x8E, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, MEM_FROM_X)
    )),

    STY_Z(0x84, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_ADL, MEM_FROM_Y)
    )),

    STY_Z_X(0x94, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADL_PLUS_X),
            opsInTick(ADDRESS_ADL, MEM_FROM_Y)
    )),

    STY_ABS(0x8C, clockTicks(
            opsInTick(ADDRESS_PC, ADL_FROM_MEM),
            opsInTick(ADDRESS_PC, ADH_FROM_MEM),
            opsInTick(ADDRESS_AD, MEM_FROM_Y)
    )),

    INX_IMP(0xE8, clockTicks(
            opsInTick(INX, SET_FLAGS_ON_X)
    )),

    PHA_IMP(0x48, clockTicks(
            opsInTick(DUMMY_READ),
            opsInTick(PUSH_A)
    )),

    PLA_IMP(0x68, clockTicks(
            opsInTick(DUMMY_READ),
            opsInTick(INC_SP),
            opsInTick(PULL_A, SET_FLAGS_ON_A)
    )),

    PHP_IMP(0x08, clockTicks(
            opsInTick(DUMMY_READ),
            opsInTick(PUSH_PROCESSOR_STATUS_WITH_BREAK)
    ))
    ;

    /*
    For sample program: INX, CPX, BNE, BRK

    PLP - provide many microops for BRK

    Transfer instructions (TAX, etc.)
    DEX, INY, DEY
    BIT
    Branch instructions
    Stack instructions
    JSR / RTS
    Shifts and rotates
    Interrupts (BRK, RTI)
     */

    /** 6502 code for this OpCode */
    private final int id;
    /** [tick][operation] */
    private final MOS6502Operation[][] ops;

    /** Helper method to make setting up enum arguments cleaner */
    private static MOS6502Operation[] opsInTick(final MOS6502Operation... ops) {
        return ops;
    }

    /** Helper method to make setting up enum arguments cleaner */
    private static MOS6502Operation[][] clockTicks(final MOS6502Operation[]... ticks) {
        return ticks;
    }

    /** Helper method for turning an ID into an enum */
    private static final Map<Integer, MOS6502OpCode> BY_ID =
            Arrays.stream(values())
                    .collect(Collectors.toMap(op -> op.id, op -> op));

    MOS6502OpCode(final int id, final MOS6502Operation[][] ops) {
        this.id = id;
        this.ops = ops;
    }

    /**
     * @return the list of {@link MOS6502Operation}s that make up this opcode arranged as a 2d array where
     * [clock tick][operation in that clock tick]
     */
    public MOS6502Operation[][] getOperations(){
        return ops.clone();
    }

    /**
     * @param id for a MOS6502 opcode
     * @return the {@link MOS6502OpCode} associated with the given ID
     * @throws IllegalArgumentException if the ID is unknown
     */
    public static MOS6502OpCode from(final int id) {
        final MOS6502OpCode opCode = BY_ID.get(id);

        if (opCode == null) {
            throw new IllegalArgumentException(String.format("Unknown opcode: 0x%02X", id));
        }

        return opCode;
    }

    public Integer getId() {
        return id;
    }
}
