package com.spendwise.ui.components;

import com.spendwise.model.CategoryTotal;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/** Pure-Swing donut chart with a two-column legend for expense distribution. */
public final class DonutChart extends JPanel {

    /** One rendered segment. */
    public record Slice(String label, long value, Color color) {
    }

    private static final int MAX_SLICES = 7;
    private static final Color OTHER_COLOR = new Color(0x94A3B8);

    private final List<Slice> slices = new ArrayList<>();
    private long total;
    private int hoverIndex = -1;
    private String centerTitle = "Tổng chi";
    private String symbol = "₫";

    public DonutChart() {
        setBackground(Theme.CARD);
        setPreferredSize(new Dimension(380, 300));
        setToolTipText(" ");
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = sliceAt(e.getX(), e.getY());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }
        });
    }

    /** Consumes category totals; more than 7 categories are merged into "Khác". */
    public void setData(List<CategoryTotal> rows, String centerTitle, String symbol) {
        slices.clear();
        this.centerTitle = centerTitle == null || centerTitle.isBlank() ? "Tổng chi" : centerTitle;
        this.symbol = symbol == null || symbol.isBlank() ? "₫" : symbol;
        this.total = 0;
        if (rows != null) {
            List<CategoryTotal> usable = rows.stream().filter(row -> row.amount() > 0).toList();
            long sum = 0;
            for (CategoryTotal row : usable) {
                sum += row.amount();
            }
            this.total = sum;
            if (usable.size() <= MAX_SLICES) {
                for (int i = 0; i < usable.size(); i++) {
                    CategoryTotal row = usable.get(i);
                    slices.add(new Slice(row.name(), row.amount(), parseColor(row.color(), i)));
                }
            } else {
                long other = 0;
                for (int i = 0; i < usable.size(); i++) {
                    CategoryTotal row = usable.get(i);
                    if (i < MAX_SLICES - 1) {
                        slices.add(new Slice(row.name(), row.amount(), parseColor(row.color(), i)));
                    } else {
                        other += row.amount();
                    }
                }
                if (other > 0) {
                    slices.add(new Slice("Khác", other, OTHER_COLOR));
                }
            }
        }
        hoverIndex = -1;
        repaint();
    }

    private static Color parseColor(String hex, int fallbackIndex) {
        try {
            return Color.decode(hex);
        } catch (Exception ex) {
            return Color.decode(Theme.DONUT_COLORS[fallbackIndex % Theme.DONUT_COLORS.length]);
        }
    }

    private Rectangle donutBounds() {
        int size = Math.min(getWidth() - 24, (int) (getHeight() * 0.60));
        size = Math.max(size, 90);
        int x = Math.max(12, (getWidth() - size) / 2);
        return new Rectangle(x, 12, size, size);
    }

    private int sliceAt(int x, int y) {
        if (slices.isEmpty()) {
            return -1;
        }
        if (slices.size() == 1) {
            return 0;
        }
        Rectangle bounds = donutBounds();
        double centerX = bounds.getCenterX();
        double centerY = bounds.getCenterY();
        double dx = x - centerX;
        double dy = y - centerY;
        double radius = Math.sqrt(dx * dx + dy * dy);
        double outer = bounds.width / 2.0;
        double inner = outer * 0.58;
        if (radius > outer || radius < inner) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        double start = 90.0; // 12 o'clock, sweeping clockwise
        double accumulated = 0;
        for (int i = 0; i < slices.size(); i++) {
            double sweep = 360.0 * slices.get(i).value() / Math.max(total, 1);
            double begin = normalize(start - accumulated - sweep);
            double finish = normalize(start - accumulated);
            if (containsAngle(angle, begin, finish)) {
                return i;
            }
            accumulated += sweep;
        }
        return -1;
    }

    private static double normalize(double angle) {
        double result = angle % 360;
        return result < 0 ? result + 360 : result;
    }

    private static boolean containsAngle(double angle, double begin, double end) {
        if (begin <= end) {
            return angle >= begin && angle <= end;
        }
        return angle >= begin || angle <= end;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = sliceAt(event.getX(), event.getY());
        if (index < 0 || total == 0) {
            return null;
        }
        Slice slice = slices.get(index);
        double percent = 100.0 * slice.value() / total;
        return slice.label() + ": " + MoneyUtil.format(slice.value(), symbol)
                + String.format(" (%.1f%%)", percent);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int width = getWidth();
        int height = getHeight();

        if (slices.isEmpty() || total <= 0) {
            g.setColor(Theme.MUTED);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
            String empty = "Chưa có dữ liệu chi tiêu";
            g.drawString(empty, (width - g.getFontMetrics().stringWidth(empty)) / 2, height / 2);
            g.dispose();
            return;
        }

        Rectangle bounds = donutBounds();
        double innerRadius = bounds.width / 2.0 * 0.58;

        double startAngle = 90.0;
        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            double sweep = 360.0 * slice.value() / total;
            Arc2D arc = new Arc2D.Double(bounds.x, bounds.y, bounds.width, bounds.height,
                    startAngle - sweep, sweep, Arc2D.PIE);
            g.setColor(slice.color());
            g.fill(arc);
            if (i == hoverIndex) {
                g.setColor(new Color(255, 255, 255, 90));
                g.fill(arc);
            }
            startAngle -= sweep;
        }

        // donut hole
        g.setColor(Theme.CARD);
        g.fill(new Ellipse2D.Double(bounds.getCenterX() - innerRadius,
                bounds.getCenterY() - innerRadius, innerRadius * 2, innerRadius * 2));

        // center labels
        g.setColor(Theme.MUTED);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11.5f));
        g.drawString(centerTitle, (int) bounds.getCenterX() - g.getFontMetrics().stringWidth(centerTitle) / 2,
                (int) bounds.getCenterY() - 4);
        g.setColor(Theme.TEXT);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
        String totalText = MoneyUtil.compact(total);
        g.drawString(totalText, (int) bounds.getCenterX() - g.getFontMetrics().stringWidth(totalText) / 2,
                (int) bounds.getCenterY() + 14);

        // legend, two columns
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11.5f));
        int legendTop = bounds.y + bounds.height + 10;
        int columnWidth = Math.max(120, (width - 24) / 2 - 6);
        int row = 0;
        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            int baseX = 12 + (i % 2) * (columnWidth + 12);
            int baseY = legendTop + 14 + row * 18;
            g.setColor(slice.color());
            g.fillRect(baseX, baseY - 9, 9, 9);
            g.setColor(Theme.TEXT);
            String text = truncate(slice.label(), 16)
                    + "  " + String.format("%.0f%%", 100.0 * slice.value() / total);
            g.drawString(text, baseX + 14, baseY);
            if (i % 2 == 1) {
                row++;
            }
        }
        g.dispose();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}