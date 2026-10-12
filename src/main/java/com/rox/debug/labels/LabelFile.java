package com.rox.debug.labels;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ROM's memory labels as plain, hand-editable text - one label per line:
 * <pre>
 * # Zelda
 * $066D rupees
 * $0670-$0671 key_count   # inline comments are fine too
 * </pre>
 * Addresses are hex, with or without a leading {@code $}; a range is inclusive at both ends. Blank lines
 * and {@code #} comments are ignored - and not kept when the file is {@link #write written} back.
 */
public final class LabelFile {
    private static final String COMMENT = "#";
    private static final String HEX_PREFIX = "$";
    private static final String RANGE_SEPARATOR = "-";
    private static final int HEX_RADIX = 16;
    private static final int MAX_HEX_DIGITS = 4;
    static final String HEADER = "# EmuRoxLab memory labels: '<address> <name>' or '<start>-<end> <name>', hex addresses."
            + " Comments aren't kept when saved from the Watch window.";

    private LabelFile(){
    }

    /**
     * @throws LabelFileException at the first line that isn't a valid, uniquely-named label
     */
    public static List<MemoryLabel> parse(final List<String> lines) throws LabelFileException {
        final List<MemoryLabel> labels = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (int i = 0; i < lines.size(); i++){
            final int lineNumber = i + 1;
            final String content = stripComment(lines.get(i)).trim();
            if (content.isEmpty()){
                continue;
            }
            final MemoryLabel label = parseLabel(content, lineNumber);
            if (!names.add(label.name())){
                throw new LabelFileException(lineNumber, "'" + label.name() + "' is already used by an earlier label");
            }
            labels.add(label);
        }
        return labels;
    }

    /** One line per label, in the given order: {@code $066D rupees} or {@code $0670-$0671 key_count}. */
    public static List<String> format(final List<MemoryLabel> labels){
        final List<String> lines = new ArrayList<>(labels.size());
        for (final MemoryLabel label : labels){
            final String address = label.length() == 1
                    ? String.format("$%04X", label.start())
                    : String.format("$%04X-$%04X", label.start(), label.end());
            lines.add(address + " " + label.name());
        }
        return lines;
    }

    /** The labels in {@code path}, or none if it doesn't exist yet. */
    public static List<MemoryLabel> read(final Path path) throws IOException, LabelFileException {
        if (!Files.exists(path)){
            return List.of();
        }
        return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    /**
     * Replaces {@code path}'s contents (creating its folder if needed) - via a temporary file and an
     * atomic move, so a crash mid-save can't leave a half-written labels file.
     */
    public static void write(final Path path, final List<MemoryLabel> labels) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.addAll(format(labels));
        //a temporary file unique to this write (in the same folder, so the move stays atomic), always cleaned
        //up - two saves at once can't move each other's file away, and a failed write leaves nothing behind
        final Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName() + "-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String stripComment(final String line){
        final int comment = line.indexOf(COMMENT);
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static MemoryLabel parseLabel(final String content, final int lineNumber) throws LabelFileException {
        final String[] parts = content.split("\\s+");
        if (parts.length != 2){
            throw new LabelFileException(lineNumber, "expected '<address> <name>' or '<start>-<end> <name>', got '" + content + "'");
        }
        final String[] range = parts[0].split(RANGE_SEPARATOR, -1);
        if (range.length > 2){
            throw new LabelFileException(lineNumber, "'" + parts[0] + "' isn't an address or a <start>-<end> range");
        }
        final int start = parseAddress(range[0], lineNumber);
        final int end = range.length == 2 ? parseAddress(range[1], lineNumber) : start;
        if (end < start){
            throw new LabelFileException(lineNumber, String.format("range ends ($%04X) before it starts ($%04X)", end, start));
        }
        try {
            return MemoryLabel.spanning(parts[1], start, end);
        } catch (IllegalArgumentException e){
            throw new LabelFileException(lineNumber, e.getMessage());
        }
    }

    private static int parseAddress(final String text, final int lineNumber) throws LabelFileException {
        final String digits = text.startsWith(HEX_PREFIX) ? text.substring(HEX_PREFIX.length()) : text;
        //checked by hand rather than trusting parseInt, which would also accept a sign ("+1F")
        if (digits.isEmpty() || digits.length() > MAX_HEX_DIGITS
                || !digits.chars().allMatch(c -> Character.digit(c, HEX_RADIX) >= 0)){
            throw new LabelFileException(lineNumber, "'" + text + "' isn't a hex address ($0000-$FFFF)");
        }
        return Integer.parseInt(digits, HEX_RADIX);
    }
}
