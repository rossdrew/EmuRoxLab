package com.rox.video;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The app's name as the desktop shows it. On X11 (including XWayland) GNOME's dock and Alt-Tab name a
 * Java window after its window class, which AWT takes from the main class - {@code com-rox-RomVideoSmokeDemo}
 * - with no public way to change it.
 */
public final class AppName {
    static final String X11_WINDOW_CLASS_FIELD = "awtAppClassName";
    static final String OPEN_FLAG = "--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED";

    private AppName(){
    }

    /**
     * Names every window created from now on - so call it before the first. Only X11 needs this; there
     * it needs {@value #OPEN_FLAG}, and without that the default name stays, with a warning.
     */
    public static void set(final String name){
        set(name, DesktopPlatform.x11Toolkit(), System.err::println);
    }

    /** @param x11Toolkit X11's toolkit class, if that's the toolkit in use */
    static void set(final String name, final Optional<Class<?>> x11Toolkit, final Consumer<String> warn){
        x11Toolkit.ifPresent(toolkit -> {
            if (!setStaticField(toolkit, X11_WINDOW_CLASS_FIELD, name)){
                warn.accept("Windows keep the default name - run with " + OPEN_FLAG + " to call them " + name);
            }
        });
    }

    /** @return whether it could be set - the field must exist and its package be open to this one */
    static boolean setStaticField(final Class<?> holder, final String field, final Object value){
        try {
            final Field target = holder.getDeclaredField(field);
            target.setAccessible(true);
            target.set(null, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e){
            //RuntimeException: InaccessibleObjectException, for a package that isn't open
            return false;
        }
    }
}
