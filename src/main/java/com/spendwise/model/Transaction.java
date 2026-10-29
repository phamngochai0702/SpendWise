package com.spendwise.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single income or expense record. Money is stored as a {@code long} amount
 * of whole VND units to avoid floating point rounding errors.
 */
public final class Transaction {

    private final long id;
    private final TransactionType type;
    private final long amount;
    private final long categoryId;
    private final String categoryName;
    private final String categoryColor;
    private final LocalDate date;
    private final String note;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Transaction(long id, TransactionType type, long amount, long categoryId,
                       String categoryName, String categoryColor, LocalDate date,
                       String note, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.categoryId = categoryId;
        this.categoryName = categoryName == null ? "" : categoryName;
        this.categoryColor = categoryColor;
        this.date = date;
        this.note = note == null ? "" : note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Convenience factory for a not-yet-persisted transaction. */
    public static Transaction draft(TransactionType type, long amount, long categoryId,
                                    LocalDate date, String note) {
        return new Transaction(0, type, amount, categoryId, "", null, date, note, null, null);
    }

    public long id() {
        return id;
    }

    public boolean isPersisted() {
        return id > 0;
    }

    public TransactionType type() {
        return type;
    }

    public long amount() {
        return amount;
    }

    public long categoryId() {
        return categoryId;
    }

    public String categoryName() {
        return categoryName;
    }

    public String categoryColor() {
        return categoryColor;
    }

    public LocalDate date() {
        return date;
    }

    public String note() {
        return note;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
