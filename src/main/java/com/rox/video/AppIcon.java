package com.rox.video;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/** EmuRoxLab's icon - the scavenged NES, square - for every window's title bar, dock and Alt-Tab entry. */
public final class AppIcon {
    /** Sizes window managers commonly pick from: each picks the nearest rather than rescaling one. */
    static final List<Integer> SIZES = List.of(16, 24, 32, 48, 64, 128, 256);
    private static final List<Image> IMAGES = images(readIcon("emuroxlab_icon.png"));

    private AppIcon(){
    }

    /** Gives {@code window} the icon - and the dock too, where the platform has one (macOS). */
    public static void applyTo(final Window window){
        applyTo(window, IMAGES, DesktopPlatform.dockIcon());
    }

    /** @param dockIcon sets the dock's icon, or {@code null} where there's no dock to set */
    static void applyTo(final Window window, final List<Image> images, final Consumer<Image> dockIcon){
        if (images.isEmpty()){
            return;
        }
        window.setIconImages(images);
        if (dockIcon != null){
            dockIcon.accept(images.get(images.size() - 1));
        }
    }

    /** {@code source} at each of {@link #SIZES}, area-averaged; none for {@code null}. */
    static List<Image> images(final BufferedImage source){
        if (source == null){
            return List.of();
        }
        return SIZES.stream().<Image>map(size -> scaled(source, size)).toList();
    }

    private static BufferedImage scaled(final BufferedImage source, final int size){
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.drawImage(source.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return image;
    }

    static BufferedImage readIcon(final String resource){
        try (InputStream in = AppIcon.class.getResourceAsStream(resource)){
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e){
            System.err.println("Couldn't read " + resource + ": " + e.getMessage());
            return null;
        }
    }
}
