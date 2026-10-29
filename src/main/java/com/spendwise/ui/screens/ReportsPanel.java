package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.PeriodKey;
import com.spendwise.model.RangeSummary;
import com.spendwise.model.TimePoint;
import com.spendwise.repository.DataAccessException;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.BudgetProgressRow;
import com.spendwise.ui.components.DateField;
import com.spendwise.ui.components.DonutChart;
import com.spendwise.ui.components.GroupedBarChart;
import com.spendwise.ui.components.TableStyles;
import com.spendwise.util.CsvExporter;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Dialogs;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Reports built from real records: overview with an adaptive time-series chart,
 * expense distribution, monthly breakdown, budget performance and CSV exports.
 */
public final class ReportsPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final JComboBox<PeriodKey> periodCombo = new JComboBox<>(PeriodKey.values());
    private final DateField fromDate;
    private final DateField toDate;
    private final JLabel rangeLabel = new JLabel(" ");

    private final GroupedBarChart seriesChart = new GroupedBarChart();
    private final List<TimePoint> currentSeries = new ArrayList<>();

    private final DonutChart distributionChart = new DonutChart();
    private final javax.swing.table.DefaultTableModel distributionModel =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"Danh mục", "Số giao dịch", "Tổng chi", "Tỷ lệ"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private List<CategoryTotal> currentDistribution = List.of();

    private final javax.swing.table.DefaultTableModel monthlyModel =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"Tháng", "Thu nhập", "Chi tiêu", "Chênh lệch", "Tỷ lệ tiết kiệm"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private List<TimePoint> currentMonthly = List.of();

    private final JPanel budgetRowsPanel = new JPanel();
    private final javax.swing.table.DefaultTableModel summaryModel =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"Chỉ số", "Giá trị"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    public ReportsPanel(AppContext context) {
        super(new BorderLayout(0, 12));
        this.context = context;
        setOpaque(false);

        LocalDate today = DateUtil.today();
        fromDate = new DateField(context.settings().dateFormat(), today.withDayOfMonth(1));
        toDate = new DateField(context.settings().dateFormat(), today);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);

        bindEvents();
        refresh();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel(new java.awt.GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel title = new JLabel("Báo cáo tài chính");
        title.setFont(Theme.titleFont(20f));
        title.setForeground(Theme.TEXT);
        rangeLabel.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(rangeLabel);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Mẫu:"));
        controls.add(periodCombo);
        controls.add(new JLabel("Từ"));
        controls.add(fromDate);
        controls.add(new JLabel("đến"));
        controls.add(toDate);
        JButton applyButton = Theme.primaryButton("Áp dụng");
        applyButton.addActionListener(event -> refresh());
        controls.add(applyButton);
        JButton exportButton = new JButton("Xuất CSV giao dịch");
        exportButton.addActionListener(event ->
                exportCsv("giao-dich", transactionCsvRows()));
        controls.add(exportButton);

        header.add(titles, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tổng quan", new JScrollPane(overviewTab()));
        tabs.addTab("Phân bổ chi tiêu", new JScrollPane(distributionTab()));
        tabs.addTab("Theo tháng", new JScrollPane(monthlyTab()));
        tabs.addTab("Ngân sách", new JScrollPane(budgetsTab()));
        return tabs;
    }

    private Component overviewTab() {
        JTable summaryTable = new JTable(summaryModel);
        TableStyles.style(summaryTable);
        summaryTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        JScrollPane summaryScroll = new JScrollPane(summaryTable);
        summaryScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        summaryScroll.setPreferredSize(new Dimension(640, 190));

        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridy = 0;
        c.weighty = 0.6;
        c.insets = new Insets(0, 0, 12, 0);
        pane.add(cardWrap("Thu nhập – Chi tiêu theo thời gian", seriesChart), c);
        c.gridy = 1;
        c.weighty = 0.4;
        pane.add(cardWrap("Tóm tắt giai đoạn", summaryScroll), c);
        return pane;
    }

    private static JPanel cardWrap(String title, Component content) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 10));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13.5f));
        titleLabel.setForeground(Theme.TEXT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Component distributionTab() {
        JTable table = new JTable(distributionModel);
        TableStyles.style(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        tableScroll.setPreferredSize(new Dimension(640, 220));

        JButton exportButton = new JButton("Xuất CSV danh mục");
        exportButton.addActionListener(
                event -> exportCsv("phan-bo-chi-tieu", distributionCsvRows()));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.setOpaque(false);
        buttonRow.add(exportButton);

        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridy = 0;
        c.weighty = 0.55;
        c.insets = new Insets(0, 0, 12, 0);
        pane.add(cardWrap("Cơ cấu chi tiêu theo danh mục", distributionChart), c);
        c.gridy = 1;
        c.weighty = 0.45;
        pane.add(cardWrap("Chi tiết theo danh mục", tableScroll), c);
        c.gridy = 2;
        c.weighty = 0;
        pane.add(buttonRow, c);
        return pane;
    }

    private Component monthlyTab() {
        JTable table = new JTable(monthlyModel);
        TableStyles.style(table);
        for (int column = 1; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(130);
        }
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JButton exportButton = new JButton("Xuất CSV theo tháng");
        exportButton.addActionListener(
                event -> exportCsv("tong-hop-thang", monthlyCsvRows()));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.setOpaque(false);
        buttonRow.add(exportButton);

        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 12, 0);
        pane.add(cardWrap("Tổng hợp thu – chi theo tháng", tableScroll), c);
        c.weighty = 0;
        c.insets = new Insets(0, 0, 0, 0);
        pane.add(buttonRow, c);
        return pane;
    }

    private Component budgetsTab() {
        budgetRowsPanel.setOpaque(false);
        budgetRowsPanel.setLayout(new javax.swing.BoxLayout(budgetRowsPanel,
                javax.swing.BoxLayout.Y_AXIS));

        JButton exportButton = new JButton("Xuất CSV ngân sách");
        exportButton.addActionListener(
                event -> exportCsv("ngan-sach", budgetCsvRows()));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.setOpaque(false);
        buttonRow.add(exportButton);

        JPanel pane = new JPanel(new GridBagLayout());
        pane.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 12, 0);
        pane.add(cardWrap("Hiệu quả ngân sách theo giai đoạn", budgetRowsPanel), c);
        c.weighty = 0;
        c.insets = new Insets(0, 0, 0, 0);
        pane.add(buttonRow, c);
        return pane;
    }

    private void bindEvents() {
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
        periodCombo.addActionListener(event -> applyPeriodPreset());
    }

    private void applyPeriodPreset() {
        PeriodKey period = (PeriodKey) periodCombo.getSelectedItem();
        LocalDate[] range = period.range(DateUtil.today());
        fromDate.setValue(range[0]);
        toDate.setValue(range[1]);
        refresh();
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    @Override
    public void refresh() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        if (from.isAfter(to)) {
            Dialogs.error(this, "Khoảng thời gian không hợp lệ: 'Từ ngày' phải trước 'đến ngày'.");
            return;
        }
        try {
            loadAll(from, to);
        } catch (DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void loadAll(LocalDate from, LocalDate to) {
        String symbol = context.settings().currencySymbol();
        String pattern = context.settings().dateFormat();
        rangeLabel.setText("Giai đoạn: " + DateUtil.formatRange(from, to, pattern));

        currentSeries.clear();
        currentSeries.addAll(context.reportService.seriesWithGranularity(ownerId(), from, to));
        seriesChart.setData(currentSeries);

        RangeSummary summary = context.reportService.rangeSummary(ownerId(), from, to);
        summaryModel.setRowCount(0);
        summaryModel.addRow(new Object[]{"Số giao dịch",
                String.valueOf(summary.transactionCount())});
        summaryModel.addRow(new Object[]{"Tổng thu nhập",
                MoneyUtil.format(summary.incomeTotal(), symbol)});
        summaryModel.addRow(new Object[]{"Tổng chi tiêu",
                MoneyUtil.format(summary.expenseTotal(), symbol)});
        summaryModel.addRow(new Object[]{"Chênh lệch (thu - chi)",
                MoneyUtil.formatSigned(summary.net(), symbol)});
        summaryModel.addRow(new Object[]{"Chi tiêu bình quân mỗi giao dịch",
                MoneyUtil.format(summary.averageExpense(), symbol)});
        summaryModel.addRow(new Object[]{"Giao dịch chi lớn nhất",
                MoneyUtil.format(summary.largestExpense(), symbol)});
        summaryModel.addRow(new Object[]{"Giao dịch thu lớn nhất",
                MoneyUtil.format(summary.largestIncome(), symbol)});
        summaryModel.addRow(new Object[]{"Tỷ lệ tiết kiệm", savingRate(summary)});

        currentDistribution = context.reportService.expenseDistribution(ownerId(), from, to);
        distributionChart.setData(currentDistribution, "Tổng chi", symbol);
        long distributionTotal = 0;
        for (CategoryTotal row : currentDistribution) {
            distributionTotal += row.amount();
        }
        distributionModel.setRowCount(0);
        for (CategoryTotal row : currentDistribution) {
            double percent = distributionTotal == 0 ? 0 : 100.0 * row.amount() / distributionTotal;
            distributionModel.addRow(new Object[]{row.name(), row.transactionCount(),
                    MoneyUtil.format(row.amount(), symbol), String.format("%.1f%%", percent)});
        }

        currentMonthly = context.reportService.monthlySeries(ownerId(), from, to);
        monthlyModel.setRowCount(0);
        for (TimePoint point : currentMonthly) {
            monthlyModel.addRow(new Object[]{
                    DateUtil.monthLabel(point.key()),
                    MoneyUtil.format(point.income(), symbol),
                    MoneyUtil.format(point.expense(), symbol),
                    MoneyUtil.formatSigned(point.income() - point.expense(), symbol),
                    savingRate(point.income(), point.expense())
            });
        }

        rebuildBudgetRows(from, to, symbol, pattern);
    }

    private String savingRate(RangeSummary summary) {
        return savingRate(summary.incomeTotal(), summary.expenseTotal());
    }

    private String savingRate(long income, long expense) {
        if (income <= 0) {
            return "—";
        }
        return String.format("%.1f%%", 100.0 * (income - expense) / income);
    }

    private void rebuildBudgetRows(LocalDate from, LocalDate to, String symbol, String pattern) {
        budgetRowsPanel.removeAll();
        java.util.List<BudgetProgress> rows = context.budgetService.activeDuring(ownerId(), from, to);
        if (rows.isEmpty()) {
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setOpaque(false);
            wrapper.add(new com.spendwise.ui.components.EmptyStatePanel(
                    com.spendwise.util.Icons.Kind.BUDGETS,
                    "Không có ngân sách nào trong giai đoạn này",
                    "Tạo ngân sách trong mục Ngân sách để theo dõi hạn mức."));
            budgetRowsPanel.add(wrapper);
        } else {
            for (BudgetProgress row : rows) {
                BudgetProgressRow line = new BudgetProgressRow();
                line.setBorder(new EmptyBorder(6, 4, 12, 4));
                line.update(row, symbol, pattern, false, null, null);
                budgetRowsPanel.add(line);
            }
        }
        budgetRowsPanel.revalidate();
        budgetRowsPanel.repaint();
    }

    private List<String[]> transactionCsvRows() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"ID", "Loại", "Danh mục", "Số tiền", "Ngày", "Ghi chú", "Ngày tạo"});
        if (from == null || to == null) {
            return lines;
        }
        for (com.spendwise.model.Transaction transaction :
                context.transactionService.search(ownerId(),
                        new com.spendwise.model.TransactionFilter().between(from, to))) {
            lines.add(new String[]{
                    String.valueOf(transaction.id()),
                    transaction.type().displayName(),
                    transaction.categoryName(),
                    String.valueOf(transaction.amount()),
                    transaction.date().toString(),
                    transaction.note(),
                    transaction.createdAt() == null ? "" : DateUtil.toIsoDateTime(transaction.createdAt())
            });
        }
        return lines;
    }

    private List<String[]> distributionCsvRows() {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Danh mục", "Số giao dịch", "Tổng chi", "Tỷ lệ (%)"});
        long total = 0;
        for (CategoryTotal row : currentDistribution) {
            total += row.amount();
        }
        for (CategoryTotal row : currentDistribution) {
            double percent = total == 0 ? 0 : 100.0 * row.amount() / total;
            lines.add(new String[]{row.name(), String.valueOf(row.transactionCount()),
                    String.valueOf(row.amount()), String.format(Locale.ROOT, "%.1f", percent)});
        }
        return lines;
    }

    private List<String[]> monthlyCsvRows() {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Tháng", "Thu nhập", "Chi tiêu", "Chênh lệch", "Tỷ lệ tiết kiệm (%)"});
        for (TimePoint point : currentMonthly) {
            String saving = point.income() <= 0 ? ""
                    : String.format(Locale.ROOT, "%.1f",
                    100.0 * (point.income() - point.expense()) / point.income());
            lines.add(new String[]{point.key(), String.valueOf(point.income()),
                    String.valueOf(point.expense()),
                    String.valueOf(point.income() - point.expense()), saving});
        }
        return lines;
    }

    private List<String[]> budgetCsvRows() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{"Danh mục", "Từ ngày", "Đến ngày", "Hạn mức",
                "Đã chi", "Còn lại", "Sử dụng (%)", "Trạng thái"});
        if (from == null || to == null) {
            return lines;
        }
        for (com.spendwise.model.BudgetProgress progress :
                context.budgetService.activeDuring(ownerId(), from, to)) {
            com.spendwise.model.Budget budget = progress.budget();
            lines.add(new String[]{
                    budget.categoryName(),
                    budget.start().toString(),
                    budget.end().toString(),
                    String.valueOf(budget.limitAmount()),
                    String.valueOf(progress.spent()),
                    String.valueOf(progress.remaining()),
                    String.format(Locale.ROOT, "%.1f", progress.usedRatio() * 100.0),
                    progress.status().label()
            });
        }
        return lines;
    }

    /** Writes the given rows to a user-chosen CSV file on a background thread. */
    private void exportCsv(String filePrefix, List<String[]> lines) {
        String delimiter = context.settings().csvDelimiter();
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(filePrefix + "-"
                + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".csv"));
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            target = target.resolveSibling(target.getFileName() + ".csv");
        }
        final Path destination = target;
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws IOException {
                return CsvExporter.export(destination, delimiter, lines);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    Dialogs.info(ReportsPanel.this,
                            "Đã xuất báo cáo ra tệp:\n" + get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    Dialogs.error(ReportsPanel.this,
                            "Không thể xuất CSV: " + cause.getMessage());
                }
            }
        }.execute();
    }
}