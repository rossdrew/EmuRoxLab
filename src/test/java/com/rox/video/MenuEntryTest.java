package com.rox.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuEntryTest {
    @Test
    void aDisabledEntryHasALabelButNoAction(){
        final MenuEntry entry = MenuEntry.disabled("No recent ROMs");

        assertEquals("No recent ROMs", entry.label());
        assertNull(entry.action());
    }

    @Test
    void theSeparatorIsNeitherLabelledNorChosen(){
        assertNull(MenuEntry.SEPARATOR.label());
        assertNull(MenuEntry.SEPARATOR.action());
    }
}
