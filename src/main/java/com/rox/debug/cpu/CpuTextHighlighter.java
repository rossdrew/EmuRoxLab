package com.rox.debug.cpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds what to color in {@link CpuStateText#panel} output. Spans may overlap: a background kind
 * ({@link Kind#CHANGED}, {@link Kind#CURRENT_LINE}) layers over a text-colour kind.
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
    private static final Pattern INSTRUCTION = Pattern.compile("([> ]) (\\$[0-9A-F]{4})  (.*)");
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
        int lineStart = 0;
        for (final String line : text.split("\n", -1)){
            highlightLine(line, lineStart, changed, flashCurrentAddress, spans);
            lineStart += line.length() + 1;
        }
        return spans;
    }

    private static void highlightLine(final String line, final int offset, final Set<String> changed,
                                      final boolean flashCurrentAddress, final List<Span> spans){
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
            highlightInstruction(line, offset, flashCurrentAddress, spans);
        }
    }

    private static void highlightInstruction(final String line, final int offset, final boolean flashCurrentAddress,
                                             final List<Span> spans){
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
