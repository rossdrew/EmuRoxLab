package com.rox.cpu.mos6502.assembler;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits 6502 assembly source into comment-stripped, label/mnemonic/operand-separated {@link SourceLine}s.
 */
final class LineScanner {
    private static final String ROL_COMMENT_START = ";";
    private static final String LABEL_END = ":";

    private LineScanner() {
    }

    /**
     * @param source a textual MOS6502 program
     * @return A {@link List} of {@link SourceLine} representing the comment-stripped program
     */
    static List<SourceLine> scan(final String source) {
        final List<SourceLine> scannedLines = new ArrayList<>();
        final List<String> rawLines = source.lines().toList();

        for (int i = 0; i < rawLines.size(); i++) {
            final String lineWithoutComment = rawLines.get(i).split(ROL_COMMENT_START, 2)[0].strip();

            if (!lineWithoutComment.isEmpty()) {
                scannedLines.add(parseLine(i + 1, lineWithoutComment));
            }
        }

        return scannedLines;
    }

    /**
     * Parse out a {@link SourceLine} which can be a label or a label and content
     */
    private static SourceLine parseLine(final int lineNumber, final String trimmed) {
        String lineWithoutLabel = trimmed;
        String labelForThisLine = null;

        final int endLabelIndex = lineWithoutLabel.indexOf(LABEL_END);
        if (endLabelIndex != -1) {
            labelForThisLine = lineWithoutLabel.substring(0, endLabelIndex).trim();
            lineWithoutLabel = lineWithoutLabel.substring(endLabelIndex + 1).trim();
        }

        if (lineWithoutLabel.isEmpty()) {
            //Label only line
            return new SourceLine(lineNumber, labelForThisLine, null, "");
        }

        final String[] parts = lineWithoutLabel.split("\\s+", 2);
        final String operationMnemonic = parts[0].toUpperCase();
        final String operandText = parts.length > 1 ? parts[1].trim() : "";

        //Content line, possibly with label
        return new SourceLine(lineNumber, labelForThisLine, operationMnemonic, operandText);
    }
}
