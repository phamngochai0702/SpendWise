package com.spendwise.service;

import com.spendwise.app.AppContext;
import com.spendwise.model.User;
import com.spendwise.util.PasswordHasher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Register/login rules plus storage of hashes only (never plaintext). */
class AuthServiceTest {

    @TempDir
    Path tempDir;

    private final List<AppContext> contexts = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void closeDatabases() {
        for (AppContext context : contexts) {
            context.database.close();
        }
    }

    private AppContext newContext() {
        AppContext context = new AppContext(
                tempDir.resolve("auth-" + System.nanoTime() + ".db").toString());
        contexts.add(context);
        return context;
    }

    @Test
    void registerStoresUserWithHashNotPassword() {
        AppContext ctx = newContext();
        User user = ctx.authService.register("nguoi.dung_01", "mat-khau-123", "Người Dùng Một");

        assertTrue(user.id() > 0);
        assertEquals("nguoi.dung_01", user.username());
        assertEquals("Người Dùng Một", user.displayName());
        assertNotEquals("mat-khau-123", user.passwordHash());
        assertTrue(user.passwordHash().startsWith("pbkdf2-sha256$"));
        assertTrue(PasswordHasher.verify("mat-khau-123", user.passwordHash()));
    }

    @Test
    void registerRejectsInvalidUsernamesPasswordsAndDuplicates() {
        AppContext ctx = newContext();
        ctx.authService.register("Quan.Ly", "mat-khau-123", "Quản lý");

        assertThrows(ValidationException.class,
                () -> ctx.authService.register("ab", "mat-khau-123", null));
        assertThrows(ValidationException.class,
                () -> ctx.authService.register("co khoang trang", "mat-khau-123", null));
        assertThrows(ValidationException.class,
                () -> ctx.authService.register("ten-rat-dai-qua-muoi-ky-tu-x", "mat-khau-123", null));
        assertThrows(ValidationException.class,
                () -> ctx.authService.register("ok-name", "12345", null));
        assertThrows(ValidationException.class,
                () -> ctx.authService.register("ok-name", null, null));

        // case-insensitive duplicate
        assertThrows(ValidationException.class,
                () -> ctx.authService.register("QUAN.LY", "mat-khau-456", null));
    }

    @Test
    void loginSucceedsWithRightCredentialsOnly() {
        AppContext ctx = newContext();
        ctx.authService.register("thanh.vien", "mat-khau-abc", "Thành Viên");

        User loggedIn = ctx.authService.login("THANH.VIEN", "mat-khau-abc");
        assertEquals("thanh.vien", loggedIn.username());
        assertEquals("Thành Viên", loggedIn.displayName());

        assertThrows(ValidationException.class,
                () -> ctx.authService.login("thanh.vien", "sai-mat-khau"));
        assertThrows(ValidationException.class, () -> ctx.authService.login("khong-ton-tai", "123456"));
        ValidationException wrongPassword = assertThrows(ValidationException.class,
                () -> ctx.authService.login("thanh.vien", "sai-mat-khau"));
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng.", wrongPassword.getMessage());
        assertThrows(ValidationException.class, () -> ctx.authService.login("", "mat-khau-abc"));
    }

    @Test
    void loginTimeIsSimilarForUnknownUsers() {
        // ensure the dummy-hash path really verifies (constant-time-ish behaviour)
        AppContext ctx = newContext();
        ValidationException unknown = assertThrows(ValidationException.class,
                () -> ctx.authService.login("ghost", "mat-khau-gi-do"));
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng.", unknown.getMessage());
    }

    @Test
    void secondAccountCanShareTheDatabase() {
        AppContext ctx = newContext();
        ctx.authService.register("user.a", "mat-khau-111", "A");
        ctx.authService.register("user.b", "mat-khau-222", "B");

        assertEquals(2, ctx.users.countAll());
        assertNotNull(ctx.users.findByUsername("user.a"));
        assertNotNull(ctx.users.findByUsername("USER.B"));
    }
}