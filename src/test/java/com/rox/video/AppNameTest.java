package com.rox.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppNameTest {
    @SuppressWarnings("unused") //set reflectively
    private static String windowClass = "com-rox-Main";
    private static final String CONSTANT = "fixed";

    @Test
    void setsAStaticField(){
        assertTrue(AppName.setStaticField(AppNameTest.class, "windowClass", "EmuRoxLab"));

        assertEquals("EmuRoxLab", windowClass);
    }

    @Test
    void aMissingFieldIsNotSet(){
        assertFalse(AppName.setStaticField(AppNameTest.class, "noSuchField", "EmuRoxLab"));
    }

    @Test
    void aFieldThatCannotBeWrittenIsNotSet(){
        assertFalse(AppName.setStaticField(AppNameTest.class, "CONSTANT", "EmuRoxLab"));
        assertEquals("fixed", CONSTANT);
    }

    @Test
    void aPackageThatIsNotOpenIsNotSet(){
        assertFalse(AppName.setStaticField(String.class, "COMPACT_STRINGS", false));
    }

    @Test
    void offX11NothingChanges(){
        //tests run headless: not the X11 toolkit
        assertDoesNotThrow(() -> AppName.set("EmuRoxLab"));
    }
}
