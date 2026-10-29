package com.spendwise;

import com.spendwise.app.AppContext;
import com.spendwise.model.DashboardSnapshot;
import com.spendwise.model.PeriodKey;
import com.spendwise.ui.MainFrame;
import com.spendwise.ui.auth.LoginFrame;
import com.spendwise.util.Theme;

import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import java.time.LocalDate;

/**
 * SpendWise application entry point.
 *
 * <p>Run directly from an IDE (IntelliJ IDEA / VS Code) by executing
 * {@code com.spendwise.Main#main}, or via Maven:</p>
 * <pre>
 *   mvn clean package
 *   java -jar target/spendwise.jar
 * </pre>
 *
 * <p>The SQLite database is created automatically on first launch at
 * {@code %USERPROFILE%\SpendWise\spendwise.db}. A custom location can be set with the
 * system property {@code -Dspendwise.db.path=...}.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String dbPath = System.getProperty("spendwise.db.path", AppContext.defaultDatabasePath());
        try {
            AppContext context = new AppContext(dbPath);

            if (GraphicsEnvironment.isHeadless()) {
                // Machine-readable smoke check: initializes the real database and services
                // without opening a window. Used by automated verification runs.
                runHeadlessSmoke(context);
                return;
            }

            Theme.apply();
            SwingUtilities.invokeLater(() -> {
                try {
                    // Authentication first: register (first launch) or log in.
                    LoginFrame login = new LoginFrame(context, () -> openMainWindow(context));
                    login.setVisible(true);
                } catch (RuntimeException ex) {
                    showStartupError(ex);
                    System.exit(1);
                }
            });
        } catch (RuntimeException ex) {
            showStartupError(ex);
            System.exit(1);
        }
    }

    private static void openMainWindow(AppContext context) {
        try {
            new MainFrame(context).setVisible(true);
        } catch (RuntimeException ex) {
            showStartupError(ex);
            System.exit(1);
        }
    }

    /** Smoke run for headless verification: registers a scratch account, seeds data. */
    private static void runHeadlessSmoke(AppContext context) {
        AppContext ctx = context;
        if (ctx.users.countAll() == 0) {
            ctx.authService.register("smoke.user", "smoke-pass-123", "Smoke");
        }
        com.spendwise.model.User user = ctx.users.findByUsername("smoke.user");
        if (user == null) {
            user = ctx.users.findFirst();
        }
        if (user == null) {
            System.out.println("SPENDWISE_SMOKE_NO_USER");
            return;
        }
        ctx.signIn(user);
        DashboardSnapshot snapshot =
                ctx.reportService.buildDashboard(user.id(), PeriodKey.THIS_MONTH, LocalDate.now());
        System.out.println("SPENDWISE_SMOKE_OK");
        System.out.println("user=" + user.username());
        System.out.println("db=" + ctx.dbPath);
        System.out.println("categories=" + ctx.categories.countAll(user.id()));
        System.out.println("transactions=" + ctx.transactions.countAll(user.id()));
        System.out.println("month_income=" + snapshot.incomeTotal());
        System.out.println("month_expense=" + snapshot.expenseTotal());
    }

    private static void showStartupError(Throwable ex) {
        ex.printStackTrace();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to whatever look-and-feel is active.
        }
        JOptionPane.showMessageDialog(
                null,
                "Khong the khoi dong SpendWise!\n"
                        + describe(ex)
                        + "\n\nChi tiet loi duoc ghi ra console.",
                "Loi khoi dong",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String describe(Throwable ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank())
                ? ex.getClass().getName()
                : message;
    }
}
