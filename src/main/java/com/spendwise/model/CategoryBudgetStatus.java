package com.spendwise.model;

/** One category's budgeted vs real spending inside a month. */
public final class CategoryBudgetStatus {

    private final long categoryId;
    private final String name;
    private final String color;
    private final long allocated;
    private final long spent;
    private final long remaining;

    public CategoryBudgetStatus(long categoryId, String name, String color,
                                long allocated, long spent) {
        this.categoryId = categoryId;
        this.name = name;
        this.color = color;
        this.allocated = allocated;
        this.spent = spent;
        this.remaining = allocated - spent;
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

    public long allocated() {
        return allocated;
    }

    public long spent() {
        return spent;
    }

    /** allocated - spent; negative when over budget. */
    public long remaining() {
        return remaining;
    }

    /** spent/allocated; 0 when allocated is 0 (division-by-zero guard). */
    public double usedRatio() {
        return allocated <= 0 ? 0d : (double) spent / (double) allocated;
    }

    public BudgetStatus status() {
        return BudgetStatus.of(spent, allocated);
    }
}