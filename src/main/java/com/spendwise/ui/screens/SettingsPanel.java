package com.spendwise.ui.screens;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.model.AppSettings;
import com.spendwise.model.PeriodKey;
import com.spendwise.repository.DataAccessException;
import com.spendwise.ui.Refreshable;
import com.spendwise.util.Dialogs;
import com.spendwise.util.MoneyUtil;
import com.spendwise.util.Theme;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;

/**
 * Application preferences with Vietnamese defaults (VND, dd/MM/yyyy) plus
 * storage info and a guarded "delete all data" maintenance action.
 * All preferences are persisted in the SQLite settings table.
 */
public final class SettingsPanel extends JPanel implements Refreshable {

    private final AppContext context;
    private final JTextField profileField = new JTextField(18);
    private final JComboBox<String> currencyCombo =
            new JComboBox<>(new String[]{"₫ (Việt Nam)", "đ (Việt Nam)", "VND"});
    private final JComboBox<String> dateFormatCombo =
            new JComboBox<>(new String[]{"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd"});
    private final JComboBox<String> periodCombo =
            new JComboBox<>(java.util.Arrays.stream(PeriodKey.values())
                    .map(PeriodKey::label).toArray(String[]::new));
    private final JComboBox<String> delimiterCombo =
            new JComboBox<>(new String[]{", (dấu phẩy)", "; (dấu chấm phẩy)"});

    private final JLabel dbPathValue = new JLabel();
    private final JLabel dbSizeValue = new JLabel();
    private final JLabel recordCountValue = new JLabel();

    public SettingsPanel(AppContext context) {
        super(new BorderLayout(0, 12));
        this.context = context;
        setOpaque(false);
        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(buildBody(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        refresh();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new java.awt.GridLayout(2, 1));
        header.setOpaque(false);
        JLabel title = new JLabel("Cài đặt");
        title.setFont(Theme.titleFont(20f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Tuỳ biến hiển thị; dữ liệu được lưu tự động trong máy của bạn.");
        subtitle.setForeground(Theme.MUTED);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 12, 0);
        body.add(preferencesCard(), c);
        c.gridy = 1;
        body.add(infoCard(), c);
        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        body.add(dangerCard(), c);
        return body;
    }

    private JPanel preferencesCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(6, 0, 2, 16);

        c.gridx = 0;
        c.gridy = 0;
        card.add(sectionTitle("Cá nhân hoá và hiển thị"), c);

        addRow(card, c, 1, "Tên gọi của bạn", profileField);
        addRow(card, c, 3, "Đơn vị tiền tệ", currencyCombo);
        addRow(card, c, 5, "Định dạng ngày", dateFormatCombo);
        addRow(card, c, 7, "Kỳ mặc định (bảng điều khiển)", periodCombo);
        addRow(card, c, 9, "Phân cách CSV", delimiterCombo);

        c.gridx = 0;
        c.gridy = 11;
        c.gridwidth = 2;
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        saveRow.setOpaque(false);
        JButton saveButton = Theme.primaryButton("Lưu cài đặt");
        saveButton.addActionListener(event -> save());
        saveRow.add(saveButton);
        card.add(saveRow, c);
        c.gridwidth = 1;
        return card;
    }

    private void addRow(JPanel card, GridBagConstraints c, int row,
                        String label, java.awt.Component field) {
        c.gridy = row;
        c.gridx = 0;
        c.insets = new Insets(10, 0, 2, 16);
        card.add(new JLabel(label), c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(field, c);
    }

    private JPanel infoCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(4, 0, 2, 16);
        c.gridx = 0;
        c.gridy = 0;
        card.add(sectionTitle("Dữ liệu và lưu trữ"), c);

        c.gridy = 1;
        c.gridx = 0;
        card.add(new javax.swing.JLabel("Tệp cơ sở dữ liệu:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        dbPathValue.setForeground(Theme.TEXT);
        card.add(dbPathValue, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 2;
        c.insets = new Insets(8, 0, 2, 16);
        card.add(new javax.swing.JLabel("Dung lượng:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        dbSizeValue.setForeground(Theme.TEXT);
        card.add(dbSizeValue, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 3;
        c.insets = new Insets(8, 0, 2, 16);
        card.add(new javax.swing.JLabel("Số bản ghi:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        recordCountValue.setForeground(Theme.TEXT);
        card.add(recordCountValue, c);

        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 4;
        c.insets = new Insets(12, 0, 0, 0);
        JPanel infoActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoActions.setOpaque(false);
        JButton openFolder = new JButton("Mở thư mục dữ liệu");
        openFolder.addActionListener(event -> openDataFolder());
        JButton reloadInfo = new JButton("Cập nhật");
        reloadInfo.addActionListener(event -> refresh());
        infoActions.add(openFolder);
        infoActions.add(reloadInfo);
        card.add(infoActions, c);
        return card;
    }

    private JPanel dangerCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 8));
        card.add(sectionTitle("Vùng nguy hiểm"), BorderLayout.NORTH);
        JTextArea warningText = new JTextArea(
                "Xoá toàn bộ giao dịch, ngân sách và danh mục. Cài đặt hiển thị được giữ nguyên. "
                        + "Hành động không thể hoàn tác - hãy cân nhắc xuất CSV trước khi tiếp tục.");
        warningText.setLineWrap(true);
        warningText.setWrapStyleWord(true);
        warningText.setEditable(false);
        warningText.setOpaque(false);
        warningText.setForeground(Theme.MUTED);
        card.add(warningText, BorderLayout.CENTER);
        JButton deleteAll = Theme.dangerButton("Xoá toàn bộ dữ liệu");
        deleteAll.addActionListener(event -> deleteAllData());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.setOpaque(false);
        buttonRow.add(deleteAll);
        card.add(buttonRow, BorderLayout.SOUTH);
        return card;
    }

    private javax.swing.JLabel sectionTitle(String text) {
        javax.swing.JLabel label = new javax.swing.JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13.5f));
        label.setForeground(Theme.TEXT);
        return label;
    }

    @Override
    public void refresh() {
        AppSettings settings = context.settings();
        profileField.setText(settings.profileName());
        currencyCombo.setSelectedIndex(switch (settings.currencySymbol()) {
            case "₫" -> 0;
            case "đ" -> 1;
            default -> 2;
        });
        dateFormatCombo.setSelectedItem(settings.dateFormat());
        periodCombo.setSelectedIndex(settings.defaultPeriod().ordinal());
        delimiterCombo.setSelectedIndex(settings.csvDelimiter().equals(";") ? 1 : 0);

        dbPathValue.setText(context.dbPath);
        dbPathValue.setToolTipText(context.dbPath);
        dbSizeValue.setText(formatBytes(context.database.dbSizeBytes()));
        long uid = ownerId();
        long transactionCount = context.transactions.countAll(uid);
        long budgetCount = context.budgets.countAll(uid);
        long categoryCount = context.categories.countAll(uid);
        recordCountValue.setText(transactionCount + " giao dịch, " + budgetCount
                + " ngân sách, " + categoryCount + " danh mục");
    }

    private long ownerId() {
        return context.currentUser() == null ? 0 : context.currentUser().id();
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1_048_576) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
        }
        if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private void save() {
        AppSettings updated = new AppSettings();
        updated.setProfileName(profileField.getText());
        updated.setCurrencySymbol(switch (currencyCombo.getSelectedIndex()) {
            case 0 -> "₫";
            case 1 -> "đ";
            default -> "VND";
        });
        updated.setDateFormat((String) dateFormatCombo.getSelectedItem());
        updated.setDefaultPeriod(PeriodKey.values()[periodCombo.getSelectedIndex()]);
        updated.setCsvDelimiter(delimiterCombo.getSelectedIndex() == 1 ? ";" : ",");
        context.saveSettings(updated);
        context.bus.fire(DataChangeBus.Topic.SETTINGS);
        Dialogs.info(this, "Đã lưu cài đặt.");
    }

    private void openDataFolder() {
        try {
            java.io.File folder = java.nio.file.Path.of(context.dbPath)
                    .toAbsolutePath().getParent().toFile();
            Desktop.getDesktop().open(folder);
        } catch (Exception ex) {
            Dialogs.error(this, "Không thể mở thư mục: " + ex.getMessage());
        }
    }

    /** Double-confirmed wipe of all financial data; settings are preserved. */
    private void deleteAllData() {
        boolean first = Dialogs.confirmDelete(this,
                "TOÀN BỘ dữ liệu (giao dịch, ngân sách, danh mục)");
        if (!first) {
            return;
        }
        boolean second = Dialogs.confirm(this, "Xác nhận lần cuối",
                "Mọi giao dịch, ngân sách và danh mục sẽ bị xoá vĩnh viễn.",
                "Xoá vĩnh viễn", Theme.EXPENSE);
        if (!second) {
            return;
        }
        try {
            context.database.inTransaction(() -> {
                long uid = ownerId();
                context.database.update("DELETE FROM transactions WHERE owner_id = ?", uid);
                context.database.update("DELETE FROM budgets WHERE owner_id = ?", uid);
                context.database.update("DELETE FROM categories WHERE owner_id = ?", uid);
                context.database.update(
                        "DELETE FROM monthly_budget_items WHERE budget_id IN "
                                + "(SELECT id FROM monthly_budgets WHERE owner_id = ?)", uid);
                context.database.update(
                        "DELETE FROM monthly_budgets WHERE owner_id = ?", uid);
            });
            context.categoryService.seedDefaultsForOwner(ownerId());
        } catch (com.spendwise.repository.DataAccessException ex) {
            Dialogs.error(this, ex.getMessage());
            return;
        }
        context.bus.fireAll();
        Dialogs.info(this, "Đã xoá toàn bộ dữ liệu. Danh mục mặc định được tạo lại.");
        refresh();
    }
}