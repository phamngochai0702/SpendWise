package com.spendwise.ui;

import com.spendwise.app.AppContext;
import com.spendwise.app.DataChangeBus;
import com.spendwise.service.SettingsService;
import com.spendwise.ui.screens.BudgetsPanel;
import com.spendwise.ui.screens.CategoriesPanel;
import com.spendwise.ui.screens.DashboardPanel;
import com.spendwise.ui.screens.ReportsPanel;
import com.spendwise.ui.screens.SettingsPanel;
import com.spendwise.ui.screens.TransactionsPanel;
import com.spendwise.ui.auth.LoginFrame;
import com.spendwise.util.AppIcon;
import com.spendwise.util.Icons;
import com.spendwise.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application shell: teal sidebar navigation + CardLayout content area.
 * Every data change goes through the {@link DataChangeBus} and the visible
 * screen reloads itself from the database, keeping all values in sync.
 */
public final class MainFrame extends JFrame {

    private static final Color NAV_TEXT = Theme.SIDEBAR_TEXT;

    private final AppContext context;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, JPanel> screens = new LinkedHashMap<>();
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private String currentKey;

    public MainFrame(AppContext context) {
        super("SpendWise");
        this.context = context;
        if (context.currentUser() == null) {
            throw new IllegalStateException("Chưa có phiên đăng nhập hợp lệ.");
        }
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());
        setSize(1240, 800);
        setMinimumSize(new Dimension(1120, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

        register("dashboard", new DashboardPanel(context), "Bảng điều khiển", Icons.Kind.DASHBOARD);
        register("transactions", new TransactionsPanel(context), "Giao dịch", Icons.Kind.TRANSACTIONS);
        register("categories", new CategoriesPanel(context), "Danh mục", Icons.Kind.CATEGORIES);
        register("budgets", new BudgetsPanel(context), "Ngân sách", Icons.Kind.BUDGETS);
        register("reports", new ReportsPanel(context), "Báo cáo", Icons.Kind.REPORTS);
        register("settings", new SettingsPanel(context), "Cài đặt", Icons.Kind.SETTINGS);

        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(14, 16, 16, 16));
        add(buildSidebar(), BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        for (DataChangeBus.Topic topic : DataChangeBus.Topic.values()) {
            context.bus.on(topic, () ->
                    SwingUtilities.invokeLater(this::refreshCurrent));
        }

        showPanel("dashboard");
        SwingUtilities.invokeLater(this::showWelcomeIfFirstRun);
    }

    private void register(String key, JPanel screen, String title, Icons.Kind icon) {
        screens.put(key, screen);
        contentPanel.add(screen, key);
        navItems.put(key, new NavItem(key, title, icon));
    }

    private void showPanel(String key) {
        currentKey = key;
        cardLayout.show(contentPanel, key);
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(key));
        }
        refreshCurrent();
    }

    private void refreshCurrent() {
        if (currentKey == null) {
            return;
        }
        JPanel screen = screens.get(currentKey);
        if (screen instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(230, 0));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(22, 18, 20, 12));
        JLabel logo = new JLabel(Icons.icon(Icons.Kind.WALLET, 30, Color.WHITE));
        JPanel brandText = new JPanel(new GridLayout(2, 1));
        brandText.setOpaque(false);
        JLabel appName = new JLabel("SpendWise");
        appName.setForeground(Color.WHITE);
        appName.setFont(Theme.titleFont(17f));
        JLabel tagline = new JLabel("Quản lý thu chi");
        tagline.setForeground(Theme.SIDEBAR_TEXT);
        tagline.setFont(tagline.getFont().deriveFont(11.5f));
        brandText.add(appName);
        brandText.add(tagline);
        brand.add(logo, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);

        JPanel navList = new JPanel(new GridBagLayout());
        navList.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 10, 2, 10);
        int row = 0;
        for (NavItem item : navItems.values()) {
            c.gridy = row++;
            navList.add(item.component(), c);
        }

        String displayName = context.currentUser().displayName().isBlank()
                ? context.currentUser().username()
                : context.currentUser().displayName();
        JLabel userLabel = new JLabel(displayName,
                Icons.icon(Icons.Kind.USER, 16, Color.WHITE), JLabel.LEFT);
        userLabel.setForeground(Color.WHITE);
        userLabel.setFont(userLabel.getFont().deriveFont(java.awt.Font.BOLD, 12.5f));

        JButton logoutButton = new javax.swing.JButton("Đăng xuất");
        logoutButton.setForeground(Theme.SIDEBAR_TEXT);
        logoutButton.setContentAreaFilled(false);
        logoutButton.setBorderPainted(false);
        logoutButton.setFocusPainted(false);
        logoutButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        logoutButton.setFont(logoutButton.getFont().deriveFont(11.5f));
        logoutButton.addActionListener(event -> {
            context.signOut();
            dispose();
            new LoginFrame(context, () -> openAfterLogin(context)).setVisible(true);
        });

        JLabel version = new JLabel("Phiên bản 1.0.0");
        version.setForeground(new Color(0x8FBDB7));
        version.setFont(version.getFont().deriveFont(11f));

        JPanel south = new JPanel(new java.awt.GridLayout(3, 1, 0, 2));
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(8, 18, 16, 8));
        south.add(userLabel);
        south.add(logoutButton);
        south.add(version);

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(navList, BorderLayout.CENTER);
        sidebar.add(south, BorderLayout.SOUTH);
        return sidebar;
    }

    /** Re-opens the main window after a fresh login (used by logout). */
    private static void openAfterLogin(AppContext context) {
        new MainFrame(context).setVisible(true);
    }

    /** Sidebar entry with hover/selected visual state. */
    private final class NavItem {

        private final String key;
        private final Icons.Kind kind;
        private final JPanel panel;
        private final JLabel label;
        private boolean selected;

        NavItem(String key, String title, Icons.Kind kind) {
            this.key = key;
            this.kind = kind;
            this.panel = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (selected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(255, 255, 255, 26));
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                        g2.setColor(Color.WHITE);
                        g2.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
                        g2.dispose();
                    }
                }
            };
            panel.setOpaque(false);
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label = new JLabel(title, Icons.nav(kind), JLabel.LEFT);
            label.setForeground(NAV_TEXT);
            label.setBorder(new EmptyBorder(9, 10, 9, 10));
            panel.add(label, BorderLayout.CENTER);
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showPanel(key);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!selected) {
                        label.setForeground(Color.WHITE);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!selected) {
                        label.setForeground(NAV_TEXT);
                    }
                }
            });
        }

        JComponent component() {
            return panel;
        }

        void setSelected(boolean value) {
            this.selected = value;
            label.setForeground(value ? Color.WHITE : NAV_TEXT);
            label.setIcon(Icons.icon(kind, 20, value ? Color.WHITE : NAV_TEXT));
            panel.repaint();
        }
    }

    /** Vietnamese first-run setup: ask for a friendly profile name (skippable). */
    private void showWelcomeIfFirstRun() {
        if (!context.settings().profileName().isBlank()) {
            return;
        }
        JDialog dialog = new JDialog(this, "Chào mừng đến với SpendWise",
                JDialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Theme.CARD);
        root.setBorder(new EmptyBorder(20, 22, 20, 22));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(new JLabel(Icons.icon(Icons.Kind.WALLET, 42, Theme.PRIMARY)), BorderLayout.WEST);
        JPanel headerText = new JPanel(new GridLayout(2, 1));
        headerText.setOpaque(false);
        JLabel title = new JLabel("Chào mừng bạn!");
        title.setFont(Theme.titleFont(17f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Nhập tên gọi để cá nhân hoá ứng dụng (có thể bỏ trống).");
        subtitle.setForeground(Theme.MUTED);
        headerText.add(title);
        headerText.add(subtitle);
        header.add(headerText, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        form.add(new JLabel("Tên gọi của bạn"), c);
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 0, 0, 0);
        JTextField nameField = new JTextField();
        form.add(nameField, c);

        JButton start = Theme.primaryButton("Bắt đầu");
        start.addActionListener(event -> {
            String name = nameField.getText().trim();
            context.settingsService.set(SettingsService.KEY_PROFILE, name);
            context.saveSettings(context.settingsService.load());
            context.bus.fire(DataChangeBus.Topic.SETTINGS);
            dialog.dispose();
        });
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(start, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(start);
        dialog.setSize(440, 250);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}