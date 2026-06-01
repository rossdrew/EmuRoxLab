package com.rox.cpu;

import com.rox.cpu.MOS6502.MOS6502Operation;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rox.cpu.MOS6502MicroOp.*;

public enum MOS6502OpCode {
    // NOTE: The first cycle is the FETCH cycle!!

    /** Zero Page Addressed ADC (ADd with Carry) */
    ADC_Z(0x65, clockTick(
            opsInTick(ADL_FROM_PC),
            opsInTick(LOAD_ADL_ADDRESS, ADC))
    ),

    /** Immediate Addressed ADC (ADd with Carry) */
    ADC_I(0x69, clockTick(
            opsInTick(LOAD_PC_ADDRESS, ADC))
    ),

    /** A ← A + Memory[ADH:ADL] + Carry */
    ADC_ABS(0x6D, clockTick(
            opsInTick(ADL_FROM_PC_ADDRESS),
            opsInTick(ADH_FROM_PC),
            opsInTick(ABS_ADDRESS, ADC))
    ),

    /** A ← A + Memory[$1234 + X] + Carry */
    ADC_ABS_X(0x7D, clockTick(
            opsInTick(ADL_FROM_PC),
            opsInTick(ADH_FROM_PC, AD_PLUS_X),
            //There's an optional operation in here if a page cross is needed
            opsInTick(ADC))
    ),

    ADC_ABS_Y(0x79, clockTick(
            opsInTick(ADL_FROM_PC),
            opsInTick(ADH_FROM_PC, AD_PLUS_Y),
            //There's an optional operation in here if a page cross is needed
            opsInTick(ADC))
    ),

    ADC_IND_X(0x61, clockTick(
            //get zero page pointer operand
            opsInTick(LOAD_PC_ADDRESS), //XXX store in temporary location we can add to?
            //add X to operand & read low byte of effective address from zero page
            opsInTick(X_OFFSET_ADDRESS),
            //read high byte of address from zero page
            opsInTick(ADL_FETCH),
            //read operand from effective address
            opsInTick(ADH_INC_FETCH),
            //perform adc
            opsInTick(ADC))
    ),

    ADC_IND_Y(0x71, clockTick(
            //TODO
            opsInTick(ADC))
    ),

    ADC_ZP_X(0x75, clockTick(
            //TODO
            opsInTick(ADC))
    );

    /** 6502 code for this OpCode */
    private final int id;
    /** [tick][operation] */
    private final MOS6502Operation[][] ops;

    /** Helper method to make setting up enum arguments cleaner */
    private static MOS6502Operation[] opsInTick(final MOS6502Operation... ops) {
        return ops;
    }

    /** Helper method to make setting up enum arguments cleaner */
    private static MOS6502Operation[][] clockTick(final MOS6502Operation[]... ticks) {
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
