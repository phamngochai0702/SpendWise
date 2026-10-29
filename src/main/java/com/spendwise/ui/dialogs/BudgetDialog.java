package com.spendwise.ui.dialogs;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Budget;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import com.spendwise.service.ValidationException;
import com.spendwise.ui.components.DateField;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.util.List;

/** Create/edit a budget for one expense category over an inclusive date range. */
public final class BudgetDialog extends JDialog {

    private final AppContext context;
    private final Budget existing;

    private JComboBox<Category> categoryCombo;
    private JTextField limitField;
    private DateField startField;
    private DateField endField;

    private final JLabel limitError = fieldError();
    private final JLabel rangeError = fieldError();
    private final JLabel dialogError = fieldError();

    public BudgetDialog(Window owner, AppContext context, Budget existing) {
        super(owner, existing == null ? "Thêm ngân sách" : "Sửa ngân sách",
                ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.existing = existing;

        limitField = new JTextField(14);
        DefaultComboBoxModel<Category> model = new DefaultComboBoxModel<>();
        List<Category> expenseCategories =
                context.categories.findByType(ownerId(), TransactionType.EXPENSE);
        for (Category category : expenseCategories) {
            model.addElement(category);
        }
        categoryCombo = new JComboBox<>(model);

        LocalDate today = LocalDate.now();
        String pattern = context.settings().dateFormat();
        startField = new DateField(pattern,
                existing == null ? today.withDayOfMonth(1) : existing.start());
        endField = new DateField(pattern,
                existing == null ? today.withDayOfMonth(today.lengthOfMonth()) : existing.end());

        if (existing != null) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).id() == existing.categoryId()) {
                    model.setSelectedItem(model.getElementAt(i));
                    break;
                }
            }
            limitField.setText(MoneyUtil.group(existing.limitAmount()));
        }

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.CARD);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));
        root.add(buildForm(), BorderLayout.CENTER);
        root.add(buildActions(), BorderLayout.SOUTH);

        setContentPane(root);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(470, 0));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(limitField::requestFocusInWindow);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(6, 0, 2, 12);
        form.add(new JLabel("Danh mục chi tiêu"), c);
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(categoryCombo, c);

        c.gridy = 2;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Hạn mức"), c);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(limitField, c);
        c.gridy = 4;
        form.add(limitError, c);

        c.gridy = 5;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Ngày bắt đầu"), c);
        c.gridy = 6;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(startField, c);

        c.gridy = 7;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Ngày kết thúc"), c);
        c.gridy = 8;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(endField, c);
        c.gridy = 9;
        form.add(rangeError, c);
        c.gridy = 10;
        form.add(dialogError, c);
        return form;
    }

    private JPanel buildActions() {
        JButton save = Theme.primaryButton(existing == null ? "Thêm ngân sách" : "Lưu thay đổi");
        save.addActionListener(event -> save());
        JButton cancel = new JButton("Huỷ");
        cancel.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(save);
        getRootPane().setDefaultButton(save);
        return actions;
    }

    private void save() {
        limitError.setText(" ");
        rangeError.setText(" ");
        dialogError.setText(" ");

        Category category = (Category) categoryCombo.getSelectedItem();
        if (category == null) {
            dialogError.setText("Chưa có danh mục chi tiêu. Hãy tạo ở màn hình Danh mục.");
            return;
        }
        long limit;
        try {
            limit = MoneyUtil.parse(limitField.getText());
        } catch (ValidationException ex) {
            limitError.setText(ex.getMessage());
            return;
        }
        LocalDate start = startField.getValue();
        LocalDate end = endField.getValue();
        try {
            if (existing == null) {
                context.budgetService.add(ownerId(), category.id(), limit, start, end);
            } else {
                context.budgetService.update(ownerId(), existing.id(), category.id(),
                        limit, start, end);
            }
        } catch (ValidationException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("Hạn mức")) {
                limitError.setText(message);
            } else if (message.contains("kết thúc") || message.contains("Trùng")
                    || message.contains("thời gian")) {
                rangeError.setText(message);
            } else {
                dialogError.setText(message);
            }
            return;
        }
        context.bus.fire(DataChangeBus.Topic.BUDGETS);
        dispose();
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private static JLabel fieldError() {
        JLabel label = new JLabel(" ");
        label.setForeground(Theme.EXPENSE);
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }
}