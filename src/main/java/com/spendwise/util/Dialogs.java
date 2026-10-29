package com.spendwise.util;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

/** Small helpers for consistently styled message and confirmation dialogs. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "SpendWise", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Destructive-action confirmation with a red accent icon. */
    public static boolean confirmDelete(Component parent, String description) {
        return confirm(parent,
                "Xác nhận xoá",
                "Bạn có chắc muốn xoá " + description + "?\nHành động này không thể hoàn tác.",
                "Xoá",
                Theme.EXPENSE);
    }

    public static boolean confirm(Component parent, String title, String message,
                                  String confirmLabel, Color accent) {
        Object[] options = {confirmLabel, "Huỷ"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                message,
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                warningIcon(accent),
                options,
                options[1]);
        return choice == 0;
    }

    /** Round "!" icon so confirmations match the app's identity. */
    private static Icon warningIcon(Color color) {
        return new Icon() {
            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, 32, 32);
                g2.setColor(Color.WHITE);
                g2.fillRect(x + 14, y + 6, 4, 13);
                g2.fillOval(x + 14, y + 22, 4, 4);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 32;
            }

            @Override
            public int getIconHeight() {
                return 32;
            }
        };
    }

    /** Left-aligned text block with vertical component list spacing. */
    public static JComponent titled(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.titleFont(17f));
        titleLabel.setForeground(Theme.TEXT);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(Theme.MUTED);
        panel.add(titleLabel, BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isBlank()) {
            panel.add(subtitleLabel, BorderLayout.SOUTH);
        }
        return panel;
    }

    /** Flat tool-bar button without borders; used for row action icons. */
    public static javax.swing.JButton iconButton(String tooltip, Icon icon) {
        javax.swing.JButton button = new javax.swing.JButton(icon);
        button.setToolTipText(tooltip);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }
}
