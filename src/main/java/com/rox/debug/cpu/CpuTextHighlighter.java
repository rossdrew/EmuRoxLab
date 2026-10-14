package com.rox.debug.cpu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds what to color in {@link CpuStateText#panel} output. Spans may overlap: a background kind
 * ({@link Kind#CHANGED}, {@link Kind#CURRENT_LINE}) layers over a text-colour kind. On instruction lines
 * carrying a state, each value that differs from the next such line down is marked {@link Kind#CHANGED}.
 */
public final class CpuTextHighlighter {
    public enum Kind {
        PAUSED, REGISTER_NAME, REGISTER_VALUE, CHANGED, FLAG_SET, FLAG_CLEAR, ACTIVE, HEADER,
        CURRENT_LINE, ADDRESS, JUMP_FLASH, FLOW, LOAD_STORE, ARITHMETIC, FLAGS_AND_STACK, IMMEDIATE, OPERAND, UNKNOWN
    }

    /** {@code length} characters of the text from {@code start}. */
    public record Span(int start, int length, Kind kind) {
    }

    private static final Set<String> FLOW = Set.of("JMP", "JSR", "RTS", "RTI", "BRK",
            "BCC", "BCS", "BEQ", "BMI", "BNE", "BPL", "BVC", "BVS");
    private static final Set<String> LOAD_STORE = Set.of("LDA", "LDX", "LDY", "STA", "STX", "STY",
            "TAX", "TAY", "TXA", "TYA", "TSX", "TXS");
    private static final Set<String> ARITHMETIC = Set.of("ADC", "SBC", "AND", "ORA", "EOR", "CMP", "CPX", "CPY",
            "BIT", "ASL", "LSR", "ROL", "ROR", "INC", "DEC", "INX", "INY", "DEX", "DEY");
    private static final Set<String> FLAGS_AND_STACK = Set.of("CLC", "SEC", "CLI", "SEI", "CLV", "CLD", "SED",
            "PHA", "PLA", "PHP", "PLP", "NOP");

    private static final Pattern REGISTER = Pattern.compile("(PC|A|X|Y|SP): (\\$[0-9A-F]+)");
    private static final Pattern ACTIVE = Pattern.compile("\\b(asserted|yes)\\b");
    private static final Pattern INSTRUCTION = Pattern.compile("([> ]) (\\$[0-9A-F]{4})  (\\S+(?: \\S+)?)(?: +(A:.*))?");
    private static final Pattern LINE_REGISTER = Pattern.compile("(A|X|Y|SP):([0-9A-F]{2})");
    private static final Pattern LINE_FLAGS = Pattern.compile("[Nn][Vv]-[Bb][Dd][Ii][Zz][Cc]");
    private static final Pattern LINE_INTERRUPT = Pattern.compile("\\b(irq|nmi)\\b");
    private static final String FLAG_NAMES = "NV-BDIZC";
    private static final String FLAGS_PREFIX = "Flags: ";
    private static final int FLAG_COUNT = 8; //NV-BDIZC

    private CpuTextHighlighter(){
    }

    /**
     * @param changed register/flag names to mark as {@link Kind#CHANGED} - see {@link RegisterChanges}
     */
    public static List<Span> highlight(final String text, final Set<String> changed){
        return highlight(text, changed, false);
    }

    /** @param flashCurrentAddress also mark the current instruction's address as {@link Kind#JUMP_FLASH} - see {@link JumpFlash} */
    public static List<Span> highlight(final String text, final Set<String> changed, final boolean flashCurrentAddress){
        final List<Span> spans = new ArrayList<>();
        final List<LineState> states = new ArrayList<>();
        int lineStart = 0;
        for (final String line : text.split("\n", -1)){
            highlightLine(line, lineStart, changed, flashCurrentAddress, spans, states);
            lineStart += line.length() + 1;
        }
        for (int i = 0; i + 1 < states.size(); i++){
            markChangesFromBelow(states.get(i), states.get(i + 1), spans);
        }
        return spans;
    }

    /** One instruction line's state values, by name ({@code A}, {@code N}, {@code irq}...), with where each sits. */
    private record LineState(Map<String, String> values, Map<String, Integer> offsets) {
    }

    private static void markChangesFromBelow(final LineState line, final LineState below, final List<Span> spans){
        line.values().forEach((name, value) -> {
            if (!value.equals(below.values().get(name))){
                spans.add(new Span(line.offsets().get(name), value.length(), Kind.CHANGED));
            }
        });
    }

    private static void highlightLine(final String line, final int offset, final Set<String> changed,
                                      final boolean flashCurrentAddress, final List<Span> spans,
                                      final List<LineState> states){
        if (line.startsWith("PAUSED")){
            spans.add(new Span(offset, "PAUSED".length(), Kind.PAUSED));
        } else if (line.startsWith("PC: ")){
            final Matcher register = REGISTER.matcher(line);
            while (register.find()){
                spans.add(new Span(offset + register.start(1), register.group(1).length(), Kind.REGISTER_NAME));
                spans.add(new Span(offset + register.start(2), register.group(2).length(), Kind.REGISTER_VALUE));
                if (changed.contains(register.group(1))){
                    spans.add(new Span(offset + register.start(2), register.group(2).length(), Kind.CHANGED));
                }
            }
        } else if (line.startsWith(FLAGS_PREFIX)){
            for (int i = 0; i < FLAG_COUNT; i++){
                final int column = FLAGS_PREFIX.length() + i;
                final char flag = line.charAt(column);
                if (flag == '-'){
                    continue;
                }
                spans.add(new Span(offset + column, 1, Character.isUpperCase(flag) ? Kind.FLAG_SET : Kind.FLAG_CLEAR));
                if (changed.contains(String.valueOf(Character.toUpperCase(flag)))){
                    spans.add(new Span(offset + column, 1, Kind.CHANGED));
                }
            }
        } else if (line.startsWith("IRQ line: ")){
            final Matcher active = ACTIVE.matcher(line);
            while (active.find()){
                spans.add(new Span(offset + active.start(), active.group().length(), Kind.ACTIVE));
            }
        } else if (line.startsWith("---")){
            spans.add(new Span(offset, line.length(), Kind.HEADER));
        } else {
            highlightInstruction(line, offset, flashCurrentAddress, spans, states);
        }
    }

    private static void highlightInstruction(final String line, final int offset, final boolean flashCurrentAddress,
                                             final List<Span> spans, final List<LineState> states){
        final Matcher instruction = INSTRUCTION.matcher(line);
        if (!instruction.matches()){
            return;
        }
        if (instruction.group(1).equals(">")){
            spans.add(new Span(offset, line.length(), Kind.CURRENT_LINE));
        }
        spans.add(new Span(offset + instruction.start(2), instruction.group(2).length(), Kind.ADDRESS));
        if (flashCurrentAddress && instruction.group(1).equals(">")){
            spans.add(new Span(offset + instruction.start(2), instruction.group(2).length(), Kind.JUMP_FLASH));
        }
        if (instruction.group(4) != null){
            states.add(highlightState(instruction.group(4), offset + instruction.start(4), spans));
        }
        final String code = instruction.group(3);
        final int codeStart = offset + instruction.start(3);
        if (code.startsWith("???")){
            spans.add(new Span(codeStart, code.length(), Kind.UNKNOWN));
            return;
        }
        final String[] parts = code.split(" ", 2);
        final String mnemonic = parts[0];
        final Kind kind = mnemonicKind(mnemonic);
        if (kind != null){
            spans.add(new Span(codeStart, mnemonic.length(), kind));
        }
        if (parts.length == 2){
            final String operand = parts[1];
            final int operandStart = codeStart + mnemonic.length() + 1;
            if (operand.startsWith("#")){
                spans.add(new Span(operandStart, operand.length(), Kind.IMMEDIATE));
            } else if (operand.contains("$")){
                spans.add(new Span(operandStart, operand.length(), Kind.OPERAND));
            }
        }
    }

    /** Colours an instruction line's state, returning its values for the line-to-line comparison. */
    private static LineState highlightState(final String state, final int offset, final List<Span> spans){
        final Map<String, String> values = new HashMap<>();
        final Map<String, Integer> offsets = new HashMap<>();
        final Matcher register = LINE_REGISTER.matcher(state);
        while (register.find()){
            spans.add(new Span(offset + register.start(1), register.group(1).length(), Kind.REGISTER_NAME));
            spans.add(new Span(offset + register.start(2), register.group(2).length(), Kind.REGISTER_VALUE));
            values.put(register.group(1), register.group(2));
            offsets.put(register.group(1), offset + register.start(2));
        }
        final Matcher flags = LINE_FLAGS.matcher(state);
        if (flags.find()){
            for (int i = 0; i < FLAG_NAMES.length(); i++){
                final char flag = flags.group().charAt(i);
                if (flag == '-'){
                    continue;
                }
                final int at = offset + flags.start() + i;
                spans.add(new Span(at, 1, Character.isUpperCase(flag) ? Kind.FLAG_SET : Kind.FLAG_CLEAR));
                values.put(String.valueOf(FLAG_NAMES.charAt(i)), String.valueOf(flag));
                offsets.put(String.valueOf(FLAG_NAMES.charAt(i)), at);
            }
        }
        final Matcher interrupt = LINE_INTERRUPT.matcher(state);
        while (interrupt.find()){
            spans.add(new Span(offset + interrupt.start(), interrupt.group().length(), Kind.ACTIVE));
            values.put(interrupt.group(), interrupt.group());
            offsets.put(interrupt.group(), offset + interrupt.start());
        }
        return new LineState(values, offsets);
    }

    private static Kind mnemonicKind(final String mnemonic){
        if (FLOW.contains(mnemonic)){
            return Kind.FLOW;
        }
        if (LOAD_STORE.contains(mnemonic)){
            return Kind.LOAD_STORE;
        }
        if (ARITHMETIC.contains(mnemonic)){
            return Kind.ARITHMETIC;
        }
        if (FLAGS_AND_STACK.contains(mnemonic)){
            return Kind.FLAGS_AND_STACK;
        }
        return null;
    }
}
