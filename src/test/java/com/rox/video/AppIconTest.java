package com.rox.video;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppIconTest {
    @Test
    void theIconIsPackaged(){
        final BufferedImage icon = AppIcon.readIcon("emuroxlab_icon.png");

        assertNotNull(icon);
        assertEquals(icon.getWidth(), icon.getHeight(), "square");
    }

    @Test
    void anUnreadableIconIsNoIcon(){
        assertNull(AppIcon.readIcon("broken.png"));
    }

    @Test
    void aMissingResourceIsNoIcon(){
        assertNull(AppIcon.readIcon("no-such-icon.png"));
        assertEquals(List.of(), AppIcon.images(null));
    }

    @Test
    void makesASquareImageAtEachSize(){
        final List<Image> images = AppIcon.images(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));

        assertEquals(AppIcon.SIZES, images.stream().map(image -> image.getWidth(null)).toList());
        assertEquals(AppIcon.SIZES, images.stream().map(image -> image.getHeight(null)).toList());
    }

    @Test
    void keepsTheSourcesPicture(){
        final BufferedImage source = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 256, 256, solid(0xFFCC2222), 0, 256);

        final BufferedImage small = (BufferedImage) AppIcon.images(source).get(0);

        assertEquals(0xFFCC2222, small.getRGB(8, 8));
    }

    @Test
    void givesAWindowEveryImage(){
        final Window window = mock(Window.class);
        final List<Image> images = AppIcon.images(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));

        final List<Image> dock = new ArrayList<>();

        AppIcon.applyTo(window, images, dock::add);

        verify(window).setIconImages(images);
        assertEquals(List.of(images.get(images.size() - 1)), dock, "the largest, for the dock");
    }

    @Test
    void withoutADockOnlyTheWindowGetsTheIcon(){
        final Window window = mock(Window.class);
        final List<Image> images = AppIcon.images(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));

        AppIcon.applyTo(window, images, null);

        verify(window).setIconImages(images);
    }

    @Test
    void theAppsWindowsGetThePackagedIconAtEverySize(){
        final Window window = mock(Window.class);

        AppIcon.applyTo(window);

        verify(window).setIconImages(argThat(images -> images.size() == AppIcon.SIZES.size()));
    }

    @Test
    void withNoImagesAWindowKeepsItsOwnIcon(){
        final Window window = mock(Window.class);

        AppIcon.applyTo(window, List.of(), image -> fail("no dock icon"));

        verifyNoInteractions(window);
    }

    private static int[] solid(final int argb){
        final int[] pixels = new int[256 * 256];
        Arrays.fill(pixels, argb);
        return pixels;
    }
}
