package com.spendwise.repository;

import com.spendwise.model.User;
import com.spendwise.util.DateUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** JDBC data access for the {@code users} table (password hashes only). */
public final class UserRepository {

    private static final String SELECT_COLUMNS =
            "id, username, display_name, password_hash, created_at";

    private final DatabaseManager database;

    public UserRepository(DatabaseManager database) {
        this.database = database;
    }

    public long insert(String username, String passwordHash, String displayName) {
        return database.insertWithId("""
                        INSERT INTO users (username, password_hash, display_name, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                username, passwordHash, displayName == null ? "" : displayName,
                LocalDateTime.now());
    }

    /** Case-insensitive username lookup; null when unknown. */
    public User findByUsername(String username) {
        return database.queryOne(
                "SELECT " + SELECT_COLUMNS + " FROM users WHERE username = ? COLLATE NOCASE",
                UserRepository::mapRow,
                username);
    }

    public boolean existsUsername(String username) {
        return database.rowExists(
                "SELECT 1 FROM users WHERE username = ? COLLATE NOCASE", username);
    }

    /** First registered user; legacy-headless smoke fallback. */
    public User findFirst() {
        return database.queryOne(
                "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY id",
                UserRepository::mapRow);
    }

    public long countAll() {
        return database.queryLong("SELECT COUNT(*) FROM users");
    }

    private static User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                DateUtil.parseIsoDateTime(rs.getString("created_at")));
    }
}