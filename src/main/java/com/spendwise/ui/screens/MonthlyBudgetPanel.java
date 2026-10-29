package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.model.Budget;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.CategoryBudgetStatus;
import com.spendwise.model.MonthlyBudgetOverview;
import com.spendwise.repository.DataAccessException;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.BudgetProgressRow;
import com.spendwise.ui.components.EmptyStatePanel;
import com.spendwise.ui.dialogs.MonthlyBudgetDialog;
import com.spendwise.util.Dialogs;
import com.spendwise.util.Icons;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import java.time.YearMonth;

/**
 * Tổng quan ngân sách tháng: điều hướng tháng, thu nhập dùng để
 * lập kế hoạch, tiết kiệm mục tiêu, tổng ngân sách vs đã chi, và trạng thái
 * từng danh mục với cảnh báo (có chữ mô tả, không chỉ dựa vào màu).
 */
public final class MonthlyBudgetPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private YearMonth viewedMonth = YearMonth.now();

    private final JLabel monthLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel incomeBasisLabel = new JLabel("—");
    private final JLabel savingLabel = new JLabel("—");
    private final JLabel totalBudgetLabel = new JLabel("—");
    private final JLabel spentLabel = new JLabel("—");
    private final JLabel remainingLabel = new JLabel("—");
    private final JLabel statusLabel = new JLabel(" ");
    private final BudgetProgressRow overallRow = new BudgetProgressRow();
    private final JPanel rowsPanel = new JPanel();
    private final JPanel emptyState = new EmptyStatePanel(Icons.Kind.BUDGETS,
            "Chưa có ngân sách cho tháng này",
            "Nhấn \"Lập ngân sách tháng\" để bắt đầu kế hoạch chi tiêu.");

    public MonthlyBudgetPanel(AppContext context) {
        super(new BorderLayout(0, 10));
        this.context = context;
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
        refresh();
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private JPanel buildHeader() {
        JPanel header = Theme.card();
        header.setLayout(new BorderLayout());
        JPanel nav = new JPanel(new GridBagLayout());
        nav.setOpaque(false);

        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        for (JButton arrow : new JButton[]{prev, next}) {
            arrow.setMargin(new Insets(2, 10, 2, 10));
            arrow.setFocusPainted(false);
        }
        monthLabel.setFont(Theme.titleFont(16f));
        monthLabel.setForeground(Theme.TEXT);

        prev.addActionListener(event -> {
            viewedMonth = viewedMonth.minusMonths(1);
            refresh();
        });
        next.addActionListener(event -> {
            viewedMonth = viewedMonth.plusMonths(1);
            refresh();
        });

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        nav.add(prev, c);
        c.gridx = 1;
        c.weightx = 1;
        nav.add(monthLabel, c);
        c.gridx = 2;
        c.weightx = 0;
        nav.add(next, c);

        JPanel planWrap = new JPanel(new BorderLayout());
        planWrap.setOpaque(false);
        header.add(nav, BorderLayout.CENTER);
        header.add(planWrap, BorderLayout.EAST);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel cards = new JPanel(new java.awt.GridLayout(1, 3, 12, 12));
        cards.setOpaque(false);
        cards.add(cardWith("Thu nhập tính ngân sách", incomeBasisLabel));
        cards.add(cardWith("Tiết kiệm mục tiêu", savingLabel));
        cards.add(cardWith("Tổng ngân sách chi", totalBudgetLabel));
        content.add(cards, c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel progressCard = Theme.card();
        progressCard.setLayout(new BorderLayout(0, 8));
        JPanel progressHead = new JPanel(new BorderLayout());
        progressHead.setOpaque(false);
        JLabel spentTitle = new JLabel("Chi tiêu thực tế trong tháng");
        spentTitle.setFont(spentTitle.getFont().deriveFont(Font.BOLD, 13.5f));
        progressHead.add(spentTitle, BorderLayout.WEST);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        progressHead.add(statusLabel, BorderLayout.EAST);
        progressCard.add(progressHead, BorderLayout.NORTH);
        JPanel numbers = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        numbers.setOpaque(false);
        numbers.add(statBlock(spentLabel));
        numbers.add(statBlock(remainingLabel));
        progressCard.add(numbers, BorderLayout.CENTER);
        progressCard.add(overallRow, BorderLayout.SOUTH);
        content.add(progressCard, c);

        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        content.add(budgetListScroll(), c);
        return content;
    }

    private JPanel cardWith(String title, JLabel valueLabel) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 4));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Theme.MUTED);
        titleLabel.setFont(titleLabel.getFont().deriveFont(12f));
        valueLabel.setFont(Theme.titleFont(16f));
        valueLabel.setForeground(Theme.TEXT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel statBlock(JLabel valueLabel) {
        JPanel block = new JPanel(new BorderLayout());
        block.setOpaque(false);
        valueLabel.setFont(Theme.titleFont(15f));
        valueLabel.setForeground(Theme.TEXT);
        block.add(valueLabel, BorderLayout.WEST);
        return block;
    }

    private JScrollPane budgetListScroll() {
        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new javax.swing.BoxLayout(rowsPanel,
                javax.swing.BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(rowsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(600, 300));
        return scroll;
    }

    @Override
    public void refresh() {
        try {
            loadMonth();
        } catch (DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void loadMonth() {
        long owner = ownerId();
        monthLabel.setText("Tháng " + viewedMonth.getMonthValue() + "/" + viewedMonth.getYear());

        MonthlyBudgetOverview overview = context.monthlyBudgetService.overview(owner, viewedMonth);
        String symbol = context.settings().currencySymbol();
        String pattern = context.settings().dateFormat();

        if (overview == null) {
            incomeBasisLabel.setText(MoneyUtil.format(
                    context.monthlyBudgetService.incomeForMonth(owner, viewedMonth), symbol));
            savingLabel.setText("—");
            totalBudgetLabel.setText("—");
            spentLabel.setText(MoneyUtil.format(0, symbol));
            remainingLabel.setText("—");
            statusLabel.setText(" ");
            rowsPanel.removeAll();
            emptyState.setPreferredSize(new Dimension(600, 200));
            GridBagConstraints filler = new GridBagConstraints();
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(emptyState, filler);
            rowsPanel.add(wrapper);
            rowsPanel.revalidate();
            rowsPanel.repaint();
            showPlanButton("Lập ngân sách tháng");
            return;
        }

        showPlanButton("Chỉnh sửa ngân sách");
        incomeBasisLabel.setText(
                MoneyUtil.format(overview.budget().averageIncome(), symbol));
        savingLabel.setText(overview.budget().savingRatePercent() + "% — "
                + MoneyUtil.format(overview.budget().savingsTarget(), symbol));
        totalBudgetLabel.setText(MoneyUtil.format(overview.budget().totalBudget(), symbol));
        spentLabel.setText(MoneyUtil.format(overview.totalSpent(), symbol));
        remainingLabel.setText(MoneyUtil.format(Math.abs(overview.remaining()), symbol)
                + (overview.remaining() < 0 ? " (vượt)" : ""));
        overallRow.update(new BudgetProgress(overallBudget(overview), overview.totalSpent()),
                symbol, pattern, false, null, null);
        switch (overview.overallStatus()) {
            case EXCEEDED -> {
                long overshoot = overview.totalSpent() - overview.budget().totalBudget();
                statusLabel.setForeground(Theme.EXPENSE);
                statusLabel.setText("Đã vượt ngân sách "
                        + MoneyUtil.format(overshoot, symbol));
            }
            case NEAR -> {
                statusLabel.setForeground(Theme.WARNING);
                statusLabel.setText("Sắp chạm hạn mức");
            }
            default -> {
                statusLabel.setForeground(Theme.INCOME);
                statusLabel.setText("Còn trong hạn mức");
            }
        }

        rowsPanel.removeAll();
        boolean anySpent = false;
        for (CategoryBudgetStatus status : overview.statuses()) {
            if (status.spent() > 0) {
                anySpent = true;
            }
            Budget shadow = new Budget(0, ownerId(), status.categoryId(), status.name(),
                    status.color(), status.allocated(),
                    viewedMonth.atDay(1), viewedMonth.atEndOfMonth(), null);
            BudgetProgressRow row = new BudgetProgressRow();
            row.setBorder(new EmptyBorder(4, 4, 10, 4));
            row.update(new BudgetProgress(shadow, status.spent()), symbol, pattern,
                    false, null, null);
            rowsPanel.add(row);
        }
        if (!anySpent) {
            JLabel note = new JLabel("Chưa có giao dịch chi nào trong tháng này (0đ).");
            note.setForeground(Theme.MUTED);
            note.setBorder(new EmptyBorder(8, 8, 8, 8));
            rowsPanel.add(note);
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private Budget overallBudget(MonthlyBudgetOverview overview) {
        return new Budget(0, ownerId(), 0, "Tổng chi tiêu", "#115E59",
                overview.budget().totalBudget(),
                viewedMonth.atDay(1), viewedMonth.atEndOfMonth(), null);
    }

    private Budget emptyRow() {
        return new Budget(0, ownerId(), 0, "", "", 1,
                viewedMonth.atDay(1), viewedMonth.atEndOfMonth(), null);
    }

    private void showPlanButton(String text) {
        JPanel headerTop = (JPanel) getComponent(0);
        java.awt.Component east = ((BorderLayout) headerTop.getLayout())
                .getLayoutComponent(BorderLayout.EAST);
        if (east instanceof JButton planButton) {
            for (java.awt.event.ActionListener listener : planButton.getActionListeners()) {
                planButton.removeActionListener(listener);
            }
            planButton.setText(text);
            planButton.addActionListener(event -> new MonthlyBudgetDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this),
                    context, viewedMonth).setVisible(true));
        } else {
            JButton planButton = Theme.primaryButton(text);
            planButton.addActionListener(event -> new MonthlyBudgetDialog(
                    javax.swing.SwingUtilities.getWindowAncestor(this),
                    context, viewedMonth).setVisible(true));
            headerTop.add(planButton, BorderLayout.EAST);
            headerTop.revalidate();
            headerTop.repaint();
        }
    }
}