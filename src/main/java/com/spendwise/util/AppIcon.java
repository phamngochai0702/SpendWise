package com.spendwise.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws the SpendWise application icon (a teal wallet mark) programmatically,
 * so no binary image resources are needed. Used for the window icon.
 */
public final class AppIcon {

    private AppIcon() {
    }

    /** Window icon set in multiple resolutions for crisp taskbar rendering. */
    public static List<java.awt.Image> images() {
        return List.of(render(16), render(24), render(32), render(48), render(64));
    }

    private static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double scale = size / 64.0;
        g2.scale(scale, scale);

        // rounded teal tile
        g2.setColor(Theme.SIDEBAR);
        g2.fill(new RoundRectangle2D.Double(1, 1, 62, 62, 15, 15));

        // wallet body
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Double(12, 20, 40, 30, 9, 9));

        // wallet flap + latch
        g2.setColor(new Color(0, 0, 0, 70));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(12, 30, 52, 30));
        g2.setColor(Theme.SIDEBAR);
        g2.fill(new Ellipse2D.Double(37, 37, 8.5, 8.5));

        // small coin accent
        g2.setColor(Theme.WARNING);
        g2.fill(new Ellipse2D.Double(40.5, 9, 14, 14));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.2f));
        g2.draw(new Ellipse2D.Double(44.5, 13, 6, 6));

        g2.dispose();
        return image;
    }
}
