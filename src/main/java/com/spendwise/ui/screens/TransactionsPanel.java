package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Category;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionFilter;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.DataAccessException;
import com.spendwise.service.ValidationException;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.DateField;
import com.spendwise.ui.components.EmptyStatePanel;
import com.spendwise.ui.components.TableStyles;
import com.spendwise.ui.components.WrapLayout;
import com.spendwise.ui.dialogs.TransactionDialog;
import com.spendwise.util.CsvExporter;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Dialogs;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Transaction history: search, filters, sorting, CRUD and CSV export. */
public final class TransactionsPanel extends JPanel implements Refreshable {

    private final AppContext context;

    private final JTextField searchField = new JTextField(16);
    private final JComboBox<String> typeCombo =
            new JComboBox<>(new String[]{"Tất cả", "Thu nhập", "Chi tiêu"});
    private final JComboBox<Object> categoryCombo = new JComboBox<>();
    private final DateField fromDate;
    private final DateField toDate;
    private final JTextField minAmountField = new JTextField(7);
    private final JTextField maxAmountField = new JTextField(7);
    private final JComboBox<TransactionFilter.Sort> sortCombo =
            new JComboBox<>(TransactionFilter.Sort.values());
    private final JLabel filterError = new JLabel(" ");

    private final javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"Ngày", "type", "Danh mục", "Ghi chú", "Số tiền"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(model);
    private final JLabel summaryLabel = new JLabel(" ");
    private final CardPanel cardPanel = new CardPanel();
    private final EmptyStatePanel emptyPanel = new EmptyStatePanel(
            com.spendwise.util.Icons.Kind.TRANSACTIONS,
            "Chưa có giao dịch nào phù hợp",
            "Thêm giao dịch đầu tiên hoặc nới lỏng bộ lọc để xem lịch sử.");
    private List<Transaction> currentRows = List.of();

    public TransactionsPanel(AppContext context) {
        super(new BorderLayout(0, 10));
        this.context = context;
        setOpaque(false);

        LocalDate today = DateUtil.today();
        fromDate = new DateField(context.settings().dateFormat(), today.withDayOfMonth(1));
        toDate = new DateField(context.settings().dateFormat(), today);

        add(buildToolbar(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);
        add(buildSummary(), BorderLayout.SOUTH);

        configureTable();
        rebuildCategories();
        bindEvents();
        refresh();
    }

    private JPanel buildToolbar() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout());
        // WrapLayout: tự xếp thành nhiều dòng và tính đúng chiều cao kiểu ưu tiên,
        // nên các nút không bao giờ bị cắt góc khi cửa sổ hẹp.
        JPanel flow = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 6));
        flow.setOpaque(false);
        flow.add(labeled("Tìm:", searchField));
        flow.add(labeled("Loại:", typeCombo));
        flow.add(labeled("Danh mục:", categoryCombo));
        flow.add(labeled("Từ:", fromDate));
        flow.add(labeled("đến:", toDate));

        JPanel amountRange = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        amountRange.setOpaque(false);
        amountRange.add(minAmountField);
        amountRange.add(new JLabel("–"));
        amountRange.add(maxAmountField);
        flow.add(labeled("Tiền:", amountRange));
        flow.add(labeled("Sắp xếp:", sortCombo));

        JButton refreshButton = new JButton("Làm mới");
        refreshButton.addActionListener(event -> refresh());
        flow.add(refreshButton);
        JButton exportButton = new JButton("Xuất CSV");
        exportButton.addActionListener(event -> exportCsv());
        flow.add(exportButton);
        JButton editButton = new JButton("Sửa");
        editButton.addActionListener(event -> editSelected());
        flow.add(editButton);
        JButton deleteButton = new JButton("Xoá");
        deleteButton.addActionListener(event -> deleteSelected());
        flow.add(deleteButton);
        JButton addButton = Theme.primaryButton("+ Thêm");
        addButton.addActionListener(event -> {
            Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
            new TransactionDialog(owner, context, null).setVisible(true);
        });
        flow.add(addButton);
        card.add(flow, BorderLayout.NORTH);

        filterError.setForeground(Theme.EXPENSE);
        filterError.setFont(filterError.getFont().deriveFont(11f));
        JPanel errorRow = new JPanel(new BorderLayout());
        errorRow.setOpaque(false);
        errorRow.add(filterError, BorderLayout.WEST);
        card.add(errorRow, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        panel.add(new JLabel(label));
        panel.add(field);
        return panel;
    }

    private JPanel buildSummary() {
        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        summary.setBorder(new EmptyBorder(2, 4, 0, 4));
        summaryLabel.setForeground(Theme.MUTED);
        summary.add(summaryLabel, BorderLayout.WEST);
        return summary;
    }

    /** CardLayout switching between empty state and the table. */
    private static final class CardPanel extends JPanel {
        CardPanel() {
            super(new java.awt.CardLayout());
            setOpaque(true);
            setBackground(Theme.CARD);
        }

        void showEmpty() {
            ((java.awt.CardLayout) getLayout()).show(this, "empty");
        }

        void showTable() {
            ((java.awt.CardLayout) getLayout()).show(this, "table");
        }
    }
    private void configureTable() {
        TableStyles.style(table);
        table.getColumnModel().removeColumn(table.getColumn("type"));
        table.getColumnModel().getColumn(0).setPreferredWidth(96);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(340);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setCellRenderer(TableStyles.moneyRenderer(1, true));
        cardPanel.add(new JScrollPane(table), "table");
        cardPanel.add(emptyPanel, "empty");
        cardPanel.showTable();

        JPopupMenu popup = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Sửa giao dịch");
        editItem.addActionListener(event -> editSelected());
        JMenuItem deleteItem = new JMenuItem("Xoá giao dịch");
        deleteItem.addActionListener(event -> deleteSelected());
        popup.add(editItem);
        popup.add(deleteItem);
        table.setComponentPopupMenu(popup);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });
    }

    private void bindEvents() {
        sortCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                                                                   Object value, int index,
                                                                   boolean isSelected,
                                                                   boolean cellHasFocus) {
                java.awt.Component component = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (component instanceof javax.swing.JLabel label
                        && value instanceof TransactionFilter.Sort sort) {
                    label.setText(sort.label());
                }
                return component;
            }
        });
        searchField.addActionListener(event -> refresh());
        typeCombo.addActionListener(event -> {
            rebuildCategories();
            refresh();
        });
        categoryCombo.addActionListener(event -> refresh());
        sortCombo.addActionListener(event -> refresh());
        fromDate.addDateChangeListener(this::refresh);
        toDate.addDateChangeListener(this::refresh);
        minAmountField.addActionListener(event -> refresh());
        maxAmountField.addActionListener(event -> refresh());
    }

    private void rebuildCategories() {
        Object selected = categoryCombo.getSelectedItem();
        Long selectedId = selected instanceof Category category ? category.id() : null;
        categoryCombo.removeAllItems();
        categoryCombo.addItem("Tất cả danh mục");
        TransactionType type = selectedType();
        List<Category> categories = type == null
                ? context.categories.findAll(ownerId())
                : context.categories.findByType(ownerId(), type);
        for (Category category : categories) {
            categoryCombo.addItem(category);
        }
        if (selectedId != null) {
            for (int i = 1; i < categoryCombo.getItemCount(); i++) {
                Object item = categoryCombo.getItemAt(i);
                if (item instanceof Category category && category.id() == selectedId) {
                    categoryCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private TransactionType selectedType() {
        int index = typeCombo.getSelectedIndex();
        return index <= 0 ? null : TransactionType.values()[index - 1];
    }

    @Override
    public void refresh() {
        filterError.setText(" ");
        TransactionFilter filter = new TransactionFilter();
        filter.keyword(searchField.getText());
        filter.type(selectedType());
        Object selectedCategory = categoryCombo.getSelectedItem();
        if (selectedCategory instanceof Category category) {
            filter.categoryId(category.id());
        }
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        if (from.isAfter(to)) {
            filterError.setText("Khoảng ngày không hợp lệ: 'Từ ngày' phải trước 'đến ngày'.");
        }
        filter.between(from, to);

        Long minAmount = null;
        Long maxAmount = null;
        try {
            if (!minAmountField.getText().isBlank()) {
                minAmount = MoneyUtil.parse(minAmountField.getText());
            }
            if (!maxAmountField.getText().isBlank()) {
                maxAmount = MoneyUtil.parse(maxAmountField.getText());
            }
            if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
                filterError.setText("Khoảng tiền không hợp lệ: giới hạn dưới phải nhỏ hơn hoặc bằng giới hạn trên.");
                minAmount = null;
                maxAmount = null;
            }
        } catch (ValidationException ex) {
            filterError.setText(ex.getMessage());
        }
        filter.amountRange(minAmount, maxAmount);
        filter.sort((TransactionFilter.Sort) sortCombo.getSelectedItem());

        try {
            currentRows = context.transactionService.search(ownerId(), filter);
        } catch (DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            currentRows = List.of();
        }

        model.setRowCount(0);
        String symbol = context.settings().currencySymbol();
        String pattern = context.settings().dateFormat();
        long income = 0;
        long expense = 0;
        for (Transaction transaction : currentRows) {
            if (transaction.type() == TransactionType.INCOME) {
                income += transaction.amount();
            } else {
                expense += transaction.amount();
            }
            model.addRow(new Object[]{
                    DateUtil.format(transaction.date(), pattern),
                    transaction.type(),
                    transaction.categoryName(),
                    transaction.note().isBlank() ? "—" : transaction.note(),
                    MoneyUtil.format(transaction.amount(), symbol)
            });
        }

        summaryLabel.setText(currentRows.size() + " giao dịch  •  Tổng thu: "
                + MoneyUtil.format(income, symbol)
                + "  •  Tổng chi: " + MoneyUtil.format(expense, symbol)
                + "  •  Chênh lệch: " + MoneyUtil.formatSigned(income - expense, symbol));

        if (currentRows.isEmpty()) {
            cardPanel.showEmpty();
        } else {
            cardPanel.showTable();
        }
    }

    private Transaction selectedTransaction() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 || viewRow >= currentRows.size()) {
            Dialogs.info(this, "Hãy chọn một giao dịch trong bảng trước.");
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return currentRows.get(modelRow);
    }

    private void editSelected() {
        Transaction selected = selectedTransaction();
        if (selected == null) {
            return;
        }
        new TransactionDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                context, selected).setVisible(true);
    }

    private void deleteSelected() {
        Transaction selected = selectedTransaction();
        if (selected == null) {
            return;
        }
        String description = "giao dịch \"" + (selected.note().isBlank()
                ? selected.categoryName() : selected.note())
                + "\" (" + MoneyUtil.format(selected.amount(),
                context.settings().currencySymbol()) + ")";
        if (!Dialogs.confirmDelete(this, description)) {
            return;
        }
        try {
            context.transactionService.delete(ownerId(), selected.id());
        } catch (ValidationException | DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            return;
        }
        context.bus.fire(DataChangeBus.Topic.TRANSACTIONS);
    }

    /** Writes the current filtered list to a CSV file on a background thread. */
    private void exportCsv() {
        String delimiter = context.settings().csvDelimiter();
        List<Transaction> rows = List.copyOf(currentRows);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("giao-dich-"
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
                List<String[]> lines = new ArrayList<>();
                lines.add(new String[]{"ID", "Loại", "Danh mục", "Số tiền",
                        "Ngày", "Ghi chú", "Ngày tạo"});
                for (Transaction transaction : rows) {
                    lines.add(new String[]{
                            String.valueOf(transaction.id()),
                            transaction.type().displayName(),
                            transaction.categoryName(),
                            String.valueOf(transaction.amount()),
                            transaction.date().toString(),
                            transaction.note(),
                            transaction.createdAt() == null
                                    ? "" : DateUtil.toIsoDateTime(transaction.createdAt())
                    });
                }
                return CsvExporter.export(destination, delimiter, lines);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    Dialogs.info(TransactionsPanel.this,
                            "Đã xuất " + rows.size() + " giao dịch ra tệp:\n" + get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    Dialogs.error(TransactionsPanel.this,
                            "Không thể xuất CSV: " + cause.getMessage());
                }
            }
        }.execute();
    }
}