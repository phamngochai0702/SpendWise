package com.spendwise.ui.dialogs;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Category;
import com.spendwise.model.CategoryBudgetStatus;
import com.spendwise.model.MonthlyBudgetItem;
import com.spendwise.model.MonthlyBudgetOverview;
import com.spendwise.model.RecentIncomeSummary;
import com.spendwise.model.TransactionType;
import com.spendwise.service.MonthlyBudgetService;
import com.spendwise.service.ValidationException;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lập / chỉnh sửa ngân sách tháng:
 * ô 1 – thu nhập đã ghi nhận trong tháng (có thể tham khảo thu nhập trung bình);
 * ô 2 – thanh trượt tỷ lệ tiết kiệm với tính toán tức thì;
 * ô 3 – phân bổ theo từng danh mục chi với số tiền còn lại thời gian thực.
 */
public final class MonthlyBudgetDialog extends JDialog {

    private static final int DEFAULT_SAVING_RATE = 20;

    private final AppContext context;
    private final YearMonth month;
    private final RecentIncomeSummary incomeSummary;
    private final long monthIncome;

    private JTextField incomeField;
    private JLabel incomeInfo;
    private JSlider rateSlider;
    private JTextField rateField;
    private JLabel savingsValue;
    private JLabel totalBudgetValue;
    private JLabel allocatedValue;
    private JLabel remainingValue;
    private final JLabel warningLabel = errorLabel();
    private final JLabel formError = errorLabel();
    private final List<CategoryField> categoryRows = new ArrayList<>();

    public MonthlyBudgetDialog(Window owner, AppContext context, YearMonth month) {
        super(owner, "Ngân sách tháng " + month.getMonthValue() + "/" + month.getYear(),
                ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.month = month;
        this.incomeSummary = context.monthlyBudgetService.recentIncome(ownerId(), month);
        this.monthIncome = context.monthlyBudgetService.incomeForMonth(ownerId(), month);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.add(buildPlanPanel(), BorderLayout.CENTER);
        root.add(buildActions(), BorderLayout.SOUTH);

        setContentPane(root);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(580, 720);
        setLocationRelativeTo(owner);
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private JComponent buildPlanPanel() {
        MonthlyBudgetOverview existing =
                context.monthlyBudgetService.overview(ownerId(), month);
        Map<Long, Long> existingAllocations = new HashMap<>();
        int existingRate = DEFAULT_SAVING_RATE;
        if (existing != null) {
            existingRate = existing.budget().savingRatePercent();
            for (MonthlyBudgetItem item : context.monthlyBudgets.listItems(
                    existing.budget().id())) {
                existingAllocations.put(item.categoryId(), item.amount());
            }
        }
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // ---- 1. income basis ---------------------------------------------------
        c.gridy = 0;
        body.add(sectionTitle("1. Thu nhập tính ngân sách"), c);

        incomeInfo = new JLabel(" ");
        incomeInfo.setForeground(Theme.MUTED);
        incomeInfo.setFont(incomeInfo.getFont().deriveFont(11.5f));
        c.gridy = 1;
        c.insets = new Insets(6, 0, 2, 0);
        body.add(incomeInfo, c);

        JButton useAverageButton = new JButton("Dùng thu nhập trung bình làm gợi ý");
        useAverageButton.setEnabled(incomeSummary.monthsWithData() > 0);
        useAverageButton.setToolTipText("Chép mức trung bình 6 tháng trước vào ô thu nhập.");
        useAverageButton.addActionListener(event -> {
            incomeField.setText(MoneyUtil.group(incomeSummary.averageIncome()));
            recompute();
        });
        c.gridy = 2;
        c.insets = new Insets(0, 0, 2, 0);
        body.add(useAverageButton, c);

        incomeField = new JTextField(12);
        incomeField.setText(MoneyUtil.group(monthIncome));
        incomeField.setToolTipText("Mặc định là tổng thu nhập đã ghi nhận trong tháng; có thể chỉnh sửa.");
        incomeField.addActionListener(event -> recompute());
        incomeField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                recompute();
            }
        });
        c.gridy = 3;
        body.add(incomeField, c);

        // ---- 2. saving rate ----------------------------------------------------
        c.gridy = 4;
        c.insets = new Insets(14, 0, 2, 0);
        body.add(sectionTitle("2. Tỷ lệ tiết kiệm"), c);

        JPanel rateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rateRow.setOpaque(false);
        rateSlider = new JSlider(0, 100, existingRate);
        rateField = new JTextField(String.valueOf(existingRate), 4);
        rateSlider.setOpaque(false);
        rateSlider.addChangeListener(event -> syncFromSlider());
        rateField.addActionListener(event -> syncFromField());
        rateRow.add(rateSlider);
        rateRow.add(rateField);
        rateRow.add(new JLabel("%"));
        c.gridy = 5;
        c.insets = new Insets(4, 0, 2, 0);
        body.add(rateRow, c);

        JPanel computed = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        computed.setOpaque(false);
        savingsValue = new JLabel("—");
        totalBudgetValue = new JLabel("—");
        computed.add(miniLabel("Tiết kiệm mục tiêu:", savingsValue));
        computed.add(miniLabel("Ngân sách chi tiêu:", totalBudgetValue));
        c.gridy = 6;
        body.add(computed, c);

        // ---- 3. allocations ----------------------------------------------------
        c.gridy = 7;
        c.insets = new Insets(14, 0, 2, 0);
        body.add(sectionTitle("3. Phân bổ theo danh mục chi"), c);

        JPanel allocationList = new JPanel();
        allocationList.setOpaque(false);
        allocationList.setLayout(new BoxLayout(allocationList, BoxLayout.Y_AXIS));
        for (Category category : context.categories.findByType(
                ownerId(), TransactionType.EXPENSE)) {
            CategoryField row = new CategoryField(category, existingAllocations.getOrDefault(
                    category.id(), 0L), this::recompute);
            categoryRows.add(row);
            allocationList.add(row.panel());
        }
        if (categoryRows.isEmpty()) {
            allocationList.add(new javax.swing.JLabel(
                    "Chưa có danh mục chi tiêu — hãy tạo ở màn hình Danh mục."));
        }
        JScrollPane scroll = new JScrollPane(allocationList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(520, 230));
        c.gridy = 8;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        body.add(scroll, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;

        JPanel totals = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        totals.setOpaque(false);
        allocatedValue = new JLabel("—");
        remainingValue = new JLabel("—");
        totals.add(miniLabel("Đã phân bổ:", allocatedValue));
        totals.add(miniLabel("Giới hạn còn lại:", remainingValue));
        c.gridy = 9;
        c.insets = new Insets(8, 0, 0, 0);
        body.add(totals, c);

        c.gridy = 10;
        body.add(warningLabel, c);

        c.gridy = 11;
        body.add(formError, c);
        recompute();
        return wrappedInScroll(body);
    }

    private long incomeBasis() {
        try {
            return MoneyUtil.parse(incomeField.getText());
        } catch (ValidationException invalid) {
            warningLabel.setText("Thu nhập tính ngân sách không hợp lệ.");
            return -1;
        }
    }

    private int currentRate() {
        try {
            int rate = Integer.parseInt(rateField.getText().trim());
            return Math.max(MonthlyBudgetService.MIN_SAVING_RATE,
                    Math.min(MonthlyBudgetService.MAX_SAVING_RATE, rate));
        } catch (NumberFormatException invalid) {
            return rateSlider.getValue();
        }
    }

    private void syncFromSlider() {
        rateField.setText(String.valueOf(rateSlider.getValue()));
        recompute();
    }

    private void syncFromField() {
        try {
            int rate = Integer.parseInt(rateField.getText().trim());
            rateSlider.setValue(Math.max(MonthlyBudgetService.MIN_SAVING_RATE,
                    Math.min(MonthlyBudgetService.MAX_SAVING_RATE, rate)));
        } catch (NumberFormatException invalid) {
            rateField.setText(String.valueOf(rateSlider.getValue()));
        }
        recompute();
    }

    /** Cập nhật tức thì mọi con số khi tỷ lệ hoặc phân bổ thay đổi. */
    private void recompute() {
        String symbol = context.settings().currencySymbol();
        long incomeBasis = incomeBasis();
        if (incomeBasis < 0) {
            return;
        }
        int rate = currentRate();
        long savings = MonthlyBudgetService.savingsTarget(incomeBasis, rate);
        long totalBudget = MonthlyBudgetService.spendingBudget(incomeBasis, rate);
        savingsValue.setText(MoneyUtil.format(savings, symbol));
        totalBudgetValue.setText(MoneyUtil.format(totalBudget, symbol));

        long allocatedSum = 0;
        for (CategoryField row : categoryRows) {
            allocatedSum += row.safeAmount();
        }
        long remaining = MonthlyBudgetService.remainingOf(totalBudget, allocatedSum);
        allocatedValue.setText(MoneyUtil.format(allocatedSum, symbol));
        remainingValue.setText(MoneyUtil.format(Math.abs(remaining), symbol)
                + (remaining < 0 ? " (vượt)" : ""));
        remainingValue.setForeground(remaining < 0 ? Theme.EXPENSE : Theme.TEXT);

        String averageHint = incomeSummary.monthsWithData() == 0
                ? "Chưa có dữ liệu trung bình 6 tháng trước."
                : "Trung bình 6 tháng trước: "
                + MoneyUtil.format(incomeSummary.averageIncome(), symbol) + " (gợi ý).";
        incomeInfo.setText("Đã ghi nhận trong tháng: " + MoneyUtil.format(monthIncome, symbol)
                + ". " + averageHint);
    }

    private void save() {
        warningLabel.setText(" ");
        formError.setText(" ");
        long incomeBasis = incomeBasis();
        if (incomeBasis < 0) {
            return;
        }
        Map<Long, Long> allocations = new HashMap<>();
        for (CategoryField row : categoryRows) {
            try {
                allocations.put(row.category.id(), row.amount());
            } catch (ValidationException invalid) {
                formError.setText("Danh mục '" + row.category.name()
                        + "': " + invalid.getMessage());
                return;
            }
        }
        try {
            context.monthlyBudgetService.save(ownerId(), month, incomeBasis,
                    currentRate(), allocations);
        } catch (ValidationException ex) {
            formError.setText(ex.getMessage());
            return;
        }
        context.bus.fire(DataChangeBus.Topic.BUDGETS);
        dispose();
    }

    private JPanel buildActions() {
        JButton saveButton = Theme.primaryButton("Lưu ngân sách tháng");
        saveButton.addActionListener(event -> save());
        JButton cancelButton = new JButton("Huỷ");
        cancelButton.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(saveButton);
        getRootPane().setDefaultButton(saveButton);
        return actions;
    }

    private JComponent wrappedInScroll(JPanel body) {
        JScrollPane scroll = new JScrollPane(body,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13.5f));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private JComponent miniLabel(String title, JLabel value) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.setOpaque(false);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
        value.setForeground(Theme.TEXT);
        panel.add(new JLabel(title));
        panel.add(value);
        return panel;
    }

    private static JLabel errorLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(Theme.EXPENSE);
        label.setFont(label.getFont().deriveFont(11.5f));
        return label;
    }

    private static Color resolvedColor(String hex) {
        try {
            return Color.decode(hex == null ? "" : hex);
        } catch (NumberFormatException invalid) {
            return Theme.PRIMARY;
        }
    }

    /** Dòng nhập tiền cho một danh mục; không hợp lệ chỉ bắn lỗi khi bấm Lưu. */
    private static final class CategoryField {

        private final Category category;
        private final JTextField field = new JTextField(9);

        CategoryField(Category category, long initial, Runnable onChange) {
            this.category = category;
            if (initial > 0) {
                field.setText(MoneyUtil.group(initial));
            }
            field.addActionListener(event -> onChange.run());
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent event) {
                    onChange.run();
                }
            });
        }

        JPanel panel() {
            JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            rowPanel.setOpaque(false);
            final Color color = resolvedColor(category.color());
            javax.swing.JComponent dot = new javax.swing.JComponent() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, 14, 14, 5, 5);
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(14, 14));
            rowPanel.add(dot);

            JLabel nameLabel = new JLabel(category.name());
            nameLabel.setPreferredSize(new Dimension(150, 22));
            rowPanel.add(nameLabel);
            rowPanel.add(field);
            rowPanel.add(new JLabel("đ"));
            return rowPanel;
        }

        /** Parses safely for the live preview (invalid = 0); strict parse on save. */
        long safeAmount() {
            try {
                return MoneyUtil.parse(field.getText());
            } catch (ValidationException invalid) {
                return 0;
            }
        }

        long amount() {
            return MoneyUtil.parse(field.getText());
        }
    }
}