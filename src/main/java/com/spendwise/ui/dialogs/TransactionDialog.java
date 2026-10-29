package com.spendwise.ui.dialogs;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Category;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionType;
import com.spendwise.service.TransactionService;
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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.util.List;

/** Add/edit form for income and expense records, with per-field validation. */
public final class TransactionDialog extends JDialog {

    private final AppContext context;
    private final Transaction existing;

    private JComboBox<TransactionType> typeCombo;
    private JTextField amountField;
    private JComboBox<Category> categoryCombo;
    private DateField dateField;
    private JTextArea noteArea;

    private final JLabel typeError = fieldError();
    private final JLabel amountError = fieldError();
    private final JLabel categoryError = fieldError();
    private final JLabel noteError = fieldError();
    private final JLabel dialogError = fieldError();

    public TransactionDialog(Window owner, AppContext context, Transaction existing) {
        super(owner, existing == null ? "Thêm giao dịch" : "Sửa giao dịch",
                ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.existing = existing;

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.CARD);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));
        root.add(buildForm(), BorderLayout.CENTER);
        root.add(buildActions(), BorderLayout.SOUTH);

        setContentPane(root);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        configureAndPrefill();

        pack();
        setMinimumSize(new Dimension(460, 0));
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        typeCombo = new JComboBox<>(TransactionType.values());
        amountField = new JTextField(14);
        categoryCombo = new JComboBox<>();
        dateField = new DateField(context.settings().dateFormat(), LocalDate.now());
        noteArea = new JTextArea(3, 20);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        JScrollPane noteScroll = new JScrollPane(noteArea);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(6, 0, 2, 12);
        form.add(new JLabel("Loại"), c);
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(typeCombo, c);
        c.gridy = 2;
        form.add(typeError, c);
        c.gridy = 3;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Số tiền"), c);
        c.gridy = 4;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(amountField, c);
        c.gridy = 5;
        form.add(amountError, c);
        c.gridy = 6;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Danh mục"), c);
        c.gridy = 7;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(categoryCombo, c);
        c.gridy = 8;
        form.add(categoryError, c);
        c.gridy = 9;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Ngày giao dịch"), c);
        c.gridy = 10;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(dateField, c);
        c.gridy = 12;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Ghi chú"), c);
        c.gridy = 13;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(noteScroll, c);
        c.gridy = 14;
        form.add(noteError, c);
        c.gridy = 15;
        form.add(dialogError, c);
        return form;
    }

    private JPanel buildActions() {
        JButton save = Theme.primaryButton(existing == null ? "Thêm giao dịch" : "Lưu thay đổi");
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
    private void configureAndPrefill() {
        typeCombo.addActionListener(event ->
                rebuildCategories(existing == null ? -1 : existing.categoryId()));
        rebuildCategories(existing == null ? -1 : existing.categoryId());
        if (existing != null) {
            typeCombo.setSelectedItem(existing.type());
            typeCombo.setEnabled(false);
            amountField.setText(MoneyUtil.group(existing.amount()));
            noteArea.setText(existing.note());
            dateField.setValue(existing.date());
        }
        SwingUtilities.invokeLater(amountField::requestFocusInWindow);
    }

    private void rebuildCategories(long preferredId) {
        TransactionType type = (TransactionType) typeCombo.getSelectedItem();
        DefaultComboBoxModel<Category> model = new DefaultComboBoxModel<>();
        List<Category> categories = context.categories.findByType(ownerId(), type);
        for (Category category : categories) {
            model.addElement(category);
        }
        if (preferredId > 0) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).id() == preferredId) {
                    model.setSelectedItem(model.getElementAt(i));
                    break;
                }
            }
        }
        categoryCombo.setModel(model);
        categoryError.setText(model.getSize() == 0
                ? "Chưa có danh mục " + (type == TransactionType.INCOME ? "thu nhập" : "chi tiêu")
                + ". Hãy tạo ở màn hình Danh mục."
                : " ");
    }

    private void clearErrors() {
        for (JLabel label : new JLabel[]{typeError, amountError, categoryError, noteError}) {
            label.setText(" ");
        }
        dialogError.setText(" ");
    }

    private void save() {
        clearErrors();
        TransactionType type = (TransactionType) typeCombo.getSelectedItem();
        Category category = (Category) categoryCombo.getSelectedItem();
        long amount = 0;
        try {
            amount = MoneyUtil.parse(amountField.getText());
        } catch (ValidationException ex) {
            amountError.setText(ex.getMessage());
            return;
        }
        boolean invalid = false;
        if (type == null) {
            typeError.setText("Vui lòng chọn loại giao dịch.");
            invalid = true;
        }
        if (amount <= 0) {
            amountError.setText("Số tiền phải lớn hơn 0.");
            invalid = true;
        }
        if (category == null) {
            categoryError.setText("Vui lòng chọn danh mục.");
            invalid = true;
        }
        String note = noteArea.getText().trim();
        if (note.length() > TransactionService.MAX_NOTE_LENGTH) {
            noteError.setText("Ghi chú tối đa " + TransactionService.MAX_NOTE_LENGTH + " ký tự.");
            invalid = true;
        }
        if (invalid) {
            return;
        }
        LocalDate date = dateField.getValue();
        try {
            if (existing == null) {
                context.transactionService.add(ownerId(), type, amount,
                        category.id(), date, note);
            } else {
                context.transactionService.update(ownerId(), existing.id(), type, amount,
                        category.id(), date, note);
            }
        } catch (ValidationException ex) {
            dialogError.setText(ex.getMessage());
            return;
        }
        context.bus.fire(DataChangeBus.Topic.TRANSACTIONS);
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