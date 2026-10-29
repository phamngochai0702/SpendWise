package com.spendwise.util;

import com.formdev.flatlaf.FlatLightLaf;
import com.spendwise.model.BudgetStatus;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/**
 * Application visual identity: deep teal primary, warm neutral backgrounds and
 * restrained accents. Also installs FlatLaf and basic Swing-wide tweaks.
 */
public final class Theme {

    // Core palette
    public static final Color PRIMARY = new Color(0x0F766E);      // deep teal
    public static final Color PRIMARY_DARK = new Color(0x0B5D57);
    public static final Color SIDEBAR = new Color(0x0B4F4A);      // navigation background
    public static final Color SIDEBAR_TEXT = new Color(0xCFE8E4);
    public static final Color PRIMARY_PALE = new Color(0xD7EEEA);

    // Surfaces
    public static final Color BG = new Color(0xF5F1E9);           // warm neutral background
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE5DFD3);

    // Text
    public static final Color TEXT = new Color(0x263238);
    public static final Color MUTED = new Color(0x6E7B72);

    // Semantic
    public static final Color INCOME = new Color(0x15803D);
    public static final Color EXPENSE = new Color(0xBE123C);
    public static final Color WARNING = new Color(0xD97706);

    /** Chart color cycle for category distributions. */
    public static final String[] DONUT_COLORS = {
            "#0F766E", "#14B8A6", "#F59E0B", "#6366F1", "#EC4899",
            "#84CC16", "#F97316", "#94A3B8", "#A855F7"
    };

    private Theme() {
    }

    /** Installs FlatLaf plus application-wide tuning. Safe to call once at startup. */
    public static void apply() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep default Metal look-and-feel
            }
        }
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("Table.selectionBackground", PRIMARY_PALE);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.height", 34);
        UIManager.put("TableHeader.background", new Color(0xF1EDE3));
        UIManager.put("OptionPane.yesButtonText", "Đồng ý");
        UIManager.put("OptionPane.noButtonText", "Huỷ");
        UIManager.put("OptionPane.okButtonText", "Đóng");
        UIManager.put("OptionPane.cancelButtonText", "Huỷ");
    }

    public static Color statusColor(BudgetStatus status) {
        return switch (status) {
            case EXCEEDED -> EXPENSE;
            case NEAR -> WARNING;
            case OK -> INCOME;
        };
    }

    /** Teal call-to-action button. */
    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 16, 8, 16));
        return button;
    }

    /** Red destructive button. */
    public static JButton dangerButton(String text) {
        JButton button = primaryButton(text);
        button.setBackground(EXPENSE);
        return button;
    }

    /** White content card with a subtle warm border. */
    public static javax.swing.JPanel card() {
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(14, 14, 14, 14)));
        panel.setOpaque(true);
        return panel;
    }

    public static Font titleFont(float size) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(Font.BOLD, size);
    }
}
