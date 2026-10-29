package com.spendwise.ui.components;

import com.spendwise.model.Budget;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.BudgetStatus;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Dialogs;
import com.spendwise.util.Icons;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * One budget row: name + period on the left, a status-colored usage bar in the
 * middle, spent/limit figures on the right, optional edit/delete actions.
 * Reused by the dashboard, the budgets screen and the reports screen.
 */
public final class BudgetProgressRow extends JPanel {

    private final JLabel nameLabel = new JLabel();
    private final JLabel periodLabel = new JLabel();
    private final JLabel usageLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final UsageBar usageBar = new UsageBar();
    private final JButton editButton = Dialogs.iconButton("Sửa ngân sách",
            Icons.icon(Icons.Kind.EDIT, 16, Theme.MUTED));
    private final JButton deleteButton = Dialogs.iconButton("Xoá ngân sách",
            Icons.icon(Icons.Kind.DELETE, 16, Theme.MUTED));

    public BudgetProgressRow() {
        super(new GridBagLayout());
        setOpaque(false);

        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
        nameLabel.setForeground(Theme.TEXT);
        periodLabel.setForeground(Theme.MUTED);
        periodLabel.setFont(periodLabel.getFont().deriveFont(11.5f));

        usageLabel.setHorizontalAlignment(JLabel.RIGHT);
        usageLabel.setForeground(Theme.TEXT);
        statusLabel.setHorizontalAlignment(JLabel.RIGHT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 11f));

        editButton.setVisible(false);
        deleteButton.setVisible(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.weightx = 0.34;
        JPanel left = new JPanel(new BorderLayout(0, 1));
        left.setOpaque(false);
        left.add(nameLabel, BorderLayout.NORTH);
        left.add(periodLabel, BorderLayout.SOUTH);
        add(left, c);

        c.gridx = 1;
        c.weightx = 0.42;
        c.insets = new Insets(0, 12, 0, 12);
        add(usageBar, c);
        c.insets = new Insets(0, 0, 0, 0);

        c.gridx = 2;
        c.weightx = 0.24;
        JPanel right = new JPanel(new BorderLayout(0, 1));
        right.setOpaque(false);
        right.add(usageLabel, BorderLayout.NORTH);
        right.add(statusLabel, BorderLayout.SOUTH);
        add(right, c);

        c.gridx = 3;
        c.weightx = 0;
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel pair = new JPanel(new BorderLayout(2, 0));
        pair.setOpaque(false);
        pair.add(editButton, BorderLayout.WEST);
        pair.add(deleteButton, BorderLayout.EAST);
        actions.add(pair, BorderLayout.EAST);
        add(actions, c);
    }

    public void update(BudgetProgress progress, String symbol, String datePattern,
                       boolean withActions, Runnable onEdit, Runnable onDelete) {
        Budget budget = progress.budget();
        nameLabel.setText(budget.categoryName());
        periodLabel.setText(DateUtil.formatRange(budget.start(), budget.end(), datePattern));
        usageLabel.setText(MoneyUtil.format(progress.spent(), symbol) + " / "
                + MoneyUtil.format(budget.limitAmount(), symbol));
        BudgetStatus status = progress.status();
        if (status == BudgetStatus.EXCEEDED) {
            long overshoot = progress.spent() - budget.limitAmount();
            statusLabel.setText("Đã vượt ngân sách " + MoneyUtil.format(overshoot, symbol));
        } else {
            statusLabel.setText(status.label());
        }
        statusLabel.setForeground(Theme.statusColor(status));
        usageBar.setProgress(progress.usedRatio(), Theme.statusColor(status));
        editButton.setVisible(withActions);
        deleteButton.setVisible(withActions);
        for (java.awt.event.ActionListener old : editButton.getActionListeners()) {
            editButton.removeActionListener(old);
        }
        for (java.awt.event.ActionListener old : deleteButton.getActionListeners()) {
            deleteButton.removeActionListener(old);
        }
        if (onEdit != null) {
            editButton.addActionListener(event -> onEdit.run());
        }
        if (onDelete != null) {
            deleteButton.addActionListener(event -> onDelete.run());
        }
        repaint();
    }

    /** Minimal rounded usage bar colored by budget status. */
    private static final class UsageBar extends JComponent {
        private double ratio;
        private Color fill = Theme.INCOME;

        void setProgress(double ratio, Color fill) {
            this.ratio = Math.max(0d, Math.min(ratio, 1d));
            this.fill = fill;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int barHeight = 9;
            int y = (getHeight() - barHeight) / 2;
            g.setColor(new Color(0xEFEAE0));
            g.fillRoundRect(0, y, getWidth(), barHeight, barHeight, barHeight);
            int used = (int) Math.round(getWidth() * ratio);
            if (used > 0) {
                g.setColor(fill);
                g.fillRoundRect(0, y, Math.max(used, barHeight), barHeight, barHeight, barHeight);
            }
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 26);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(60, 26);
        }
    }
}