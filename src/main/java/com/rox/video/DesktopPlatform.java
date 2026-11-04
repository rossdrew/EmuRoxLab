package com.rox.video;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.util.Optional;
import java.util.function.Consumer;

/** What {@link AppIcon} and {@link AppName} ask of the real desktop - none of it exists in a headless test. */
final class DesktopPlatform {
    static final String X11_TOOLKIT = "sun.awt.X11.XToolkit";

    private DesktopPlatform(){
    }

    /** Sets the dock's icon, or {@code null} where there's no dock to set (anywhere but macOS). */
    static Consumer<Image> dockIcon(){
        return Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)
                ? Taskbar.getTaskbar()::setIconImage
                : null;
    }

    /** The toolkit's class, if it's X11's - including under XWayland. */
    static Optional<Class<?>> x11Toolkit(){
        final Class<?> toolkit = Toolkit.getDefaultToolkit().getClass();
        return toolkit.getName().equals(X11_TOOLKIT) ? Optional.of(toolkit) : Optional.empty();
    }
}
