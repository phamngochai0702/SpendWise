package com.spendwise.repository;

import com.spendwise.model.Budget;
import com.spendwise.util.DateUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** JDBC data access for legacy range budgets; scoped per owning user. */
public final class BudgetRepository {

    private static final String BASE_SELECT = """
            SELECT b.id, b.owner_id, b.category_id, c.name AS category_name,
                   c.color AS category_color, b.amount, b.start_date, b.end_date, b.created_at
            FROM budgets b
            JOIN categories c ON c.id = b.category_id
            """;

    private final DatabaseManager database;

    public BudgetRepository(DatabaseManager database) {
        this.database = database;
    }

    public Budget insert(long ownerId, long categoryId, long amount,
                         LocalDate start, LocalDate end) {
        long id = database.insertWithId("""
                        INSERT INTO budgets (owner_id, category_id, amount, start_date, end_date, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                ownerId, categoryId, amount, start, end, LocalDateTime.now());
        return findById(ownerId, id);
    }

    public Budget update(long ownerId, Budget budget) {
        database.update("""
                        UPDATE budgets
                        SET category_id = ?, amount = ?, start_date = ?, end_date = ?
                        WHERE id = ? AND owner_id = ?
                        """,
                budget.categoryId(), budget.limitAmount(), budget.start(), budget.end(),
                budget.id(), ownerId);
        return findById(ownerId, budget.id());
    }

    public boolean deleteById(long ownerId, long id) {
        return database.update("DELETE FROM budgets WHERE id = ? AND owner_id = ?",
                id, ownerId) > 0;
    }

    /** Used when the owning category is removed; always confirmed by the user first. */
    public long deleteByCategory(long ownerId, long categoryId) {
        return database.update(
                "DELETE FROM budgets WHERE category_id = ? AND owner_id = ?", categoryId, ownerId);
    }

    public Budget findById(long ownerId, long id) {
        return database.queryOne(BASE_SELECT + " WHERE b.id = ? AND b.owner_id = ?",
                BudgetRepository::mapRow, id, ownerId);
    }

    public List<Budget> findAll(long ownerId) {
        return database.query(BASE_SELECT + " WHERE b.owner_id = ? ORDER BY b.start_date DESC, b.id DESC",
                BudgetRepository::mapRow, ownerId);
    }

    public long countAll(long ownerId) {
        return database.queryLong("SELECT COUNT(*) FROM budgets WHERE owner_id = ?", ownerId);
    }

    public long countByCategory(long ownerId, long categoryId) {
        return database.queryLong(
                "SELECT COUNT(*) FROM budgets WHERE owner_id = ? AND category_id = ?",
                ownerId, categoryId);
    }

    /** Existing budgets of the category overlapping [start, end], excluding one id. */
    public long countOverlapping(long ownerId, long categoryId, LocalDate start,
                                 LocalDate end, long excludeId) {
        return database.queryLong("""
                        SELECT COUNT(*) FROM budgets
                        WHERE owner_id = ? AND category_id = ? AND id <> ?
                          AND start_date <= ? AND end_date >= ?
                        """,
                ownerId, categoryId, excludeId, end, start);
    }

    private static Budget mapRow(ResultSet rs) throws SQLException {
        return new Budget(
                rs.getLong("id"),
                rs.getLong("owner_id"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("category_color"),
                rs.getLong("amount"),
                DateUtil.parseIsoDate(rs.getString("start_date")),
                DateUtil.parseIsoDate(rs.getString("end_date")),
                DateUtil.parseIsoDateTime(rs.getString("created_at")));
    }
}