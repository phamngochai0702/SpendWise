package com.spendwise.model;

import java.time.LocalDateTime;

/** A local SpendWise account. Only the password HASH is kept, never the password. */
public final class User {

    private final long id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final LocalDateTime createdAt;

    public User(long id, String username, String displayName,
                String passwordHash, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName == null ? "" : displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    /** PBKDF2 storage string; intentionally NOT sensitive plaintext. */
    public String passwordHash() {
        return passwordHash;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}