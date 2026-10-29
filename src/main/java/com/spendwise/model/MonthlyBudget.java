package com.spendwise.model;

import java.time.YearMonth;

/**
 * One user's plan for a calendar month: expected income, saving rate and the
 * total spending budget derived from them. Category allocations live in
 * {@code monthly_budget_items} (see MonthlyBudgetItem).
 */
public final class MonthlyBudget {

    private final long id;
    private final long ownerId;
    private final YearMonth month;
    private final long averageIncome;
    private final int savingRatePercent;
    private final long savingsTarget;
    private final long totalBudget;

    public MonthlyBudget(long id, long ownerId, YearMonth month, long averageIncome,
                         int savingRatePercent, long savingsTarget, long totalBudget) {
        this.id = id;
        this.ownerId = ownerId;
        this.month = month;
        this.averageIncome = averageIncome;
        this.savingRatePercent = savingRatePercent;
        this.savingsTarget = savingsTarget;
        this.totalBudget = totalBudget;
    }

    public long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
    }

    public YearMonth month() {
        return month;
    }

    public long averageIncome() {
        return averageIncome;
    }

    public int savingRatePercent() {
        return savingRatePercent;
    }

    public long savingsTarget() {
        return savingsTarget;
    }

    /** Spending budget = average income - savings target. */
    public long totalBudget() {
        return totalBudget;
    }
}