package com.spendwise.service;

import com.spendwise.model.User;
import com.spendwise.repository.DatabaseManager;
import com.spendwise.repository.UserRepository;
import com.spendwise.util.PasswordHasher;

import java.util.regex.Pattern;

/**
 * Register/login with real credential validation; passwords are stored only as
 * PBKDF2 hashes (see {@link PasswordHasher}). Case-insensitive usernames.
 *
 * Data ownership: registering seeds default categories for the new user; when
 * a pre-v3 (legacy) database still has unowned rows, the first user to log in
 * claims them, so no historical data is lost.
 */
public final class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_.]{3,30}");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 128;

    /** Precomputed hash so unknown usernames cost the same time as known ones. */
    private static final String DUMMY_HASH = PasswordHasher.hash("spendwise-timing-equalizer");

    private final DatabaseManager database;
    private final UserRepository users;
    private final CategoryService categoryService;

    public AuthService(DatabaseManager database, UserRepository users,
                       CategoryService categoryService) {
        this.database = database;
        this.users = users;
        this.categoryService = categoryService;
    }

    /** Creates an account (+ default categories) and returns it for sign-in. */
    public User register(String username, String password, String displayName) {
        String cleanUsername = validateUsername(username);
        validatePassword(password);
        String cleanDisplayName = displayName == null ? "" : displayName.trim();
        if (cleanDisplayName.length() > 60) {
            throw new ValidationException("Tên hiển thị tối đa 60 ký tự.");
        }
        if (users.existsUsername(cleanUsername)) {
            throw new ValidationException("Tên đăng nhập '" + cleanUsername + "' đã tồn tại.");
        }
        long id = users.insert(cleanUsername, PasswordHasher.hash(password), cleanDisplayName);
        categoryService.seedDefaultsForOwner(id);
        return users.findByUsername(cleanUsername);
    }

    /** Verifies credentials; both wrong password and unknown user give one message. */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ValidationException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        }
        User user = users.findByUsername(username.trim());
        String stored = user == null ? DUMMY_HASH : user.passwordHash();
        boolean passwordOk = PasswordHasher.verify(password, stored);
        if (user == null || !passwordOk) {
            throw new ValidationException("Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        claimLegacyUnownedData(user.id());
        return user;
    }

    /** Migrates pre-v3 rows (owner NULL) to the first user that logs in. */
    private void claimLegacyUnownedData(long ownerId) {
        boolean hasOrphans =
                database.rowExists("SELECT 1 FROM transactions WHERE owner_id IS NULL LIMIT 1")
                        || database.rowExists("SELECT 1 FROM categories WHERE owner_id IS NULL LIMIT 1")
                        || database.rowExists("SELECT 1 FROM budgets WHERE owner_id IS NULL LIMIT 1");
        if (!hasOrphans) {
            return;
        }
        database.inTransaction(() -> {
            database.update("UPDATE transactions SET owner_id = ? WHERE owner_id IS NULL", ownerId);
            database.update("UPDATE categories SET owner_id = ? WHERE owner_id IS NULL", ownerId);
            database.update("UPDATE budgets SET owner_id = ? WHERE owner_id IS NULL", ownerId);
        });
    }

    private static String validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Vui lòng nhập tên đăng nhập.");
        }
        String clean = username.trim();
        if (!USERNAME_PATTERN.matcher(clean).matches()) {
            throw new ValidationException("Tên đăng nhập gồm 3-30 ký tự chữ, số, dấu chấm "
                    + "hoặc dấu gạch dưới.");
        }
        return clean;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Mật khẩu phải có ít nhất "
                    + MIN_PASSWORD_LENGTH + " ký tự.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("Mật khẩu tối đa " + MAX_PASSWORD_LENGTH + " ký tự.");
        }
    }
}