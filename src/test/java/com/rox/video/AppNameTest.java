package com.rox.video;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AppNameTest {
    private static String windowClass = "com-rox-Main";

    /** Stands in for X11's toolkit, holding its window-class field. */
    private static final class FakeX11Toolkit {
        @SuppressWarnings("unused") //set reflectively
        private static String awtAppClassName = "com-rox-Main";
    }
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
    void onX11TheWindowClassIsSet(){
        final List<String> warnings = new ArrayList<>();

        AppName.set("EmuRoxLab", Optional.of(FakeX11Toolkit.class), warnings::add);

        assertEquals("EmuRoxLab", FakeX11Toolkit.awtAppClassName);
        assertEquals(List.of(), warnings);
    }

    @Test
    void onX11WithoutTheFieldOpenItWarnsHowToFixIt(){
        final List<String> warnings = new ArrayList<>();

        AppName.set("EmuRoxLab", Optional.of(String.class), warnings::add);

        assertEquals(List.of("Windows keep the default name - run with " + AppName.OPEN_FLAG + " to call them EmuRoxLab"), warnings);
    }

    @Test
    void offX11NothingIsSetOrWarned(){
        AppName.set("EmuRoxLab", Optional.empty(), warning -> fail("warned: " + warning));
    }

    @Test
    void offX11NothingChanges(){
        //tests run headless: not the X11 toolkit
        assertDoesNotThrow(() -> AppName.set("EmuRoxLab"));
    }
}
