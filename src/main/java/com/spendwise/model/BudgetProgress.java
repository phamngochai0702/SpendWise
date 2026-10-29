package com.spendwise.model;

/** A budget joined with its real, computed spending figures. */
public final class BudgetProgress {

    private final Budget budget;
    private final long spent;
    private final long remaining;
    private final double usedRatio;
    private final BudgetStatus status;

    public BudgetProgress(Budget budget, long spent) {
        this.budget = budget;
        this.spent = spent;
        long limit = budget.limitAmount();
        this.remaining = Math.max(limit - spent, 0);
        this.usedRatio = limit <= 0 ? 0d : (double) spent / (double) limit;
        this.status = BudgetStatus.of(spent, limit);
    }

    public Budget budget() {
        return budget;
    }

    public long spent() {
        return spent;
    }

    public long remaining() {
        return remaining;
    }

    /** Spent / limit, may exceed 1.0 when the budget is exceeded. */
    public double usedRatio() {
        return usedRatio;
    }

    public BudgetStatus status() {
        return status;
    }
}
