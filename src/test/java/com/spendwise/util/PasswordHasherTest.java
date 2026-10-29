package com.spendwise.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Password hashing must be deterministic per verification yet salted per hash. */
class PasswordHasherTest {

    @Test
    void verifiesCorrectAndRejectsWrongPasswords() {
        String stored = PasswordHasher.hash("mat-khau-123");
        assertTrue(PasswordHasher.verify("mat-khau-123", stored));
        assertFalse(PasswordHasher.verify("mat-khau-124", stored));
        assertFalse(PasswordHasher.verify("", stored));
        assertFalse(PasswordHasher.verify(null, stored));
    }

    @Test
    void everyHashUsesFreshSalt() {
        String first = PasswordHasher.hash("cung-mot-mat-khau");
        String second = PasswordHasher.hash("cung-mot-mat-khau");
        assertNotEquals(first, second);
        assertTrue(PasswordHasher.verify("cung-mot-mat-khau", first));
        assertTrue(PasswordHasher.verify("cung-mot-mat-khau", second));
    }

    @Test
    void storedFormatIsAlgorithmIterationsSaltHash() {
        String stored = PasswordHasher.hash("abc123");
        String[] parts = stored.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals("120000", parts[1]);
    }

    @Test
    void malformedStoredHashFailsClosed() {
        assertFalse(PasswordHasher.verify("abc123", "khong-phai-hash"));
        assertFalse(PasswordHasher.verify("abc123", "pbkdf2-sha256$notanumber$aa$bb"));
        assertFalse(PasswordHasher.verify("abc123", "md5$1000$aa$bb"));
        assertFalse(PasswordHasher.verify("abc123", null));
    }
}