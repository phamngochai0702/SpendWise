package com.spendwise.ui.dialogs;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import com.spendwise.service.ValidationException;
import com.spendwise.util.Theme;

import javax.swing.ButtonGroup;
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

/** Create/edit a category; income/expense type is fixed once created. */
public final class CategoryDialog extends JDialog {

    private final AppContext context;
    private final Category existing;

    private JTextField nameField;
    private JComboBox<TransactionType> typeCombo;
    private final java.util.List<ColorSwatchButton> swatches = new ArrayList<>();
    private final ButtonGroup swatchGroup = new ButtonGroup();
    private String selectedColor = "#0F766E";

    private final JLabel nameError = fieldError();
    private final JLabel typeError = fieldError();
    private final JLabel dialogError = fieldError();

    public CategoryDialog(Window owner, AppContext context, Category existing) {
        super(owner, existing == null ? "Thêm danh mục" : "Sửa danh mục",
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

        nameField = new JTextField(18);
        typeCombo = new JComboBox<>(TransactionType.values());
        buildSwatches();
        if (existing != null) {
            nameField.setText(existing.name());
            typeCombo.setSelectedItem(existing.type());
            typeCombo.setEnabled(false);
            selectedColor = existing.color() == null || existing.color().isBlank()
                    ? selectedColor : existing.color();
        }

        // preselect current color
        for (ColorSwatchButton swatch : swatches) {
            swatch.setSelected(swatch.hex.equalsIgnoreCase(selectedColor));
        }
        pack();
        setMinimumSize(new Dimension(430, 0));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(6, 0, 2, 12);
        form.add(new JLabel("Tên danh mục"), c);
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(nameField, c);
        c.gridy = 2;
        form.add(nameError, c);
        c.gridy = 3;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Loại"), c);
        c.gridy = 4;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(typeCombo, c);
        c.gridy = 5;
        form.add(typeError, c);
        c.gridy = 6;
        c.insets = new Insets(10, 0, 2, 12);
        form.add(new JLabel("Màu hiển thị"), c);
        c.gridy = 7;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(buildSwatchRow(), c);
        c.gridy = 8;
        form.add(dialogError, c);
        return form;
    }

    private JPanel buildSwatchRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.setOpaque(false);
        for (String hex : Theme.DONUT_COLORS) {
            ColorSwatchButton swatch = new ColorSwatchButton(hex);
            swatches.add(swatch);
            swatchGroup.add(swatch);
            row.add(swatch);
        }
        return row;
    }

    private void buildSwatches() {
        // created inside buildSwatchRow(); kept for clarity of call order
    }

    private JPanel buildActions() {
        JButton save = Theme.primaryButton(existing == null ? "Thêm danh mục" : "Lưu thay đổi");
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
        nameError.setText(" ");
        typeError.setText(" ");
        dialogError.setText(" ");
        try {
            if (existing == null) {
                context.categoryService.add(ownerId(), nameField.getText(),
                        (TransactionType) typeCombo.getSelectedItem(), selectedColor);
            } else {
                context.categoryService.update(ownerId(), existing.id(),
                        nameField.getText(), selectedColor);
            }
        } catch (ValidationException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("loại")) {
                typeError.setText(message);
            } else {
                nameError.setText(message);
            }
            return;
        }
        context.bus.fire(DataChangeBus.Topic.CATEGORIES);
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

    /** Small color choice button used by the palette row. */
    private static final class ColorSwatchButton extends javax.swing.JToggleButton {

        private final String hex;
        private final Color color;

        ColorSwatchButton(String hex) {
            this.hex = hex;
            this.color = Color.decode(hex);
            setActionCommand(hex);
            setPreferredSize(new Dimension(30, 22));
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setToolTipText(hex);
            addActionListener((ActionEvent event) -> { /* selection handled by ButtonGroup */ });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (isSelected()) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 6, 6);
            }
            g2.dispose();
        }
    }
}