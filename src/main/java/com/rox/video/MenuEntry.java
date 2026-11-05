package com.rox.video;

/**
 * One item in a menu filled as it opens - see {@link SwingVideoOutput#fillMenuOnOpen}.
 *
 * @param action what choosing it does, or {@code null} for an item shown greyed out
 */
public record MenuEntry(String label, Runnable action) {
    /** A dividing line. */
    public static final MenuEntry SEPARATOR = new MenuEntry(null, null);

    /** Shown, but greyed out - e.g. to say a list is empty. */
    public static MenuEntry disabled(final String label){
        return new MenuEntry(label, null);
    }
}
