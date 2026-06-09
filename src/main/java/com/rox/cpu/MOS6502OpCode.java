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
            opsInTick(ADL_FROM_PC_POINTER),
            opsInTick(ADDRESS_ADL, ADC))
    ),

    /** Immediate Addressed ADC (ADd with Carry) */
    ADC_I(0x69, clockTicks(
            opsInTick(ADDRESS_PC, ADC))
    ),

    /** A ← A + Memory[ADH:ADL] + Carry */
    ADC_ABS(0x6D, clockTicks(
            opsInTick(ADDRESS_PC, MEM_TO_ADL),
            opsInTick(ADH_FROM_PC),
            opsInTick(ABS_ADDRESS, ADC))
    ),

    /** A ← A + Memory[$1234 + X] + Carry */
    ADC_ABS_X(0x7D, clockTicks(
            opsInTick(ADL_FROM_PC_POINTER),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            opsInTick(ADC))
    ),

    ADC_ABS_Y(0x79, clockTicks(
            opsInTick(ADL_FROM_PC_POINTER),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            opsInTick(ADC))
    ),

    ADC_IND_X(0x61, clockTicks(
            opsInTick(ADDRESS_PC), //XXX store in temporary location we can add to?
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(MEM_TO_ADL),
            opsInTick(MEM_INC_TO_ADH, ABS_ADDRESS),
            opsInTick(ADC))
    ),

    ADC_IND_Y(0x71, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(ADDRESS_MEM_POINTER, MEM_TO_ADL),
            opsInTick(MEM_INC_TO_ADH, AD_PLUS_Y),
            opsInTick(ADC))
    ),

    ADC_Z_X(0x75, clockTicks(
            opsInTick(ADDRESS_PC),
            opsInTick(X_OFFSET_ADDRESS),
            opsInTick(ADC))
    ),

    LDA_I(0xA9, clockTicks(
            opsInTick(A_FROM_PC, SET_FLAGS_ON_A)
    )),

    LDA_Z(0xA5, clockTicks(
            opsInTick(ADL_FROM_PC_POINTER),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    )),

    LDA_Z_X(0xB5, clockTicks(
            opsInTick(ADL_FROM_PC_POINTER),
            opsInTick(ADL_PLUS_X),
            opsInTick(ADDRESS_ADL, A_FROM_AD, SET_FLAGS_ON_A)
    ));

    //LDA_ABS
    //LDA_ABS_X
    //LDA_ABS_Y
    //LDA_IND_X
    //LDA_IND_Y

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
