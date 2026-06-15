package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rox.cpu.MOS6502MicroOp.*;

public enum MOS6502OpCode {
    // NOTE: The first cycle is the FETCH cycle!!

    /** Zero Page Addressed ADC (ADd with Carry) */
    ADC_Z(0x65, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADDRESS_ADL, ADC, SET_FLAGS_ON_A)
    )),

    /** Immediate Addressed ADC (ADd with Carry) */
    ADC_I(0x69, clockTicks(
            opsInTick(ADDRESS_PC, ADC, SET_FLAGS_ON_A)
    )),

    /** A ← A + Memory[ADH:ADL] + Carry */
    ADC_ABS(0x6D, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC),
            opsInTick(ADDRESS_AD, ADC, SET_FLAGS_ON_A)
    )),

    /** A ← A + Memory[$1234 + X] + Carry */
    ADC_ABS_X(0x7D, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            opsInTick(ADC, SET_FLAGS_ON_A))
    ),

    ADC_ABS_Y(0x79, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_IND_X(0x61, clockTicks(
            opsInTick(ADDRESS_PC), //XXX store in temporary location we can add to?
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_IND_Y(0x71, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, AD_PLUS_Y),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    ADC_Z_X(0x75, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADC, SET_FLAGS_ON_A)
    )),

    LDA_I(0xA9, clockTicks(
            opsInTick(A_FROM_PC, SET_FLAGS_ON_A)
    )),

    LDA_Z(0xA5, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_Z_X(0xB5, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADL_PLUS_X),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS(0xAD, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC),
            opsInTick(ADDRESS_AD, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS_X(0xBD, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_ABS_Y(0xB9, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_IND_X(0xA1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_IND_Y(0xB1, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, AD_PLUS_Y),
            opsInTick(A_FROM_AD, SET_FLAGS_ON_A)
    )),

    AND_I(0x29, clockTicks(
            opsInTick(ADDRESS_PC, AND, SET_FLAGS_ON_A)
    )),

    AND_Z(0x25, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADDRESS_ADL, AND, SET_FLAGS_ON_A)
    )),

    AND_Z_X(0x35,clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_ABS(0x2D,clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC),
            opsInTick(ADDRESS_AD, AND, SET_FLAGS_ON_A)
    )),

    AND_ABS_X(0x3D,clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_ABS_Y(0x39,clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_IND_X(0x21,clockTicks(
            opsInTick(ADDRESS_PC), //XXX store in temporary location we can add to?
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    AND_IND_Y(0x31,clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, AD_PLUS_Y),
            opsInTick(AND, SET_FLAGS_ON_A)
    )),

    ORA_I(0x09, clockTicks(
            opsInTick(ADDRESS_PC, ORA, SET_FLAGS_ON_A)
    )),

    ORA_Z(0x05, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADDRESS_ADL, ORA, SET_FLAGS_ON_A)
    )),

    ORA_Z_X(0x15, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_ABS(0x0D, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC),
            opsInTick(ADDRESS_AD, ORA, SET_FLAGS_ON_A)
    )),

    //AI generated = Needs validated...

    ORA_ABS_X(0x1D, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_ABS_Y(0x19, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_IND_X(0x01, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, ADDRESS_AD),
            opsInTick(ORA, SET_FLAGS_ON_A)
    )),

    ORA_IND_Y(0x11, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, MEM_TO_ADL),
            opsInTick(NEXT_MEM_TO_ADH, AD_PLUS_Y),
            opsInTick(ORA, SET_FLAGS_ON_A)
    ));

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
