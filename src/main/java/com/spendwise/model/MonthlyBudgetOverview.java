package com.spendwise.model;

import java.util.List;

/** A saved monthly budget joined with its real spending figures. */
public final class MonthlyBudgetOverview {

    private final MonthlyBudget budget;
    private final List<CategoryBudgetStatus> statuses;
    private final long totalSpent;
    private final long remaining;
    private final BudgetStatus overallStatus;

    public MonthlyBudgetOverview(MonthlyBudget budget, List<CategoryBudgetStatus> statuses) {
        this.budget = budget;
        this.statuses = statuses;
        long spent = 0;
        for (CategoryBudgetStatus status : statuses) {
            spent += status.spent();
        }
        this.totalSpent = spent;
        this.remaining = budget.totalBudget() - spent;
        this.overallStatus = BudgetStatus.of(spent, budget.totalBudget());
    }

    public MonthlyBudget budget() {
        return budget;
    }

    public List<CategoryBudgetStatus> statuses() {
        return statuses;
    }

    public long totalSpent() {
        return totalSpent;
    }

    /** total budget - total spent; negative when exceeded. */
    public long remaining() {
        return remaining;
    }

    public BudgetStatus overallStatus() {
        return overallStatus;
    }
}