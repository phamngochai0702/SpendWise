package com.spendwise.ui.components;

import com.spendwise.model.TimePoint;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/** Hand-painted "income vs expense" grouped bar chart - pure Swing, zero libraries. */
public final class GroupedBarChart extends JPanel {

    private static final Color GRID = new Color(0xEBE6DA);
    private static final Color INCOME_BAR = Theme.INCOME;
    private static final Color EXPENSE_BAR = Theme.EXPENSE;

    private List<TimePoint> points = List.of();
    private int hoverIndex = -1;

    public GroupedBarChart() {
        setBackground(Theme.CARD);
        setPreferredSize(new Dimension(420, 260));
        setToolTipText(" "); // enables the tooltip subsystem
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = pointAt(e.getX());
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

    public void setData(List<TimePoint> data) {
        this.points = data == null ? List.of() : data;
        this.hoverIndex = -1;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = pointAt(event.getX());
        if (index < 0 || index >= points.size()) {
            return null;
        }
        TimePoint point = points.get(index);
        return point.label()
                + "  -  Thu: " + MoneyUtil.compact(point.income())
                + "  -  Chi: " + MoneyUtil.compact(point.expense());
    }

    private int plotLeft() {
        return 52;
    }

    private int plotTop() {
        return 34;
    }

    private int plotBottom() {
        return getHeight() - 26;
    }

    private int plotRight() {
        return getWidth() - 12;
    }

    private int pointAt(int x) {
        int width = plotRight() - plotLeft();
        if (width <= 0 || points.isEmpty()) {
            return -1;
        }
        int groupWidth = width / points.size();
        if (groupWidth <= 0) {
            return -1;
        }
        int index = (x - plotLeft()) / groupWidth;
        if (index < 0 || index >= points.size()) {
            return -1;
        }
        int groupStart = plotLeft() + index * groupWidth;
        return (x >= groupStart && x <= groupStart + groupWidth) ? index : -1;
    }

    private long maxOfPoints() {
        long max = 0;
        for (TimePoint point : points) {
            max = Math.max(max, Math.max(point.income(), point.expense()));
        }
        return max;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int left = plotLeft();
        int top = plotTop();
        int right = plotRight();
        int bottom = plotBottom();

        // legend
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11.5f));
        g.setColor(INCOME_BAR);
        g.fillRoundRect(left, 11, 10, 10, 3, 3);
        g.setColor(Theme.MUTED);
        String incomeText = "Thu nhập";
        g.drawString(incomeText, left + 15, 20);
        int expenseX = left + 15 + g.getFontMetrics().stringWidth(incomeText) + 16;
        g.setColor(EXPENSE_BAR);
        g.fillRoundRect(expenseX, 11, 10, 10, 3, 3);
        g.setColor(Theme.MUTED);
        g.drawString("Chi tiêu", expenseX + 15, 20);

        if (points.isEmpty() || maxOfPoints() <= 0 || bottom - top < 30 || right - left < 30) {
            g.setColor(Theme.MUTED);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
            String empty = "Chưa có dữ liệu trong giai đoạn này";
            int emptyX = left + Math.max(0, (right - left - g.getFontMetrics().stringWidth(empty)) / 2);
            g.drawString(empty, emptyX, (top + bottom) / 2);
            g.dispose();
            return;
        }

        long max = maxOfPoints();

        // grid lines + value axis
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10.5f));
        for (int i = 0; i <= 4; i++) {
            int y = top + (int) Math.round((bottom - top) * i / 4.0);
            g.setColor(i == 4 ? Theme.BORDER : GRID);
            g.drawLine(left, y, right, y);
            long value = Math.round(max * (4 - i) / 4.0);
            String label = MoneyUtil.compact(value);
            g.setColor(Theme.MUTED);
            g.drawString(label, left - 8 - g.getFontMetrics().stringWidth(label), y + 4);
        }

        // bars
        int groupWidth = Math.max(8, (right - left) / points.size());
        int barWidth = Math.max(5, Math.min(26, (int) (groupWidth * 0.26)));
        Font axisFont = g.getFont().deriveFont(Font.PLAIN, 10.5f);
        for (int i = 0; i < points.size(); i++) {
            TimePoint point = points.get(i);
            int centerX = left + i * groupWidth + groupWidth / 2;

            if (point.income() > 0) {
                int height = Math.max((int) Math.round((double) point.income() / max * (bottom - top)), 3);
                g.setColor(INCOME_BAR);
                g.fill(new RoundRectangle2D.Double(centerX - barWidth - 2, bottom - height,
                        barWidth, height, 5, 5));
                if (i == hoverIndex) {
                    g.setColor(Theme.MUTED);
                    String text = MoneyUtil.compact(point.income());
                    g.drawString(text, centerX - barWidth - 2
                            + Math.max(0, (barWidth - g.getFontMetrics().stringWidth(text)) / 2),
                            bottom - height - 4);
                }
            }
            if (point.expense() > 0) {
                int height = Math.max((int) Math.round((double) point.expense() / max * (bottom - top)), 3);
                g.setColor(EXPENSE_BAR);
                g.fill(new RoundRectangle2D.Double(centerX + 2, bottom - height,
                        barWidth, height, 5, 5));
                if (i == hoverIndex) {
                    g.setColor(Theme.MUTED);
                    String text = MoneyUtil.compact(point.expense());
                    g.drawString(text, centerX + 2
                            + Math.max(0, (barWidth - g.getFontMetrics().stringWidth(text)) / 2),
                            bottom - height - 4);
                }
            }

            g.setColor(Theme.MUTED);
            g.setFont(axisFont);
            String label = point.label();
            g.drawString(label, centerX - g.getFontMetrics().stringWidth(label) / 2, bottom + 16);
        }
        g.dispose();
    }
}