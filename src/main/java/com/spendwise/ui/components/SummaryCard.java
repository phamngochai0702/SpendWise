package com.spendwise.ui.components;

import com.spendwise.util.Icons;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.RenderingHints;

/** Financial summary card: colored icon chip, label, big value, muted subtitle. */
public final class SummaryCard extends JPanel {

    private final JLabel valueLabel = new JLabel("—");
    private final JLabel subtitleLabel = new JLabel(" ");
    private final Color accent;

    public SummaryCard(String title, Icons.Kind iconKind, Color accent) {
        super(new BorderLayout(14, 0));
        this.accent = accent;
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(16, 18, 16, 18)));

        JPanel iconChip = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 36));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        iconChip.setOpaque(false);
        iconChip.setPreferredSize(new Dimension(46, 46));
        GridBagConstraints chipConstraints = new GridBagConstraints();
        chipConstraints.anchor = GridBagConstraints.CENTER;
        iconChip.add(new JLabel(Icons.icon(iconKind, 22, accent)), chipConstraints);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Theme.MUTED);
        Font base = UIManager.getFont("defaultFont");
        titleLabel.setFont(base == null ? titleLabel.getFont() : base.deriveFont(Font.PLAIN, 12.5f));

        valueLabel.setForeground(Theme.TEXT);
        valueLabel.setFont(Theme.titleFont(19f));

        subtitleLabel.setForeground(Theme.MUTED);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11.5f));

        JPanel textColumn = new JPanel();
        textColumn.setOpaque(false);
        textColumn.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        textColumn.add(titleLabel, c);
        c.gridy = 1;
        c.insets = new Insets(2, 0, 2, 0);
        textColumn.add(valueLabel, c);
        c.gridy = 2;
        c.insets = new Insets(0, 0, 0, 0);
        textColumn.add(subtitleLabel, c);

        add(iconChip, BorderLayout.WEST);
        add(textColumn, BorderLayout.CENTER);
    }

    public void setValues(String value, String subtitle) {
        valueLabel.setText(value == null || value.isBlank() ? "—" : value);
        subtitleLabel.setText(subtitle == null ? " " : subtitle);
    }
}