package com.spendwise.ui.components;

import com.spendwise.model.TransactionType;
import com.spendwise.util.Theme;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.ListSelectionModel;

/** Shared JTable styling and cell renderers so every screen looks the same. */
public final class TableStyles {

    private TableStyles() {
    }

    public static void style(JTable table) {
        table.setRowHeight(32);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
    }

    /**
     * Right-aligned bold money cell. The color (income green / expense red)
     * comes from a hidden model column that holds a {@link TransactionType}.
     */
    public static DefaultTableCellRenderer moneyRenderer(int modelTypeColumn, boolean bold) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                setText(value == null ? "" : value.toString());
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
                if (!isSelected) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object type = table.getModel().getValueAt(modelRow, modelTypeColumn);
                    setForeground(type == TransactionType.INCOME ? Theme.INCOME : Theme.EXPENSE);
                }
                return component;
            }
        };
    }

    /** Small colored "Thu nhập"/"Chi tiêu" badge text. */
    public static DefaultTableCellRenderer typeBadge() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if (value instanceof TransactionType type) {
                    component.setForeground(isSelected ? component.getForeground()
                            : type == TransactionType.INCOME ? Theme.INCOME : Theme.EXPENSE);
                    setText(type.displayName());
                } else {
                    setText("");
                }
                return component;
            }
        };
    }

    /** Rounded color chip from a "#RRGGBB" value. */
    public static DefaultTableCellRenderer colorSwatch() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                return this;
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                Color color = Theme.PRIMARY;
                try {
                    color = Color.decode(getText());
                } catch (NumberFormatException ignored) {
                    // keep teal fallback
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = 22;
                int h = 14;
                int x = (getWidth() - w) / 2;
                int y = (getHeight() - h) / 2;
                g2.setColor(color);
                g2.fillRoundRect(x, y, w, h, 6, 6);
                g2.setColor(new Color(0, 0, 0, 36));
                g2.drawRoundRect(x, y, w, h, 6, 6);
                g2.dispose();
            }
        };
    }
}