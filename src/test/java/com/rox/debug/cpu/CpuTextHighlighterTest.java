package com.rox.debug.cpu;

import com.rox.cpu.mos6502.MOS6502Snapshot;
import com.rox.cpu.mos6502.assembler.DisassembledInstruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static com.rox.debug.cpu.CpuTextHighlighter.Kind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CpuTextHighlighterTest {
    /** Each span as "KIND:covered text", in order - readable where raw offsets aren't. */
    private static List<String> spans(final String text, final Set<String> changed){
        return CpuTextHighlighter.highlight(text, changed).stream()
                .map(s -> s.kind() + ":" + text.substring(s.start(), s.start() + s.length()))
                .toList();
    }

    private static List<String> spans(final String text){
        return spans(text, Set.of());
    }

    @Test
    public void pausedIsMarkedRunningIsNot(){
        assertEquals(List.of("PAUSED:PAUSED"), spans("PAUSED  Clock: 0 Hz of 1,789,773 Hz (0.0%)"));
        assertEquals(List.of(), spans("Running  Clock: 1,789,773 Hz of 1,789,773 Hz (100.0%)"));
    }

    @Test
    public void registersGetNameAndValueAndChangedValuesAreMarked(){
        final String line = "PC: $8123  A: $01  X: $02  Y: $03  SP: $FD";

        assertEquals(List.of(
                "REGISTER_NAME:PC", "REGISTER_VALUE:$8123", "CHANGED:$8123",
                "REGISTER_NAME:A", "REGISTER_VALUE:$01",
                "REGISTER_NAME:X", "REGISTER_VALUE:$02", "CHANGED:$02",
                "REGISTER_NAME:Y", "REGISTER_VALUE:$03",
                "REGISTER_NAME:SP", "REGISTER_VALUE:$FD", "CHANGED:$FD"), spans(line, Set.of("PC", "X", "SP")));
    }

    @Test
    public void flagsAreSetOrClearAndChangedFlagsAreMarked(){
        assertEquals(List.of("FLAG_SET:N", "FLAG_CLEAR:v", "CHANGED:v", "FLAG_SET:B", "FLAG_CLEAR:d",
                        "FLAG_SET:I", "FLAG_CLEAR:z", "FLAG_SET:C", "CHANGED:C"),
                spans("Flags: Nv-BdIzC  (NV-BDIZC)", Set.of("V", "C")));
    }

    @Test
    public void activeInterruptLinesAreMarked(){
        assertEquals(List.of("ACTIVE:asserted", "ACTIVE:yes"), spans("IRQ line: asserted  NMI pending: yes"));
        assertEquals(List.of(), spans("IRQ line: clear  NMI pending: no"));
    }

    @Test
    public void headersAreMarkedWhole(){
        assertEquals(List.of("HEADER:--- Next (furthest ahead at top) ---"), spans("--- Next (furthest ahead at top) ---"));
    }

    @Test
    public void theCurrentInstructionLineIsMarkedWhole(){
        assertEquals(List.of("CURRENT_LINE:> $E45B  JMP $E45B", "ADDRESS:$E45B", "FLOW:JMP", "OPERAND:$E45B"),
                spans("> $E45B  JMP $E45B"));
    }

    @ParameterizedTest
    @CsvSource({"JMP $9000, FLOW", "BNE $8000, FLOW", "RTS, FLOW", "LDA #$05, LOAD_STORE", "TXS, LOAD_STORE",
            "ADC #$01, ARITHMETIC", "INX, ARITHMETIC", "SEC, FLAGS_AND_STACK", "PHA, FLAGS_AND_STACK", "NOP, FLAGS_AND_STACK"})
    public void mnemonicsAreColouredByKind(final String code, final Kind kind){
        final List<String> spans = spans("  $8000  " + code);

        assertEquals("ADDRESS:$8000", spans.get(0));
        assertEquals(kind + ":" + code.split(" ")[0], spans.get(1));
    }

    @Test
    public void immediateAndMemoryOperandsAreDistinguished(){
        assertEquals(List.of("ADDRESS:$8000", "LOAD_STORE:LDA", "IMMEDIATE:#$05"), spans("  $8000  LDA #$05"));
        assertEquals(List.of("ADDRESS:$8000", "ARITHMETIC:ORA", "OPERAND:($40),Y"), spans("  $8000  ORA ($40),Y"));
        assertEquals(List.of("ADDRESS:$8000", "ARITHMETIC:ASL"), spans("  $8000  ASL A"), "accumulator isn't an operand address");
    }

    @Test
    public void unknownBytesAreMarkedWhole(){
        assertEquals(List.of("ADDRESS:$8000", "UNKNOWN:??? ($FF)"), spans("  $8000  ??? ($FF)"));
    }

    @Test
    public void anUnrecognisedMnemonicStillGetsItsAddressAndOperand(){
        assertEquals(List.of("ADDRESS:$8000", "OPERAND:$12"), spans("  $8000  XYZ $12"));
    }

    @Test
    public void otherLinesAreLeftAlone(){
        assertEquals(List.of(), spans(""));
        assertEquals(List.of(), spans("something else entirely"));
    }

    /** Offsets carry across lines, against real CpuStateText output. */
    @Test
    public void spansLineUpWithTheRealPanelText(){
        final MOS6502Snapshot cpu = new MOS6502Snapshot(0x8123, 1, 2, 3, 0xFD, 0, 0, 0,
                true, false, true, false, true, false, true, false, true);
        final String text = String.join("\n",
                CpuStateText.registers(cpu),
                "Flags: " + CpuStateText.flags(cpu) + "  (NV-BDIZC)",
                CpuStateText.instruction(new DisassembledInstruction(0x8123, 3, "JMP", new int[]{0x00, 0x90}, "JMP $9000"), true));

        final List<String> spans = spans(text, Set.of("A", "N"));

        assertTrue(spans.contains("CHANGED:$01"), spans.toString());
        assertTrue(spans.contains("CHANGED:N"), spans.toString());
        assertTrue(spans.contains("CURRENT_LINE:> $8123  JMP $9000"), spans.toString());
        assertTrue(spans.contains("OPERAND:$9000"), spans.toString());
    }

    @Test
    public void aJumpFlashMarksOnlyTheCurrentInstructionsAddress(){
        final String text = "> $8000  JMP $9000\n  $9000  INX";

        final List<String> flashing = CpuTextHighlighter.highlight(text, Set.of(), true).stream()
                .filter(s -> s.kind() == Kind.JUMP_FLASH)
                .map(s -> text.substring(s.start(), s.start() + s.length()))
                .toList();

        assertEquals(List.of("$8000"), flashing);
        assertTrue(CpuTextHighlighter.highlight(text, Set.of(), false).stream().noneMatch(s -> s.kind() == Kind.JUMP_FLASH));
    }

    // --- per-line state ---

    /** Just the CHANGED spans, as covered text, in text order. */
    private static List<String> changedIn(final String text){
        return CpuTextHighlighter.highlight(text, Set.of()).stream()
                .filter(s -> s.kind() == Kind.CHANGED)
                .sorted(java.util.Comparator.comparingInt(CpuTextHighlighter.Span::start))
                .map(s -> s.start() + ":" + text.substring(s.start(), s.start() + s.length()))
                .toList();
    }

    @Test
    public void aLinesStateIsColouredLikeTheHeaderRegisters(){
        final List<String> spans = spans("  $8121  LDA #$05       A:10 X:02 Y:00 SP:FF  nv-bdIzC  irq");

        assertEquals(List.of("ADDRESS:$8121", "REGISTER_NAME:A", "REGISTER_VALUE:10", "REGISTER_NAME:X", "REGISTER_VALUE:02",
                "REGISTER_NAME:Y", "REGISTER_VALUE:00", "REGISTER_NAME:SP", "REGISTER_VALUE:FF",
                "FLAG_CLEAR:n", "FLAG_CLEAR:v", "FLAG_CLEAR:b", "FLAG_CLEAR:d", "FLAG_SET:I", "FLAG_CLEAR:z", "FLAG_SET:C",
                "ACTIVE:irq", "LOAD_STORE:LDA", "IMMEDIATE:#$05"), spans);
    }

    @Test
    public void eachLinesValuesThatDifferFromTheLineBelowAreMarked(){
        final String text = String.join("\n",
                "  $8122  STA $10        A:11 X:02 Y:00 SP:FF  nv-bdIzc",
                "  $8121  ADC #$01       A:10 X:02 Y:00 SP:FF  nv-bdIzC",
                "  $8120  INX            A:10 X:01 Y:00 SP:FF  nv-bdIzC");

        final List<String> changed = changedIn(text);

        assertEquals(3, changed.size(), changed.toString());
        assertTrue(changed.get(0).endsWith(":11"), "A changed by the ADC below it: " + changed);
        assertTrue(changed.get(1).endsWith(":c"), "and so did C: " + changed);
        assertTrue(changed.get(2).endsWith(":02"), "X changed by the INX below: " + changed);
    }

    @Test
    public void theLowestLineHasNothingBelowToCompareWith(){
        assertTrue(changedIn("  $8120  INX            A:10 X:01 Y:00 SP:FF  nv-bdIzC").isEmpty());
    }

    @Test
    public void anInterruptAppearingIsMarkedButOneGoingAwayHasNothingToMark(){
        final String appeared = "  $8121  NOP            A:00 X:00 Y:00 SP:FF  nv-bdizc  nmi\n"
                + "  $8120  NOP            A:00 X:00 Y:00 SP:FF  nv-bdizc";
        final String cleared = "  $8121  NOP            A:00 X:00 Y:00 SP:FF  nv-bdizc\n"
                + "  $8120  NOP            A:00 X:00 Y:00 SP:FF  nv-bdizc  nmi";

        assertEquals(1, changedIn(appeared).size());
        assertTrue(changedIn(appeared).get(0).endsWith(":nmi"));
        assertTrue(changedIn(cleared).isEmpty());
    }

    /** The current line sits above the Recent header - the header mustn't break the comparison. */
    @Test
    public void theCurrentLineIsComparedWithTheLatestHistoryLineAcrossTheHeader(){
        final String text = String.join("\n",
                "  $8124  NOP",
                "> $8123  NOP            A:01 X:02 Y:03 SP:FD  nv-bdizc",
                "--- Recent (latest at top) ---",
                "  $8121  LDA #$01       A:00 X:02 Y:03 SP:FD  nv-bdizc");

        final List<String> changed = changedIn(text);

        assertEquals(1, changed.size(), changed.toString());
        assertTrue(changed.get(0).endsWith(":01"));
    }

    @Test
    public void spansLineUpWithRealStateLines(){
        final com.rox.cpu.mos6502.MOS6502Snapshot before = com.rox.cpu.mos6502.MOS6502Snapshot.unpack(0xFFFF,
                com.rox.cpu.mos6502.MOS6502Snapshot.packRegisters(0xAB, 0, 0, 0xFD), 0);
        final String text = CpuStateText.instruction(new DisassembledInstruction(0xFFFF, 2, "ORA", new int[]{0x40}, "ORA ($40),Y"),
                false, before);

        final List<String> spans = spans(text);

        assertTrue(spans.contains("REGISTER_VALUE:AB"), spans.toString());
        assertTrue(spans.contains("OPERAND:($40),Y"), spans.toString());
    }
}
