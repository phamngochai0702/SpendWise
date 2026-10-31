package com.spendwise.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;

public final class AppIcon {

    private static final BufferedImage ICON = load();

    private AppIcon() {
    }

    /** Window icon set in multiple resolutions for crisp taskbar rendering. */
    public static List<java.awt.Image> images() {
        return List.of(16, 24, 32, 48, 64, 128, 256).stream()
                .map(AppIcon::render)
                .map(image -> (Image) image)
                .toList();
    }

    private static BufferedImage load() {
        try (InputStream input = AppIcon.class.getResourceAsStream("/com/spendwise/app-icon.png")) {
            if (input == null) {
                throw new IllegalStateException("Missing application icon resource.");
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Invalid application icon resource.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load application icon.", e);
        }
    }

    private static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(ICON, 0, 0, size, size, null);
        g2.dispose();
        return image;
    }
}
