package com.spendwise.model;

/** Planned amount for one category inside a monthly budget. */
public final class MonthlyBudgetItem {

    private final long categoryId;
    private final String categoryName;
    private final String categoryColor;
    private final long amount;

    public MonthlyBudgetItem(long categoryId, String categoryName, String categoryColor, long amount) {
        this.categoryId = categoryId;
        this.categoryName = categoryName == null ? "" : categoryName;
        this.categoryColor = categoryColor == null ? "#0F766E" : categoryColor;
        this.amount = amount;
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

    public long amount() {
        return amount;
    }
}