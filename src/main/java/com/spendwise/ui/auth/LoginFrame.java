package com.spendwise.ui.auth;

import com.spendwise.app.AppContext;
import com.spendwise.model.User;
import com.spendwise.service.SettingsService;
import com.spendwise.service.ValidationException;
import com.spendwise.util.AppIcon;
import com.spendwise.util.Icons;
import com.spendwise.util.Theme;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * First gate of the app: register a local account (first launch) or log in.
 * Real credential validation against PBKDF2 password hashes in SQLite.
 */
public final class LoginFrame extends JFrame {

    private final AppContext context;
    private final Runnable onSuccess;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    private JTextField loginUsername;
    private JPasswordField loginPassword;
    private final JLabel loginError = errorLabel();

    private JTextField regUsername;
    private JPasswordField regPassword;
    private JPasswordField regConfirm;
    private JTextField regDisplayName;
    private final JLabel regError = errorLabel();

    public LoginFrame(AppContext context, Runnable onSuccess) {
        super("SpendWise – Đăng nhập");
        this.context = context;
        this.onSuccess = onSuccess;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());
        setResizable(false);

        content.add(buildLoginPanel(), "login");
        content.add(buildRegisterPanel(), "register");
        setContentPane(content);

        show(context.users.countAll() == 0 ? "register" : "login");
        pack();
        setLocationRelativeTo(null);
    }

    private void show(String key) {
        cards.show(content, key);
        pack();
    }

    private JComponent header(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(Theme.SIDEBAR);
        header.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel logo = new JLabel(Icons.icon(Icons.Kind.WALLET, 38, Color.WHITE));
        JPanel text = new JPanel(new java.awt.GridLayout(2, 1));
        text.setOpaque(false);
        JLabel brand = new JLabel("SpendWise");
        brand.setForeground(Color.WHITE);
        brand.setFont(Theme.titleFont(18f));
        JLabel tagline = new JLabel(subtitle);
        tagline.setForeground(Theme.SIDEBAR_TEXT);
        tagline.setFont(tagline.getFont().deriveFont(11.5f));
        text.add(brand);
        text.add(tagline);
        header.add(logo, BorderLayout.WEST);
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.titleFont(16f));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private void addField(JPanel body, GridBagConstraints c, int row,
                          String label, JComponent field) {
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(10, 0, 2, 0);
        body.add(new javax.swing.JLabel(label), c);
        c.gridy = row + 1;
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        body.add(field, c);
        c.fill = GridBagConstraints.NONE;
    }

    private static JButton linkButton(String text) {
        JButton button = new javax.swing.JButton(text);
        button.setBorder(new EmptyBorder(2, 0, 2, 0));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(Theme.PRIMARY);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        return button;
    }

    private static JLabel errorLabel() {
        JLabel label = new javax.swing.JLabel(" ");
        label.setForeground(Theme.EXPENSE);
        label.setFont(label.getFont().deriveFont(11.5f));
        return label;
    }

    private JPanel buildLoginPanel() {
        loginUsername = new JTextField(16);
        loginPassword = new JPasswordField(16);

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(16, 20, 18, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        body.add(heading("Chào mừng trở lại!"), c);
        c.gridy = 1;
        c.insets = new Insets(4, 0, 10, 0);
        body.add(new JLabel("Đăng nhập để quản lý thu chi của bạn."), c);

        addField(body, c, 2, "Tên đăng nhập", loginUsername);
        addField(body, c, 4, "Mật khẩu", loginPassword);
        c.gridy = 6;
        c.insets = new Insets(8, 0, 0, 0);
        body.add(loginError, c);
        c.gridy = 7;
        c.insets = new Insets(10, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        JButton loginButton = Theme.primaryButton("Đăng nhập");
        loginButton.addActionListener(event -> performLogin());
        body.add(loginButton, c);
        c.fill = GridBagConstraints.NONE;
        c.gridy = 8;
        c.insets = new Insets(12, 0, 0, 0);
        JButton switchToRegister = linkButton("Chưa có tài khoản? Đăng ký ngay");
        switchToRegister.addActionListener(event -> {
            loginError.setText(" ");
            show("register");
        });
        body.add(switchToRegister, c);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header("Đăng nhập", "Tài khoản cục bộ, dữ liệu nằm trên máy bạn"),
                BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        getRootPane().setDefaultButton(loginButton);
        return root;
    }

    private JPanel buildRegisterPanel() {
        regUsername = new JTextField(16);
        regPassword = new JPasswordField(16);
        regConfirm = new JPasswordField(16);
        regDisplayName = new JTextField(16);

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(16, 20, 18, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.LINE_START;
        body.add(heading("Tạo tài khoản"), c);
        c.gridy = 1;
        c.insets = new Insets(4, 0, 10, 0);
        body.add(new JLabel("Mật khẩu chỉ lưu dưới dạng mã hoá PBKDF2."), c);

        addField(body, c, 2, "Tên đăng nhập", regUsername);
        addField(body, c, 4, "Mật khẩu (tối thiểu 6 ký tự)", regPassword);
        addField(body, c, 6, "Xác nhận mật khẩu", regConfirm);
        addField(body, c, 8, "Tên hiển thị (không bắt buộc)", regDisplayName);
        c.gridy = 10;
        c.insets = new Insets(8, 0, 0, 0);
        body.add(regError, c);
        c.gridy = 11;
        c.insets = new Insets(10, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        JButton registerButton = Theme.primaryButton("Đăng ký và bắt đầu");
        registerButton.addActionListener(event -> performRegister());
        body.add(registerButton, c);
        c.fill = GridBagConstraints.NONE;
        c.gridy = 12;
        c.insets = new Insets(12, 0, 0, 0);
        JButton switchToLogin = linkButton("Đã có tài khoản? Đăng nhập");
        switchToLogin.addActionListener(event -> {
            regError.setText(" ");
            show("login");
        });
        body.add(switchToLogin, c);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header("Đăng ký", "Tạo tài khoản cho ứng dụng trên máy này"),
                BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        getRootPane().setDefaultButton(registerButton);
        return root;
    }

    private void performLogin() {
        loginError.setText(" ");
        String username = loginUsername.getText().trim();
        String password = new String(loginPassword.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            loginError.setText("Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }
        try {
            User user = context.authService.login(username, password);
            finishAndContinue(user);
        } catch (ValidationException ex) {
            loginError.setText(ex.getMessage());
        }
    }

    private void performRegister() {
        regError.setText(" ");
        String password = new String(regPassword.getPassword());
        if (!password.equals(new String(regConfirm.getPassword()))) {
            regError.setText("Mật khẩu xác nhận không khớp.");
            return;
        }
        try {
            User user = context.authService.register(
                    regUsername.getText(), password, regDisplayName.getText());
            finishAndContinue(user);
        } catch (ValidationException ex) {
            regError.setText(ex.getMessage());
        }
    }

    /** Signs in, optionally copies the display name into the greeting, opens the app. */
    private void finishAndContinue(User user) {
        context.signIn(user);
        if (context.settings().profileName().isBlank() && !user.displayName().isBlank()) {
            context.settingsService.set(SettingsService.KEY_PROFILE, user.displayName());
            context.saveSettings(context.settingsService.load());
            context.bus.fire(com.spendwise.app.DataChangeBus.Topic.SETTINGS);
        }
        dispose();
        onSuccess.run();
    }
}