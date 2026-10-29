package com.spendwise.repository;

import com.spendwise.app.AppContext;
import com.spendwise.model.TransactionType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistence across close/reopen cycles + schema migrations. */
class PersistenceTest {

    @TempDir
    Path tempDir;

    private final List<AppContext> contexts = new ArrayList<>();

    @AfterEach
    void closeDatabases() {
        for (AppContext context : contexts) {
            context.database.close();
        }
    }

    private AppContext newContext(String file) {
        AppContext context = new AppContext(tempDir.resolve(file).toString());
        contexts.add(context);
        return context;
    }

    @Test
    void dataSurvivesReopenAndReinitDoesNotWipe() {
        AppContext first = newContext("persist.db");
        first.authService.register("persistence.user", "mat_khau_123", "");
        long owner = first.users.findFirst().id();
        long category = first.categoryService.add(owner, "Lương giữ lại",
                TransactionType.INCOME, "#15803D").id();
        long savedId = first.transactionService.add(owner, TransactionType.INCOME, 12_000_000,
                category, java.time.LocalDate.of(2026, 4, 1), "lương tháng 4").id();
        first.database.close();

        AppContext second = newContext("persist.db");
        var reloaded = second.transactionService.requireExisting(owner, savedId);
        assertEquals("lương tháng 4", reloaded.note());
        assertEquals(12_000_000, reloaded.amount());

        long categoriesBefore = second.categories.countAll(owner);
        second.categoryService.seedDefaultsForOwner(owner);
        assertEquals(categoriesBefore, second.categories.countAll(owner),
                "re-init must never reseed or wipe existing data");
        second.database.close();
        assertTrue(Files.exists(tempDir.resolve("persist.db")));
    }

    private Path db() {
        return tempDir.resolve("persist.db");
    }

    @Test
    void databaseDirectoryIsCreatedRecursively() {
        AppContext ctx = new AppContext(
                tempDir.resolve("library").resolve("spendwise").resolve("data.db").toString());
        contexts.add(ctx);
        ctx.authService.register("nested.user", "mat_khau_123", "");
        assertEquals(12, ctx.categories.countAll(ctx.users.findFirst().id()));
        assertTrue(Files.exists(tempDir.resolve("library").resolve("spendwise").resolve("data.db")));
    }

    @Test
    void schemaMigrationFromTrueV2Database() throws Exception {
        // Dựng đúng DB legacy v2 (chưa có owner_id, categories UNIQUE toàn cục).
        Path legacy = tempDir.resolve("upgrade.db");
        try (var raw = java.sql.DriverManager.getConnection("jdbc:sqlite:" + legacy)) {
            try (var st = raw.createStatement()) {
                st.execute("""
                        CREATE TABLE categories (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            type TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
                            color TEXT NOT NULL DEFAULT '#0F766E',
                            created_at TEXT NOT NULL,
                            UNIQUE (name, type)
                        )
                        """);
                st.execute("""
                        CREATE TABLE transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            type TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
                            amount INTEGER NOT NULL CHECK (amount > 0),
                            category_id INTEGER NOT NULL REFERENCES categories(id),
                            date TEXT NOT NULL,
                            note TEXT NOT NULL DEFAULT '',
                            created_at TEXT NOT NULL,
                            updated_at TEXT NOT NULL,
                            UNIQUE (category_id, date, amount, note)
                        )
                        """);
                st.execute("""
                        CREATE TABLE budgets (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            category_id INTEGER NOT NULL REFERENCES categories(id),
                            amount INTEGER NOT NULL CHECK (amount > 0),
                            start_date TEXT NOT NULL,
                            end_date TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            UNIQUE (category_id, start_date, end_date)
                        )
                        """);
                st.execute("""
                        CREATE TABLE settings (
                            key TEXT PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                        """);
                st.execute("""
                        CREATE TABLE users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                            password_hash TEXT NOT NULL,
                            display_name TEXT NOT NULL DEFAULT '',
                            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                        )
                        """);
                st.execute("""
                        INSERT INTO categories (name, type, color, created_at)
                        VALUES ('Ăn uống cũ', 'EXPENSE', '#F59E0B', '2025-01-01T00:00:00')
                        """);
                st.execute("""
                        INSERT INTO transactions (type, amount, category_id, date, note,
                                                   created_at, updated_at)
                        VALUES ('EXPENSE', 50_000, 1, '2025-06-01', 'hóa đơn cũ',
                                '2025-06-01T08:00:00', '2025-06-01T08:00:00')
                        """);
                st.execute("PRAGMA user_version = 2");
            }
        }

        // Mở bằng AppContext → migration v3/v4 gán owner + rebuild UNIQUE theo owner.
        AppContext upgraded = newContext(legacy.getFileName().toString());
        assertEquals(0, upgraded.users.countAll());
        assertEquals(0, upgraded.categories.countAll(999_999)); // row cũ chưa thuộc ai
        assertTrue(upgraded.database.rowExists(
                "SELECT 1 FROM categories WHERE name = 'Ăn uống cũ' AND owner_id IS NULL"),
                "legacy category survives the rebuild");
        assertNull(upgraded.categories.findById(42, 1)); // user khác không thấy

        upgraded.authService.register("tai_khoan_moi", "mat_khau_456", "Mới");
        long owner = upgraded.users.findByUsername("tai_khoan_moi").id();
        assertTrue(upgraded.users.existsUsername("tai_khoan_moi"));

        // giao dịch cũ vẫn còn nguyên, mồ côi owner chờ được nhận khi đăng nhập
        assertTrue(upgraded.database.rowExists(
                "SELECT 1 FROM transactions WHERE id = 1 AND owner_id IS NULL"));

        // tài khoản thứ hai: trùng tên danh mục mặc định (Lương...) là hợp lệ
        upgraded.authService.register("tai_khoan_hai", "mat_khau_789", "");
        long owner2 = upgraded.users.findByUsername("tai_khoan_hai").id();
        assertEquals(12, upgraded.categories.countAll(owner2));
        assertEquals(12, upgraded.categories.countAll(owner));
        assertEquals(0, upgraded.categories.countAll(999_999));

        // đăng nhập owner → nhận dữ liệu mồ côi về tài khoản của mình
        upgraded.authService.login("tai_khoan_moi", "mat_khau_456");
        assertTrue(upgraded.database.rowExists(
                "SELECT 1 FROM transactions WHERE id = 1 AND owner_id = " + owner));

        upgraded.database.close();
        AppContext reopened = newContext(legacy.getFileName().toString());
        assertTrue(reopened.users.existsUsername("tai_khoan_moi"));
        assertEquals(2, reopened.users.countAll());
        reopened.database.close();
    }
}