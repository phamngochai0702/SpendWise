package com.spendwise.repository;

import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.MonthlyBudgetItem;
import com.spendwise.util.DateUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * JDBC data access for monthly budgets ({@code monthly_budgets} + items).
 * UNIQUE (owner_id, year, month) guarantees one active budget per user/month;
 * the service still checks and upserts explicitly.
 */
public final class MonthlyBudgetRepository {

    private final DatabaseManager database;

    public MonthlyBudgetRepository(DatabaseManager database) {
        this.database = database;
    }

    public MonthlyBudget findByOwnerAndMonth(long ownerId, int year, int month) {
        return database.queryOne("""
                        SELECT id, owner_id, year, month, average_income, saving_rate_percent,
                               savings_target, total_budget
                        FROM monthly_budgets WHERE owner_id = ? AND year = ? AND month = ?
                        """,
                MonthlyBudgetRepository::mapRow, ownerId, year, month);
    }

    public long insert(long ownerId, int year, int month, long averageIncome,
                       int savingRatePercent, long savingsTarget, long totalBudget) {
        return database.insertWithId("""
                        INSERT INTO monthly_budgets
                            (owner_id, year, month, average_income, saving_rate_percent,
                             savings_target, total_budget, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ownerId, year, month, averageIncome, savingRatePercent, savingsTarget,
                totalBudget, LocalDateTime.now(), LocalDateTime.now());
    }

    /** Header update; item rows are replaced by the caller inside the same transaction. */
    public void update(long budgetId, long averageIncome, int savingRatePercent,
                       long savingsTarget, long totalBudget) {
        database.update("""
                        UPDATE monthly_budgets
                        SET average_income = ?, saving_rate_percent = ?, savings_target = ?,
                            total_budget = ?, updated_at = ?
                        WHERE id = ?
                        """,
                averageIncome, savingRatePercent, savingsTarget, totalBudget,
                LocalDateTime.now(), budgetId);
    }

    public void deleteItems(long budgetId) {
        database.update("DELETE FROM monthly_budget_items WHERE budget_id = ?", budgetId);
    }

    public void insertItem(long budgetId, long categoryId, long amount) {
        database.update(
                "INSERT INTO monthly_budget_items (budget_id, category_id, amount) VALUES (?, ?, ?)",
                budgetId, categoryId, amount);
    }

    /** Number of saved monthly budgets owned by a user (uniqueness checks in tests). */
    public long countByOwner(long ownerId) {
        return database.queryLong(
                "SELECT COUNT(*) FROM monthly_budgets WHERE owner_id = ?", ownerId);
    }

    /** Allocations joined with (current) category names and colors. */
    public List<MonthlyBudgetItem> listItems(long budgetId) {
        return database.query("""
                        SELECT mbi.category_id, mbi.amount, c.name AS category_name,
                               c.color AS category_color
                        FROM monthly_budget_items mbi
                        JOIN categories c ON c.id = mbi.category_id
                        WHERE mbi.budget_id = ?
                        ORDER BY mbi.amount DESC
                        """,
                rs -> new MonthlyBudgetItem(
                        rs.getLong("category_id"),
                        rs.getString("category_name"),
                        rs.getString("category_color"),
                        rs.getLong("amount")),
                budgetId);
    }

    private static MonthlyBudget mapRow(ResultSet rs) throws SQLException {
        YearMonth month = YearMonth.of(rs.getInt("year"), rs.getInt("month"));
        return new MonthlyBudget(
                rs.getLong("id"),
                rs.getLong("owner_id"),
                month,
                rs.getLong("average_income"),
                rs.getInt("saving_rate_percent"),
                rs.getLong("savings_target"),
                rs.getLong("total_budget"));
    }
}