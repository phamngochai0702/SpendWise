package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Category;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.DataAccessException;
import com.spendwise.service.ValidationException;
import com.spendwise.ui.Refreshable;
import com.spendwise.ui.components.TableStyles;
import com.spendwise.ui.dialogs.CategoryDialog;
import com.spendwise.util.DateUtil;
import com.spendwise.util.Dialogs;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Category management: create/edit/delete with full data protection, plus
 * per-category totals and a drill-down list of its transactions.
 */
public final class CategoriesPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final JComboBox<String> typeCombo =
            new JComboBox<>(new String[]{"Tất cả", "Thu nhập", "Chi tiêu"});

    private final javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(
                    new Object[]{"", "Tên danh mục", "type", "Loại",
                            "Số giao dịch", "Tổng tiền", "Ngân sách"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JTable table = new JTable(model);
    private final JLabel summaryLabel = new JLabel(" ");
    private List<CategoryTotal> currentRows = List.of();
    private final Map<Long, Long> budgetCounts = new HashMap<>();

    public CategoriesPanel(AppContext context) {
        super(new BorderLayout(0, 10));
        this.context = context;
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        configureTable();
        typeCombo.addActionListener(event -> refresh());
        refresh();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel(new java.awt.GridLayout(2, 1));
        titles.setOpaque(false);
        JLabel title = new JLabel("Danh mục");
        title.setFont(Theme.titleFont(20f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Quản lý nhóm thu nhập và chi tiêu; dữ liệu cũ luôn được bảo vệ khi xoá.");
        subtitle.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(subtitle);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Loại:"));
        controls.add(typeCombo);
        JButton addButton = Theme.primaryButton("+ Thêm danh mục");
        addButton.addActionListener(event ->
                new CategoryDialog(javax.swing.SwingUtilities.getWindowAncestor(this), context, null)
                        .setVisible(true));
        controls.add(addButton);

        header.add(titles, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JScrollPane buildTable() {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(javax.swing.BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        summaryLabel.setForeground(Theme.MUTED);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton viewButton = new JButton("Xem giao dịch");
        viewButton.addActionListener(event -> viewSelected());
        buttons.add(viewButton);
        JButton editButton = new JButton("Sửa");
        editButton.addActionListener(event -> editSelected());
        buttons.add(editButton);
        JButton deleteButton = new JButton("Xoá");
        deleteButton.addActionListener(event -> deleteSelected());
        buttons.add(deleteButton);
        actions.add(summaryLabel, BorderLayout.WEST);
        actions.add(buttons, BorderLayout.EAST);
        return actions;
    }

    private void configureTable() {
        TableStyles.style(table);
        table.getColumnModel().removeColumn(table.getColumn("type"));
        table.getColumnModel().getColumn(0).setMaxWidth(64);
        table.getColumnModel().getColumn(0).setCellRenderer(TableStyles.colorSwatch());
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setCellRenderer(TableStyles.typeBadge());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private TransactionType selectedType() {
        int index = typeCombo.getSelectedIndex();
        return index <= 0 ? null : TransactionType.values()[index - 1];
    }

    @Override
    public void refresh() {
        try {
            currentRows = context.categoryService.totalsForScreen(ownerId(), selectedType());
        } catch (DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            currentRows = List.of();
        }
        budgetCounts.clear();
        model.setRowCount(0);
        String symbol = context.settings().currencySymbol();
        long totalTransactions = 0;
        for (CategoryTotal row : currentRows) {
            long budgetCount = context.categoryService.budgetCount(ownerId(), row.categoryId());
            budgetCounts.put(row.categoryId(), budgetCount);
            totalTransactions += row.transactionCount();
            model.addRow(new Object[]{
                    row.color(),
                    row.name(),
                    row.type(),
                    row.type().displayName(),
                    row.transactionCount(),
                    MoneyUtil.format(row.amount(), symbol),
                    budgetCount
            });
        }
        summaryLabel.setText(currentRows.size() + " danh mục  •  " + totalTransactions
                + " giao dịch đã ghi nhận");
        revalidate();
        repaint();
    }

    private CategoryTotal selected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0 || viewRow >= currentRows.size()) {
            Dialogs.info(this, "Hãy chọn một danh mục trong bảng trước.");
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return currentRows.get(modelRow);
    }

    private void editSelected() {
        CategoryTotal selected = selected();
        if (selected == null) {
            return;
        }
        Category category = context.categories.findById(ownerId(), selected.categoryId());
        if (category == null) {
            refresh();
            return;
        }
        new CategoryDialog(javax.swing.SwingUtilities.getWindowAncestor(this), context, category)
                .setVisible(true);
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private void deleteSelected() {
        CategoryTotal selected = selected();
        if (selected == null) {
            return;
        }
        long categoryId = selected.categoryId();
        long transactionCount = context.categoryService.transactionCount(ownerId(), categoryId);
        long budgetCount = budgetCounts.getOrDefault(categoryId,
                context.categoryService.budgetCount(ownerId(), categoryId));
        try {
            if (transactionCount == 0 && budgetCount == 0) {
                if (!Dialogs.confirmDelete(this, "danh mục '" + selected.name() + "'")) {
                    return;
                }
                context.categoryService.delete(ownerId(), categoryId, null);
            } else if (transactionCount == 0) {
                boolean confirmed = Dialogs.confirm(this, "Xoá danh mục",
                        "Danh mục '" + selected.name() + "' đang có " + budgetCount
                                + " ngân sách.\nXoá danh mục sẽ xoá luôn các ngân sách này.",
                        "Xoá danh mục", Theme.EXPENSE);
                if (!confirmed) {
                    return;
                }
                context.categoryService.delete(ownerId(), categoryId, null);
            } else {
                Category target = chooseReassignTarget(selected);
                if (target == null) {
                    return;
                }
                context.categoryService.delete(ownerId(), categoryId, target.id());
            }
        } catch (ValidationException | DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            return;
        }
        context.bus.fire(DataChangeBus.Topic.CATEGORIES);
    }

    /** Asks the user for a same-type target category when reassignment is needed. */
    private Category chooseReassignTarget(CategoryTotal selected) {
        List<Category> candidates = new ArrayList<>();
        for (Category category : context.categories.findByType(ownerId(), selected.type())) {
            if (category.id() != selected.categoryId()) {
                candidates.add(category);
            }
        }
        if (candidates.isEmpty()) {
            Dialogs.error(this, "Không có danh mục nào khác cùng loại để chuyển giao dịch sang. "
                    + "Hãy tạo danh mục mới hoặc xoá các giao dịch trước.");
            return null;
        }
        javax.swing.JComboBox<Category> combo = new javax.swing.JComboBox<>(
                candidates.toArray(new Category[0]));
        List<Object> message = new ArrayList<>();
        message.add("Danh mục '" + selected.name() + "' đang có " + selected.transactionCount()
                + " giao dịch.");
        message.add("Chọn danh mục đích để chuyển toàn bộ giao dịch sang:");
        message.add(combo);
        if (budgetCounts.getOrDefault(selected.categoryId(), 0L) > 0) {
            message.add("Lưu ý: các ngân sách của danh mục này cũng sẽ bị xoá.");
        }
        int choice = JOptionPane.showConfirmDialog(this, message.toArray(), "Xoá danh mục",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        return (Category) combo.getSelectedItem();
    }

    /** Small read-only dialog listing a category's transactions with totals. */
    private void viewSelected() {
        CategoryTotal selected = selected();
        if (selected == null) {
            return;
        }
        List<Transaction> transactions =
                context.transactionService.byCategory(ownerId(), selected.categoryId());
        String symbol = context.settings().currencySymbol();
        String pattern = context.settings().dateFormat();

        String[] columns = {"Ngày", "Loại", "Số tiền", "Ghi chú"};
        javax.swing.table.DefaultTableModel dialogModel =
                new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        long total = 0;
        for (Transaction transaction : transactions) {
            total += transaction.amount();
            dialogModel.addRow(new Object[]{
                    DateUtil.format(transaction.date(), pattern),
                    transaction.type().shortName(),
                    MoneyUtil.format(transaction.amount(), symbol),
                    transaction.note().isBlank() ? "—" : transaction.note()
            });
        }
        JTable dialogTable = new JTable(dialogModel);
        TableStyles.style(dialogTable);
        dialogTable.getColumnModel().getColumn(2).setPreferredWidth(130);

        JLabel totalLabel = new JLabel("Tổng cộng: " + MoneyUtil.format(total, symbol)
                + "  (" + transactions.size() + " giao dịch)");
        totalLabel.setFont(totalLabel.getFont().deriveFont(java.awt.Font.BOLD));
        totalLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Theme.CARD);
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        content.add(new JScrollPane(dialogTable), BorderLayout.CENTER);
        content.add(totalLabel, BorderLayout.SOUTH);

        Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner,
                "Danh mục: " + selected.name(), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(content);
        dialog.setSize(560, 420);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}