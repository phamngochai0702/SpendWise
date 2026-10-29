package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.model.BudgetProgress;
import com.spendwise.repository.DataAccessException;
import com.spendwise.service.ValidationException;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.BudgetProgressRow;
import com.spendwise.ui.dialogs.BudgetDialog;
import com.spendwise.util.Dialogs;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Ngân sách: tab 1 là ngân sách tháng (kế hoạch + đối chiếu chi tiêu thực tế
 * + cảnh báo), tab 2 là ngân sách theo giai đoạn tự do.
 */
public final class BudgetsPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final MonthlyBudgetPanel monthlyPanel;
    private final JPanel legacyRowsPanel = new JPanel();
    private final JLabel legacyLimitLabel = new JLabel("—");
    private final JLabel legacySpentLabel = new JLabel("—");
    private final JLabel legacyStatusLabel = new JLabel("—");

    public BudgetsPanel(AppContext context) {
        super(new BorderLayout(0, 12));
        this.context = context;
        this.monthlyPanel = new MonthlyBudgetPanel(context);
        setOpaque(false);

        JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Ngân sách tháng", monthlyScroll());
        tabs.addTab("Theo giai đoạn", new JScrollPane(buildLegacyBody()));
        add(buildHeader(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        refresh();
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel(new java.awt.GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel title = new JLabel("Ngân sách");
        title.setFont(Theme.titleFont(20f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Lập ngân sách theo tháng dựa trên thu nhập thực tế của bạn.");
        subtitle.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);
        return header;
    }

    private JScrollPane monthlyScroll() {
        JScrollPane scroll = new JScrollPane(monthlyPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ------------------------------------------------------------------
    // Tab "Theo giai đoạn" (legacy range budgets)
    // ------------------------------------------------------------------

    private JPanel buildLegacyBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 12, 0);
        body.add(buildSummaryStrip(), c);

        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        legacyRowsPanel.setOpaque(false);
        legacyRowsPanel.setLayout(new javax.swing.BoxLayout(legacyRowsPanel,
                javax.swing.BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(legacyRowsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        body.add(scroll, c);
        return body;
    }

    private JPanel buildSummaryStrip() {
        javax.swing.JPanel strip = Theme.card();
        strip.setLayout(new java.awt.GridLayout(1, 3, 12, 0));
        strip.add(statBlock("Tổng hạn mức", legacyLimitLabel));
        strip.add(statBlock("Đã chi tiêu", legacySpentLabel));
        strip.add(statBlock("Trạng thái", legacyStatusLabel));
        return strip;
    }

    private JPanel statBlock(String titleText, JLabel valueLabel) {
        JPanel block = new JPanel(new BorderLayout(0, 3));
        block.setOpaque(false);
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setForeground(Theme.MUTED);
        titleLabel.setFont(titleLabel.getFont().deriveFont(12f));
        valueLabel.setFont(Theme.titleFont(16f));
        valueLabel.setForeground(Theme.TEXT);
        block.add(titleLabel, java.awt.BorderLayout.NORTH);
        block.add(valueLabel, java.awt.BorderLayout.CENTER);
        return block;
    }

    @Override
    public void refresh() {
        monthlyPanel.refresh();
        refreshLegacy();
    }

    private void refreshLegacy() {
        java.util.List<BudgetProgress> rows;
        try {
            rows = context.budgetService.listWithProgress(ownerId());
        } catch (DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            rows = java.util.List.of();
        }

        String symbol = context.settings().currencySymbol();
        String pattern = context.settings().dateFormat();
        long totalLimit = 0;
        long totalSpent = 0;
        long exceeded = 0;
        long nearLimit = 0;
        for (BudgetProgress row : rows) {
            totalLimit += row.budget().limitAmount();
            totalSpent += Math.min(row.spent(), row.budget().limitAmount());
            switch (row.status()) {
                case EXCEEDED -> exceeded++;
                case NEAR -> nearLimit++;
                default -> { }
            }
        }
        legacyLimitLabel.setText(MoneyUtil.format(totalLimit, symbol));
        legacySpentLabel.setText(MoneyUtil.format(totalSpent, symbol));
        legacyStatusLabel.setText(rows.isEmpty() ? "Chưa có ngân sách"
                : exceeded + " vượt, " + nearLimit + " sắp chạm hạn mức");

        legacyRowsPanel.removeAll();
        if (rows.isEmpty()) {
            com.spendwise.ui.components.EmptyStatePanel empty =
                    new com.spendwise.ui.components.EmptyStatePanel(
                            com.spendwise.util.Icons.Kind.BUDGETS,
                            "Chưa có ngân sách nào",
                            "Tạo ngân sách để theo dõi hạn mức chi của từng danh mục.");
            empty.setPreferredSize(new Dimension(600, 220));
            GridBagConstraints filler = new GridBagConstraints();
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(empty, filler);
            legacyRowsPanel.add(wrapper);
        } else {
            for (BudgetProgress row : rows) {
                BudgetProgressRow line = new BudgetProgressRow();
                line.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        new javax.swing.border.MatteBorder(0, 0, 1, 0, Theme.BORDER),
                        new EmptyBorder(10, 12, 10, 6)));
                line.update(row, symbol, pattern, true,
                        () -> new BudgetDialog(
                                javax.swing.SwingUtilities.getWindowAncestor(this),
                                context, row.budget()).setVisible(true),
                        () -> deleteLegacyBudget(row));
                legacyRowsPanel.add(line);
            }
        }
        legacyRowsPanel.revalidate();
        legacyRowsPanel.repaint();
    }

    private void deleteLegacyBudget(BudgetProgress progress) {
        boolean confirmed = Dialogs.confirmDelete(this,
                "ngân sách của danh mục '" + progress.budget().categoryName()
                        + "' (" + com.spendwise.util.DateUtil.formatRange(
                        progress.budget().start(), progress.budget().end(),
                        context.settings().dateFormat()) + ")");
        if (!confirmed) {
            return;
        }
        try {
            context.budgetService.delete(ownerId(), progress.budget().id());
        } catch (ValidationException | DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            return;
        }
        context.bus.fire(com.spendwise.app.DataChangeBus.Topic.BUDGETS);
    }
}