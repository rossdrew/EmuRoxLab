package com.rox.cpu.mos6502.assembler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssemblerTest {

    @Test
    public void assembleWithoutAnExplicitStartAddressDefaultsToZero() {
        final AssembledProgram program = Assembler.assemble("BRK");

        assertEquals(0, program.startAddress());
    }

    @Test
    public void assemblesAStraightLineProgramWithNoLabelReferences() {
        final AssembledProgram program = Assembler.assemble("""
                LDA #$05
                STA $10
                """, 0x8000);

        assertArrayEquals(new int[]{0xA9, 0x05, 0x85, 0x10}, program.bytes());
        assertTrue(program.labels().isEmpty());
    }

    @Test
    public void aDefinedButUnreferencedLabelStillAppearsInTheSymbolTable() {
        final AssembledProgram program = Assembler.assemble("""
                START:  LDA #$05
                        BRK
                """, 0x8000);

        assertEquals(Map.of("START", 0x8000), program.labels());
        assertArrayEquals(new int[]{0xA9, 0x05, 0x00}, program.bytes());
    }

    @Test
    public void labelOnlyLineFollowedByLabelAndInstructionLineBothResolveToTheSameAddress() {
        final AssembledProgram program = Assembler.assemble("""
                FOO:
                BAR:    LDA #$00
                """, 0x8000);

        assertEquals(Map.of("FOO", 0x8000, "BAR", 0x8000), program.labels());
        assertArrayEquals(new int[]{0xA9, 0x00}, program.bytes());
    }

    @Test
    public void forwardBranchResolvesCorrectly() {
        final AssembledProgram program = Assembler.assemble("""
                BEQ SKIP
                INX
                SKIP:   BRK
                """, 0x8000);

        // offset = target(0x8003) - (instructionAddress(0x8000) + 2) = 1
        assertArrayEquals(new int[]{0xF0, 0x01, 0xE8, 0x00}, program.bytes());
        assertEquals(Map.of("SKIP", 0x8003), program.labels());
    }

    @Test
    public void jmpToALabelEmitsALittleEndianAbsoluteAddress() {
        final AssembledProgram program = Assembler.assemble("""
                JMP TARGET
                TARGET: BRK
                """, 0x8000);

        assertArrayEquals(new int[]{0x4C, 0x03, 0x80, 0x00}, program.bytes());
    }

    @Test
    public void duplicateLabelThrows() {
        assertThrows(AssemblyException.class, () -> Assembler.assemble("""
                START:  NOP
                START:  BRK
                """, 0x8000));
    }

    @Test
    public void undefinedLabelReferenceThrows() {
        assertThrows(AssemblyException.class, () -> Assembler.assemble("BNE MISSING", 0x8000));
    }

    @Test
    public void branchOffsetOfExactlyPositive127Succeeds() {
        final String source = "BEQ TARGET\n" + "NOP\n".repeat(127) + "TARGET: BRK\n";

        final AssembledProgram program = Assembler.assemble(source, 0x8000);

        assertEquals(0x7F, program.bytes()[1]);
    }

    @Test
    public void branchOffsetOfPositive128FailsRangeCheck() {
        final String source = "BEQ TARGET\n" + "NOP\n".repeat(128) + "TARGET: BRK\n";

        assertThrows(AssemblyException.class, () -> Assembler.assemble(source, 0x8000));
    }

    @Test
    public void branchOffsetOfExactlyNegative128Succeeds() {
        final String source = "TARGET:\n" + "NOP\n".repeat(126) + "BEQ TARGET\n";

        final AssembledProgram program = Assembler.assemble(source, 0x8000);

        assertEquals(0x80, program.bytes()[program.length() - 1]);
    }

    @Test
    public void branchOffsetOfNegative129FailsRangeCheck() {
        final String source = "TARGET:\n" + "NOP\n".repeat(127) + "BEQ TARGET\n";

        assertThrows(AssemblyException.class, () -> Assembler.assemble(source, 0x8000));
    }

    @Test
    public void assemblesTheExactSampleProgramFromTheIntegrationTest() {
        final String simpleProgram = """
                                LDX #$00      ; X = 0
                                LDA #$09      ; A = value to store

                        LOOP:   STA $0200,X   ; Store A at $0200 + X
                                INX           ; X = X + 1
                                CPX #$FF      ; Have we reached the end?
                                BNE LOOP      ; No, continue

                                BRK           ; Stop
                        """;

        final AssembledProgram program = Assembler.assemble(simpleProgram, 0x8000);

        assertArrayEquals(new int[]{
                0xA2, 0x00,       // LDX #$00
                0xA9, 0x09,       // LDA #$09
                0x9D, 0x00, 0x02, // LOOP: STA $0200,X
                0xE8,             // INX
                0xE0, 0xFF,       // CPX #$FF
                0xD0, 0xF8,       // BNE LOOP (offset -8)
                0x00              // BRK
        }, program.bytes());

        assertEquals(Map.of("LOOP", 0x8004), program.labels());
        assertEquals(0x8000, program.startAddress());
        assertEquals(13, program.length());
        assertEquals(0x800D, program.endAddress());
    }
}
