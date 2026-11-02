package com.rox.debug.breakpoints;

import com.rox.debug.labels.MemoryLabel;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Typing and showing a breakpoint or watchpoint address - as hex, or by a {@link MemoryLabel}'s name. */
public final class AddressText {
    private static final Pattern HEX = Pattern.compile("(?:\\$|0[xX])?([0-9A-Fa-f]{1,4})");

    private AddressText(){
    }

    /**
     * {@code $C123}, {@code 0xC123} or {@code C123} - or a label's name (its first byte). A name that also reads as
     * hex, like {@code BEEF}, is the label.
     */
    public static OptionalInt parse(final String text, final List<MemoryLabel> labels){
        final String trimmed = text.trim();
        final Optional<MemoryLabel> label = labels.stream().filter(l -> l.name().equals(trimmed)).findFirst();
        if (label.isPresent()){
            return OptionalInt.of(label.get().start());
        }
        final var hex = HEX.matcher(trimmed);
        return hex.matches() ? OptionalInt.of(Integer.parseInt(hex.group(1), 16)) : OptionalInt.empty();
    }

    /** {@code $0042}, plus the name of the first label covering it: {@code $0042 PLAYER_HP}. */
    public static String describe(final int address, final List<MemoryLabel> labels){
        final String hex = String.format("$%04X", address);
        return labels.stream()
                .filter(l -> l.start() <= address && address <= l.end())
                .findFirst()
                .map(l -> hex + " " + l.name())
                .orElse(hex);
    }
}
