package com.spendwise.ui.components;

import com.spendwise.util.Icons;
import com.spendwise.util.Theme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Friendly empty state shown instead of an empty table/list. */
public final class EmptyStatePanel extends JPanel {

    public EmptyStatePanel(Icons.Kind iconKind, String title, String description) {
        super(new GridBagLayout());
        setBackground(Theme.CARD);

        JLabel iconLabel = new JLabel(Icons.icon(iconKind, 54, new Color(0xC9C3B4)));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.titleFont(15f));
        titleLabel.setForeground(Theme.TEXT);
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setForeground(Theme.MUTED);
        descriptionLabel.setHorizontalAlignment(JLabel.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(8, 12, 8, 12);
        add(iconLabel, c);
        add(titleLabel, c);
        add(descriptionLabel, c);
    }
}