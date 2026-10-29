package com.spendwise.util;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Consistent, dependency-free vector icon set drawn with Java2D at any size.
 * All glyphs are designed on a 24x24 unit canvas and scaled when painted.
 */
public final class Icons {

    public enum Kind {
        DASHBOARD, TRANSACTIONS, CATEGORIES, BUDGETS, REPORTS, SETTINGS,
        PLUS, EDIT, DELETE, EXPORT, SEARCH, CALENDAR, WALLET, MONEY, USER
    }

    private Icons() {
    }

    public static Icon icon(Kind kind, int size, Color color) {
        return new VectorIcon(kind, size, color);
    }

    /** Icon for unselected sidebar navigation entries. */
    public static Icon nav(Kind kind) {
        return icon(kind, 20, Theme.SIDEBAR_TEXT);
    }

    /** Icon for the currently selected sidebar navigation entry. */
    public static Icon navActive(Kind kind) {
        return icon(kind, 20, Color.WHITE);
    }

    private static final class VectorIcon implements Icon {

        private final Kind kind;
        private final int size;
        private final Color color;

        VectorIcon(Kind kind, int size, Color color) {
            this.kind = kind;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            double scale = size / 24.0;
            g2.scale(scale, scale);
            g2.setColor(color);
            paint(g2);
            g2.dispose();
        }

        private void paint(Graphics2D g2) {
            switch (kind) {
                case DASHBOARD -> paintGrid(g2);
                case TRANSACTIONS -> paintArrows(g2);
                case CATEGORIES -> paintTag(g2);
                case BUDGETS -> paintTarget(g2);
                case REPORTS -> paintBars(g2);
                case SETTINGS -> paintGear(g2);
                case PLUS -> paintPlus(g2);
                case EDIT -> paintPencil(g2);
                case DELETE -> paintTrash(g2);
                case EXPORT -> paintExport(g2);
                case SEARCH -> paintSearch(g2);
                case CALENDAR -> paintCalendar(g2);
                case WALLET -> paintWallet(g2);
                case MONEY -> paintMoney(g2);
                case USER -> paintUser(g2);
            }
        }

        private void fillRoundRect(Graphics2D g2, double x, double y, double w, double h, double arc) {
            g2.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        }

        private void drawRoundRect(Graphics2D g2, double x, double y, double w, double h,
                                   double arc, double stroke) {
            g2.setStroke(new java.awt.BasicStroke((float) stroke, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        }

        private void line(Graphics2D g2, double x1, double y1, double x2, double y2, double stroke) {
            g2.setStroke(new java.awt.BasicStroke((float) stroke, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
        }

        private void paintGrid(Graphics2D g2) {
            fillRoundRect(g2, 3, 3, 8, 8, 2);
            fillRoundRect(g2, 13, 3, 8, 8, 2);
            fillRoundRect(g2, 3, 13, 8, 8, 2);
            fillRoundRect(g2, 13, 13, 8, 8, 2);
        }

        private void paintArrows(Graphics2D g2) {
            line(g2, 4, 8, 17, 8, 2);
            Path2D head1 = new Path2D.Double();
            head1.moveTo(17, 4.5);
            head1.lineTo(21, 8);
            head1.lineTo(17, 11.5);
            head1.closePath();
            g2.fill(head1);

            line(g2, 20, 16, 7, 16, 2);
            Path2D head2 = new Path2D.Double();
            head2.moveTo(7, 12.5);
            head2.lineTo(3, 16);
            head2.lineTo(7, 19.5);
            head2.closePath();
            g2.fill(head2);
        }

        private void paintTag(Graphics2D g2) {
            Path2D tag = new Path2D.Double();
            tag.moveTo(3.5, 3.5);
            tag.lineTo(10, 3.5);
            tag.lineTo(20.5, 14);
            tag.lineTo(14, 20.5);
            tag.closePath();
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(tag);
            g2.fill(new Ellipse2D.Double(5.8, 5.8, 3.4, 3.4));
        }

        private void paintTarget(Graphics2D g2) {
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(4, 4, 16, 16));
            g2.setStroke(new java.awt.BasicStroke(1.6f));
            g2.draw(new Ellipse2D.Double(8.2, 8.2, 7.6, 7.6));
            g2.fill(new Ellipse2D.Double(10.6, 10.6, 2.8, 2.8));
        }

        private void paintBars(Graphics2D g2) {
            fillRoundRect(g2, 4.5, 12.5, 4, 7.5, 1.2);
            fillRoundRect(g2, 10, 8, 4, 12, 1.2);
            fillRoundRect(g2, 15.5, 3.5, 4, 16.5, 1.2);
        }

        private void paintGear(Graphics2D g2) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                g2.translate(12, 12);
                g2.rotate(radians);
                fillRoundRect(g2, -1.7, -11.5, 3.4, 4.8, 1.6);
                g2.rotate(-radians);
                g2.translate(-12, -12);
            }
            g2.setStroke(new java.awt.BasicStroke(1.9f));
            g2.draw(new Ellipse2D.Double(5.7, 5.7, 12.6, 12.6));
            g2.fill(new Ellipse2D.Double(10.4, 10.4, 3.2, 3.2));
        }

        private void paintPlus(Graphics2D g2) {
            line(g2, 12, 5, 12, 19, 2.6);
            line(g2, 5, 12, 19, 12, 2.6);
        }

        private void paintPencil(Graphics2D g2) {
            Path2D pencil = new Path2D.Double();
            pencil.moveTo(4, 20);
            pencil.lineTo(5.1, 15.7);
            pencil.lineTo(16.4, 4.4);
            pencil.quadTo(17.2, 3.6, 18.0, 4.4);
            pencil.quadTo(18.8, 5.2, 18.0, 6.0);
            pencil.lineTo(6.7, 17.3);
            pencil.closePath();
            g2.fill(pencil);
        }

        private void paintTrash(Graphics2D g2) {
            line(g2, 4.5, 7, 19.5, 7, 2);
            fillRoundRect(g2, 9.6, 3.6, 4.8, 2.4, 1.2);
            Path2D body = new Path2D.Double();
            body.moveTo(6.4, 9);
            body.lineTo(7, 19.3);
            body.quadTo(7.1, 20.6, 8.4, 20.6);
            body.lineTo(15.6, 20.6);
            body.quadTo(16.9, 20.6, 17, 19.3);
            body.lineTo(17.6, 9);
            body.closePath();
            g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(body);
            line(g2, 10.3, 12, 10.5, 17, 1.5);
            line(g2, 13.7, 12, 13.5, 17, 1.5);
        }

        private void paintExport(Graphics2D g2) {
            line(g2, 12, 3.5, 12, 13.5, 2.2);
            Path2D head = new Path2D.Double();
            head.moveTo(7.8, 10);
            head.lineTo(12, 14.6);
            head.lineTo(16.2, 10);
            head.closePath();
            g2.fill(head);

            Path2D tray = new Path2D.Double();
            tray.moveTo(4, 15.5);
            tray.lineTo(4, 19);
            tray.quadTo(4, 20.2, 5.2, 20.2);
            tray.lineTo(18.8, 20.2);
            tray.quadTo(20, 20.2, 20, 19);
            tray.lineTo(20, 15.5);
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.draw(tray);
        }

        private void paintSearch(Graphics2D g2) {
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(4.5, 4.5, 12.6, 12.6));
            line(g2, 15.5, 15.5, 21, 21, 2.2);
        }

        private void paintCalendar(Graphics2D g2) {
            drawRoundRect(g2, 3.5, 5, 17, 15.5, 2.5, 1.8);
            line(g2, 3.5, 9.8, 20.5, 9.8, 1.8);
            fillRoundRect(g2, 7.3, 3.2, 2.2, 3.6, 1);
            fillRoundRect(g2, 14.5, 3.2, 2.2, 3.6, 1);
            float[] dotsX = {8.6f, 12f, 15.4f};
            for (float dotX : dotsX) {
                g2.fill(new Ellipse2D.Double(dotX - 0.9, 12.2, 1.8, 1.8));
            }
            g2.fill(new Ellipse2D.Double(7.7, 15.7, 1.8, 1.8));
            g2.fill(new Ellipse2D.Double(11.1, 15.7, 1.8, 1.8));
        }

        private void paintWallet(Graphics2D g2) {
            g2.fill(new RoundRectangle2D.Double(3, 6, 18, 13, 3.2, 3.2));
            Color dimmed = new Color(0, 0, 0, 100);
            g2.setColor(dimmed);
            g2.setStroke(new java.awt.BasicStroke(1.5f));
            g2.draw(new java.awt.geom.Line2D.Double(3, 9.6, 21, 9.6));
            g2.fill(new Ellipse2D.Double(15.2, 12, 3.6, 3.6));
            g2.setColor(color);
        }

        private void paintMoney(Graphics2D g2) {
            drawRoundRect(g2, 3, 6.5, 18, 11, 2.2, 1.9);
            g2.setStroke(new java.awt.BasicStroke(1.8f));
            g2.draw(new Ellipse2D.Double(9.4, 9.4, 5.2, 5.2));
            g2.fill(new Ellipse2D.Double(5.4, 10.8, 2.2, 2.2));
            g2.fill(new Ellipse2D.Double(16.4, 10.8, 2.2, 2.2));
        }

        private void paintUser(Graphics2D g2) {
            g2.fill(new Ellipse2D.Double(8.4, 3.6, 7.2, 7.2));
            java.awt.geom.Arc2D shoulders = new java.awt.geom.Arc2D.Double(
                    4.0, 12.2, 16.0, 15.0, 0, 180, java.awt.geom.Arc2D.CHORD);
            g2.fill(shoulders);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
