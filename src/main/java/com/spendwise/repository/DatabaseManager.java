package com.spendwise.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the single SQLite JDBC connection and the schema lifecycle
 * (creation + versioned migrations). All queries of the data access layer run
 * through its prepared-statement helpers.
 */
public final class DatabaseManager {

    /** Bump when the schema evolves and add a migration step in {@link #migrate()}. */
    private static final int SCHEMA_VERSION = 4;

    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Maps one result-set row into a domain object. */
    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private final String dbPath;
    private Connection connection;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
        initialize();
    }

    public String dbPath() {
        return dbPath;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private void initialize() {
        try {
            Path parent = Path.of(dbPath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=4000");
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
            }
            migrate();
        } catch (Exception ex) {
            connection = null;
            throw new DataAccessException(
                    "Không thể mở cơ sở dữ liệu tại: " + dbPath
                            + "\n(Hỏi quản trị viên xem thư mục có bị khoá, hoặc chọn vị trí khác qua -Dspendwise.db.path)",
                    ex);
        }
    }

    /** Applies schema migrations step by step; never rebuilds existing data. */
    private void migrate() {
        int version = userVersion();
        if (version < 1) {
            inTransaction(() -> {
                for (String ddl : schemaV1()) {
                    exec(ddl);
                }
            });
        }
        if (version < 2) {
            // v2: local user accounts for register/login
            inTransaction(() -> {
                for (String ddl : schemaV2()) {
                    exec(ddl);
                }
            });
        }
        if (version < 3) {
            // v3: per-user data ownership + monthly budgets.
            // Column adds are guarded so an old v3+ snapshot rolled back to v2
            // (or a partially upgraded file) can still migrate cleanly.
            inTransaction(() -> {
                for (String table : List.of("transactions", "budgets")) {
                    if (!hasColumn(table, "owner_id")) {
                        exec("ALTER TABLE " + table
                                + " ADD COLUMN owner_id INTEGER REFERENCES users(id)");
                    }
                }
                for (String ddl : schemaV3()) {
                    if (ddl.startsWith("ALTER")) {
                        continue; // handled with the hasColumn guard above
                    }
                    exec(ddl);
                }
            });
        }
        if (version < 4) {
            // v4: category names must be unique PER OWNER. v1 had a global
            // UNIQUE (name, type); rebuild the table with UNIQUE (owner_id, name, type).
            // Rebuild runs outside the transaction because SQLite forbids toggling
            // foreign_keys inside one, and the drop needs FKs off (child references).
            if (!hasIndex("idx_categories_owner")) {
                exec("PRAGMA foreign_keys=OFF");
                inTransaction(this::rebuildCategoriesForPerOwnerUniqueness);
                exec("PRAGMA foreign_keys=ON");
            }
        }

        // Future migrations:
        // if (version < 5) { inTransaction(() -> { ...v5 statements... }); }
        setUserVersion(SCHEMA_VERSION);
    }

    /** Copies every row into a fresh categories table scoped per owner. */
    private void rebuildCategoriesForPerOwnerUniqueness() {
        if (!hasColumn("categories", "owner_id")) {
            exec("ALTER TABLE categories ADD COLUMN owner_id INTEGER REFERENCES users(id)");
        }
        exec("""
                CREATE TABLE categories_rebuild (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_id  INTEGER REFERENCES users(id),
                    name      TEXT NOT NULL,
                    type      TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
                    color     TEXT NOT NULL DEFAULT '#0F766E',
                    created_at TEXT NOT NULL,
                    UNIQUE (owner_id, name, type)
                )
                """);
        exec("""
                INSERT INTO categories_rebuild (id, owner_id, name, type, color, created_at)
                SELECT id, owner_id, name, type, color, created_at FROM categories
                """);
        exec("DROP TABLE categories");
        exec("ALTER TABLE categories_rebuild RENAME TO categories");
        exec("CREATE INDEX IF NOT EXISTS idx_categories_owner ON categories(owner_id)");
    }

    /** TRUE when the named index exists (sqlite_master lookup). */
    private boolean hasIndex(String indexName) {
        boolean found = false;
        for (String name : query("SELECT name FROM sqlite_master WHERE type = 'index'",
                rs -> rs.getString("name"))) {
            if (indexName.equalsIgnoreCase(name)) {
                found = true;
                break;
            }
        }
        return found;
    }

    private List<String> schemaV3() {
        return List.of(
                // Ownership columns added to legacy tables; rows stay NULL until the
                // first user logs in (AuthService claims orphan rows at that point).
                // NOTE: the categories column is added in rebuildCategories... (v4).
                "ALTER TABLE transactions ADD COLUMN owner_id INTEGER REFERENCES users(id)",
                "ALTER TABLE budgets ADD COLUMN owner_id INTEGER REFERENCES users(id)",
                "CREATE INDEX IF NOT EXISTS idx_transactions_owner ON transactions(owner_id, date)",
                """
                CREATE TABLE IF NOT EXISTS monthly_budgets (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_id            INTEGER NOT NULL REFERENCES users(id),
                    year                INTEGER NOT NULL,
                    month               INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
                    average_income      INTEGER NOT NULL CHECK (average_income >= 0),
                    saving_rate_percent INTEGER NOT NULL CHECK (saving_rate_percent BETWEEN 0 AND 100),
                    savings_target      INTEGER NOT NULL CHECK (savings_target >= 0),
                    total_budget        INTEGER NOT NULL CHECK (total_budget >= 0),
                    created_at          TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                    updated_at          TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                    UNIQUE (owner_id, year, month)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS monthly_budget_items (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    budget_id   INTEGER NOT NULL REFERENCES monthly_budgets(id) ON DELETE CASCADE,
                    category_id INTEGER NOT NULL REFERENCES categories(id),
                    amount      INTEGER NOT NULL CHECK (amount >= 0),
                    UNIQUE (budget_id, category_id)
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_monthly_budgets_owner_period ON monthly_budgets(owner_id, year, month)"
        );
    }

    /** TRUE when {@code column} already exists on {@code table} (PRAGMA table_info). */
    private boolean hasColumn(String table, String column) {
        boolean found = false;
        for (String name : query("PRAGMA table_info(" + table + ")",
                rs -> rs.getString("name"))) {
            if (column.equalsIgnoreCase(name)) {
                found = true;
                break;
            }
        }
        return found;
    }

    private List<String> schemaV2() {
        return List.of("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    password_hash TEXT NOT NULL,
                    display_name  TEXT NOT NULL DEFAULT '',
                    created_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """);
    }

    private void exec(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new DataAccessException("Không thể khởi tạo cấu trúc cơ sở dữ liệu.", ex);
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            // nothing sensible to do on close failure
        } finally {
            connection = null;
        }
    }

    /** Size of the database file in bytes (for the Settings screen). */
    public long dbSizeBytes() {
        try {
            return Files.size(Path.of(dbPath));
        } catch (Exception ex) {
            return 0;
        }
    }

    private List<String> schemaV1() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT    NOT NULL,
                    type       TEXT    NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
                    color      TEXT    NOT NULL DEFAULT '#0F766E',
                    created_at TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """,
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_name_type ON categories(name, type)",
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    type        TEXT    NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
                    amount      INTEGER NOT NULL CHECK (amount > 0),
                    category_id INTEGER NOT NULL REFERENCES categories(id),
                    date        TEXT    NOT NULL,
                    note        TEXT    NOT NULL DEFAULT '',
                    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                    updated_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime'))
                )
                """,
                // No CASCADE on purpose: deleting a category that still has transactions
                // must be blocked so historical records are protected (services reassign first).
                "CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date)",
                "CREATE INDEX IF NOT EXISTS idx_transactions_category_date ON transactions(category_id, date)",
                "CREATE INDEX IF NOT EXISTS idx_transactions_type_date ON transactions(type, date)",
                """
                CREATE TABLE IF NOT EXISTS budgets (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                    amount      INTEGER NOT NULL CHECK (amount > 0),
                    start_date  TEXT    NOT NULL,
                    end_date    TEXT    NOT NULL,
                    created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                    CHECK (end_date >= start_date),
                    UNIQUE (category_id, start_date, end_date)
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_budgets_range ON budgets(start_date, end_date)",
                """
                CREATE TABLE IF NOT EXISTS settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
        );
    }

    private int userVersion() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Không thể đọc phiên bản cơ sở dữ liệu.", ex);
        }
    }

    private void setUserVersion(int version) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version=" + version);
        } catch (SQLException ex) {
            throw new DataAccessException("Không thể ghi phiên bản cơ sở dữ liệu.", ex);
        }
    }
    // ------------------------------------------------------------------
    // Prepared-statement data access
    // ------------------------------------------------------------------

    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw translate("Không thể truy vấn dữ liệu.", ex);
        }
    }

    public synchronized <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public synchronized int update(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw translate("Không thể cập nhật dữ liệu.", ex);
        }
    }

    /** Executes an INSERT and returns the generated primary key. */
    public synchronized long insertWithId(String sql, Object... params) {
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw translate("Không thể ghi dữ liệu.", ex);
        }
    }

    /** Returns the first column of the first row as long (0 when NULL/empty). */
    public synchronized long queryLong(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    Object value = rs.getObject(1);
                    return value instanceof Number number ? number.longValue() : 0L;
                }
                return 0L;
            }
        } catch (SQLException ex) {
            throw translate("Không thể tính toán dữ liệu.", ex);
        }
    }

    public synchronized boolean rowExists(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translate("Không thể truy vấn dữ liệu.", ex);
        }
    }

    /** Runs the given writes atomically; rolls back automatically on failure. */
    public synchronized void inTransaction(Runnable action) {
        try {
            connection.setAutoCommit(false);
            try {
                action.run();
                connection.commit();
            } catch (RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // rollback best-effort; original error is rethrown below
                }
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Không thể hoàn tất giao dịch dữ liệu.", ex);
        }
    }

    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            switch (value) {
                case null -> statement.setNull(i + 1, Types.NULL);
                case LocalDate date -> statement.setString(i + 1, date.toString());
                case LocalDateTime dateTime ->
                        statement.setString(i + 1, dateTime.format(DATETIME_FORMAT));
                case Boolean flag -> statement.setInt(i + 1, flag ? 1 : 0);
                case Integer number -> statement.setInt(i + 1, number);
                case Long number -> statement.setLong(i + 1, number);
                default -> statement.setString(i + 1, value.toString());
            }
        }
    }

    private static DataAccessException translate(String context, SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("unique")) {
            return new DataAccessException(context + " Dữ liệu đã tồn tại (trùng khoá).", ex);
        }
        if (message.contains("foreign key")) {
            return new DataAccessException(context
                    + " Dữ liệu đang được tham chiếu nên không thể thao tác.", ex);
        }
        if (message.contains("check")) {
            return new DataAccessException(context + " Giá trị không hợp lệ.", ex);
        }
        return new DataAccessException(context + " (" + ex.getMessage() + ")", ex);
    }
}