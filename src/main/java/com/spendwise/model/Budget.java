package com.spendwise.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A spending budget: an expense category plus a date range and a limit. */
public final class Budget {

    private final long id;
    private final long ownerId;
    private final long categoryId;
    private final String categoryName;
    private final String categoryColor;
    private final long limitAmount;
    private final LocalDate start;
    private final LocalDate end;
    private final LocalDateTime createdAt;

    public Budget(long id, long ownerId, long categoryId, String categoryName, String categoryColor,
                  long limitAmount, LocalDate start, LocalDate end, LocalDateTime createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.categoryId = categoryId;
        this.categoryName = categoryName == null ? "" : categoryName;
        this.categoryColor = categoryColor;
        this.limitAmount = limitAmount;
        this.start = start;
        this.end = end;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
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

    public long limitAmount() {
        return limitAmount;
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return end;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    /** True when the given day falls inside this budget's inclusive date range. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** True when the two ranges overlap (inclusive on both ends). */
    public boolean overlapsRange(LocalDate otherStart, LocalDate otherEnd) {
        return !otherStart.isAfter(end) && !otherEnd.isBefore(start);
    }
}
