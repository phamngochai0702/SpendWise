package com.spendwise.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (built into the JDK - no external
 * crypto library). Storage format:
 *
 * <pre>pbkdf2-sha256$iterations$base64(salt)$base64(hash)</pre>
 *
 * Every hash uses a fresh random salt; verification compares in constant time.
 * Plaintext passwords are never stored anywhere.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private PasswordHasher() {
    }

    /** Hashes a password; the result embeds algorithm, iterations and salt. */
    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Mật khẩu không được rỗng.");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** Constant-time verification against a stored {@link #hash(String)} string. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !PREFIX.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception malformed) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, HASH_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM)
                        .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception cryptoFailure) {
            throw new IllegalStateException("Không thể tạo mã hoá mật khẩu.", cryptoFailure);
        }
    }

    /** UTF-8 note: stored strings are pure ASCII base64, safe for SQLite TEXT. */
    static byte[] toBytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}