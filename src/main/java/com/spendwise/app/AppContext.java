package com.spendwise.app;

import com.spendwise.model.AppSettings;
import com.spendwise.model.User;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.DatabaseManager;
import com.spendwise.repository.MonthlyBudgetRepository;
import com.spendwise.repository.SettingsRepository;
import com.spendwise.repository.TransactionRepository;
import com.spendwise.repository.UserRepository;
import com.spendwise.service.AuthService;
import com.spendwise.service.BudgetService;
import com.spendwise.service.CategoryService;
import com.spendwise.service.MonthlyBudgetService;
import com.spendwise.service.ReportService;
import com.spendwise.service.SettingsService;
import com.spendwise.service.TransactionService;

import java.nio.file.Path;

/**
 * Manual dependency wiring for the whole application (no framework needed).
 * A single instance is created at startup and shared with every screen.
 */
public final class AppContext {

    /** Absolute path of the SQLite database file. */
    public final String dbPath;
    public final DatabaseManager database;
    public final CategoryRepository categories;
    public final TransactionRepository transactions;
    public final BudgetRepository budgets;
    public final SettingsRepository settingsStore;
    public final UserRepository users;
    public final MonthlyBudgetRepository monthlyBudgets;
    public final DataChangeBus bus = new DataChangeBus();

    public final CategoryService categoryService;
    public final TransactionService transactionService;
    public final BudgetService budgetService;
    public final MonthlyBudgetService monthlyBudgetService;
    public final ReportService reportService;
    public final SettingsService settingsService;
    public final AuthService authService;

    private AppSettings settings;
    private User currentUser;

    public AppContext(String dbPath) {
        this.dbPath = dbPath;
        this.database = new DatabaseManager(dbPath);
        this.categories = new CategoryRepository(database);
        this.transactions = new TransactionRepository(database);
        this.budgets = new BudgetRepository(database);
        this.settingsStore = new SettingsRepository(database);
        this.users = new UserRepository(database);
        this.monthlyBudgets = new MonthlyBudgetRepository(database);

        this.categoryService = new CategoryService(database, categories, transactions, budgets);
        this.transactionService = new TransactionService(transactions, categories);
        this.budgetService = new BudgetService(budgets, categories, transactions);
        this.monthlyBudgetService = new MonthlyBudgetService(
                monthlyBudgets, categories, transactions, database);
        this.reportService = new ReportService(transactions, categories, budgetService);
        this.settingsService = new SettingsService(settingsStore);
        this.authService = new AuthService(database, users, categoryService);

        // Default categories are seeded per account in AuthService.register().
        this.settings = settingsService.load();
    }

    /** Signed-in account of this session (null before login / after logout). */
    public User currentUser() {
        return currentUser;
    }

    public void signIn(User user) {
        this.currentUser = user;
    }

    public void signOut() {
        this.currentUser = null;
    }

    /** Current cached settings snapshot (reloaded when settings are saved). */
    public AppSettings settings() {
        return settings;
    }

    /** Persists the given settings and updates the cached snapshot. */
    public void saveSettings(AppSettings updated) {
        settingsService.save(updated);
        this.settings = updated;
    }

    /** Default database location: %USERPROFILE%\SpendWise\spendwise.db */
    public static String defaultDatabasePath() {
        return Path.of(System.getProperty("user.home"), "SpendWise", "spendwise.db").toString();
    }
}
