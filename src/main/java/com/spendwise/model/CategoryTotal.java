package com.spendwise.model;

/**
 * Aggregated totals for one category within a date range. Used by the category
 * table, the expense distribution chart and report tables. Categories without
 * transactions keep count = 0 and amount = 0.
 */
public final class CategoryTotal {

    private final long categoryId;
    private final String name;
    private final String color;
    private final TransactionType type;
    private final long transactionCount;
    private final long amount;

    public CategoryTotal(long categoryId, String name, String color, TransactionType type,
                         long transactionCount, long amount) {
        this.categoryId = categoryId;
        this.name = name;
        this.color = color;
        this.type = type;
        this.transactionCount = transactionCount;
        this.amount = amount;
    }

    public long categoryId() {
        return categoryId;
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }

    public TransactionType type() {
        return type;
    }

    public long transactionCount() {
        return transactionCount;
    }

    public long amount() {
        return amount;
    }
}
