package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.model.AppSettings;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.DashboardSnapshot;
import com.spendwise.model.PeriodKey;
import com.spendwise.model.Transaction;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.BudgetProgressRow;
import com.spendwise.ui.components.DonutChart;
import com.spendwise.ui.components.GroupedBarChart;
import com.spendwise.ui.components.SummaryCard;
import com.spendwise.ui.components.TableStyles;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Icons;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * Dashboard: period totals, 6-month income/expense chart, expense donut,
 * top budget usage and the latest transactions. Everything is re-computed from
 * the database on every refresh.
 */
public final class DashboardPanel extends JPanel implements Refreshable {

    private final AppContext context;

    private final JComboBox<PeriodKey> periodCombo = new JComboBox<>(PeriodKey.values());
    private final JLabel greetingLabel = new JLabel(" ");
    private final SummaryCard incomeCard =
            new SummaryCard("Thu nhập kỳ này", Icons.Kind.MONEY, Theme.INCOME);
    private final SummaryCard expenseCard =
            new SummaryCard("Chi tiêu kỳ này", Icons.Kind.WALLET, Theme.EXPENSE);
    private final SummaryCard netCard =
            new SummaryCard("Số dư ròng", Icons.Kind.REPORTS, Theme.PRIMARY);
    private final SummaryCard budgetCard =
            new SummaryCard("Tình hình ngân sách", Icons.Kind.BUDGETS, Theme.WARNING);
    private final GroupedBarChart monthlyChart = new GroupedBarChart();
    private final DonutChart donutChart = new DonutChart();
    private final JPanel budgetList = new JPanel();
    private final javax.swing.table.DefaultTableModel recentModel =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"Ngày", "Danh mục", "Ghi chú", "Số tiền", "type"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable recentTable = new JTable(recentModel);

    public DashboardPanel(AppContext context) {
        super(new BorderLayout(0, 12));
        this.context = context;
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(buildBody(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        ((JScrollPane) getComponent(1)).setBorder(null);
        ((JScrollPane) getComponent(1)).getVerticalScrollBar().setUnitIncrement(18);

        styleRecentTable();
        periodCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                                                                   Object value, int index,
                                                                   boolean isSelected,
                                                                   boolean cellHasFocus) {
                java.awt.Component component = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (component instanceof javax.swing.JLabel label && value instanceof PeriodKey key) {
                    label.setText(key.label());
                }
                return component;
            }
        });
        periodCombo.addActionListener(event -> refresh());
        refresh();
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel title = new JLabel("Bảng điều khiển");
        title.setFont(Theme.titleFont(20f));
        title.setForeground(Theme.TEXT);
        greetingLabel.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(greetingLabel);

        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);
        JPanel periodWrap = new JPanel(new BorderLayout(6, 0));
        periodWrap.setOpaque(false);
        periodWrap.add(new JLabel("Giai đoạn:"), BorderLayout.WEST);
        periodWrap.add(periodCombo, BorderLayout.CENTER);
        JButton addTransaction = Theme.primaryButton("+ Thêm giao dịch");
        addTransaction.addActionListener(event ->
                new com.spendwise.ui.dialogs.TransactionDialog(
                        javax.swing.SwingUtilities.getWindowAncestor(this), context, null)
                        .setVisible(true));
        controls.add(addTransaction, BorderLayout.EAST);
        controls.add(periodWrap, BorderLayout.WEST);

        header.add(titles, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 12, 0);

        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 12));
        cards.setOpaque(false);
        cards.add(incomeCard);
        cards.add(expenseCard);
        cards.add(netCard);
        cards.add(budgetCard);
        body.add(cards, c);

        c.gridy = 1;
        JPanel chartRow = new JPanel(new GridBagLayout());
        chartRow.setOpaque(false);
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridy = 0;
        cc.weightx = 0.58;
        cc.fill = GridBagConstraints.HORIZONTAL;
        chartRow.add(sectionCard("Thu nhập – Chi tiêu 6 tháng gần nhất", monthlyChart), cc);
        cc.gridx = 1;
        cc.weightx = 0.42;
        cc.insets = new Insets(0, 12, 0, 0);
        chartRow.add(sectionCard("Cơ cấu chi tiêu", donutChart), cc);
        body.add(chartRow, c);

        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel bottomRow = new JPanel(new GridBagLayout());
        bottomRow.setOpaque(false);
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridy = 0;
        bc.weightx = 0.58;
        bc.fill = GridBagConstraints.BOTH;
        JPanel budgetPane = sectionCard("Ngân sách đang theo dõi", budgetScroll());
        budgetPane.setPreferredSize(new Dimension(500, 230));
        bottomRow.add(budgetPane, bc);
        bc.gridx = 1;
        bc.weightx = 0.42;
        bc.insets = new Insets(0, 12, 0, 0);
        JScrollPane recentScroll = new JScrollPane(recentTable);
        recentScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        recentScroll.setPreferredSize(new Dimension(360, 230));
        JPanel recentPane = sectionCard("Giao dịch gần đây", recentScroll);
        bottomRow.add(recentPane, bc);
        body.add(bottomRow, c);
        return body;
    }

    private javax.swing.JScrollPane budgetScroll() {
        budgetList.setOpaque(false);
        budgetList.setLayout(new javax.swing.BoxLayout(budgetList, javax.swing.BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(budgetList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(480, 170));
        return scroll;
    }

    /** White card with a small section title over the given content. */
    private static JPanel sectionCard(String title, java.awt.Component content) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13.5f));
        titleLabel.setForeground(Theme.TEXT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void styleRecentTable() {
        TableStyles.style(recentTable);
        recentTable.getColumnModel().removeColumn(recentTable.getColumn("type"));
        recentTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        recentTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        recentTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        recentTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        recentTable.getColumnModel().getColumn(3)
                .setCellRenderer(TableStyles.moneyRenderer(4, true));
    }

    @Override
    public void refresh() {
        AppSettings settings = context.settings();
        PeriodKey period = (PeriodKey) periodCombo.getSelectedItem();
        if (period == null) {
            period = settings.defaultPeriod();
        }
        DashboardSnapshot snapshot =
                context.reportService.buildDashboard(ownerId(), period, DateUtil.today());
        String symbol = settings.currencySymbol();
        String pattern = settings.dateFormat();

        String periodText = period.label() + " (" + DateUtil.formatRange(
                snapshot.from(), snapshot.to(), pattern) + ")";
        incomeCard.setValues(MoneyUtil.format(snapshot.incomeTotal(), symbol), periodText);
        expenseCard.setValues(MoneyUtil.format(snapshot.expenseTotal(), symbol), periodText);
        netCard.setValues(MoneyUtil.format(snapshot.netAllTime(), symbol),
                "Tính trên toàn bộ lịch sử thu chi");

        long exceeded = snapshot.budgetOverviews().stream()
                .filter(p -> p.status() == com.spendwise.model.BudgetStatus.EXCEEDED).count();
        long nearLimit = snapshot.budgetOverviews().stream()
                .filter(p -> p.status() == com.spendwise.model.BudgetStatus.NEAR).count();
        budgetCard.setValues(
                snapshot.budgetOverviews().isEmpty() ? "Chưa có ngân sách"
                        : snapshot.budgetOverviews().size() + " ngân sách",
                snapshot.budgetOverviews().isEmpty() ? "Tạo ngân sách để theo dõi hạn mức"
                        : exceeded + " vượt hạn mức, " + nearLimit + " sắp chạm hạn mức");

        greetingLabel.setText(settings.profileName().isBlank()
                ? "Xin chào! Tổng quan tài chính của bạn hôm nay."
                : "Xin chào, " + settings.profileName() + "! Tổng quan tài chính của bạn hôm nay.");

        monthlyChart.setData(snapshot.monthlySeries());
        donutChart.setData(snapshot.expenseDistribution(), "Tổng chi", symbol);

        rebuildBudgetRows(snapshot, symbol, pattern);

        recentModel.setRowCount(0);
        for (Transaction transaction : snapshot.recentTransactions()) {
            recentModel.addRow(new Object[]{
                    DateUtil.format(transaction.date(), pattern),
                    transaction.categoryName(),
                    transaction.note().isBlank() ? "—" : transaction.note(),
                    MoneyUtil.format(transaction.amount(), symbol),
                    transaction.type()
            });
        }
        revalidate();
        repaint();
    }

    private void rebuildBudgetRows(DashboardSnapshot snapshot, String symbol, String pattern) {
        budgetList.removeAll();
        if (snapshot.budgetOverviews().isEmpty()) {
            budgetList.add(new com.spendwise.ui.components.EmptyStatePanel(Icons.Kind.BUDGETS,
                    "Chưa có ngân sách nào",
                    "Tạo ngân sách trong mục Ngân sách để theo dõi hạn mức chi tiêu."));
        } else {
            int shown = 0;
            for (BudgetProgress progress : snapshot.budgetOverviews()) {
                if (shown >= 4) {
                    break;
                }
                BudgetProgressRow row = new BudgetProgressRow();
                row.setBorder(new EmptyBorder(4, 0, 8, 0));
                row.update(progress, symbol, pattern, false, null, null);
                budgetList.add(row);
                shown++;
            }
        }
        budgetList.revalidate();
        budgetList.repaint();
    }
}