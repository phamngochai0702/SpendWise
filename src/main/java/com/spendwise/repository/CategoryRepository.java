package com.spendwise.repository;

import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import com.spendwise.util.DateUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JDBC data access for the {@code categories} table. Every query is scoped to
 * the owning user ({@code owner_id}) — users never see each other's data.
 */
public final class CategoryRepository {

    private static final String SELECT_COLUMNS =
            "id, name, type, color, created_at";

    private final DatabaseManager database;

    public CategoryRepository(DatabaseManager database) {
        this.database = database;
    }

    public Category insert(long ownerId, String name, TransactionType type, String color) {
        long id = database.insertWithId(
                "INSERT INTO categories (owner_id, name, type, color, created_at) VALUES (?, ?, ?, ?, ?)",
                ownerId, name, type.dbValue(), color, LocalDateTime.now());
        return findById(ownerId, id);
    }

    public void update(long ownerId, Category category) {
        database.update("UPDATE categories SET name = ?, color = ? WHERE id = ? AND owner_id = ?",
                category.name(), category.color(), category.id(), ownerId);
    }

    /** Returns false when the row did not exist or belongs to another user. */
    public boolean deleteById(long ownerId, long id) {
        return database.update("DELETE FROM categories WHERE id = ? AND owner_id = ?", id, ownerId) > 0;
    }

    public Category findById(long ownerId, long id) {
        return database.queryOne(
                "SELECT " + SELECT_COLUMNS + " FROM categories WHERE id = ? AND owner_id = ?",
                CategoryRepository::mapRow, id, ownerId);
    }

    public Category findByNameAndType(long ownerId, String name, TransactionType type) {
        return database.queryOne(
                "SELECT " + SELECT_COLUMNS + " FROM categories "
                        + "WHERE lower(name) = ? AND type = ? AND owner_id = ?",
                CategoryRepository::mapRow, name.toLowerCase(), type.dbValue(), ownerId);
    }

    public List<Category> findAll(long ownerId) {
        return database.query(
                "SELECT " + SELECT_COLUMNS + " FROM categories WHERE owner_id = ? ORDER BY type, lower(name)",
                CategoryRepository::mapRow, ownerId);
    }

    public List<Category> findByType(long ownerId, TransactionType type) {
        return database.query(
                "SELECT " + SELECT_COLUMNS + " FROM categories WHERE owner_id = ? AND type = ? "
                        + "ORDER BY lower(name)",
                CategoryRepository::mapRow, ownerId, type.dbValue());
    }

    public long countAll(long ownerId) {
        return database.queryLong("SELECT COUNT(*) FROM categories WHERE owner_id = ?", ownerId);
    }

    private static Category mapRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                TransactionType.fromDb(rs.getString("type")),
                rs.getString("color"),
                DateUtil.parseIsoDateTime(rs.getString("created_at")));
    }
}
