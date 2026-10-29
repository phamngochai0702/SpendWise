package com.spendwise.model;

import java.time.LocalDate;
import java.util.List;

/** Everything the dashboard screen shows, computed from real database records. */
public final class DashboardSnapshot {

    private final LocalDate from;
    private final LocalDate to;
    private final long incomeTotal;
    private final long expenseTotal;
    private final long netAllTime;
    private final List<TimePoint> monthlySeries;
    private final List<CategoryTotal> expenseDistribution;
    private final List<BudgetProgress> budgetOverviews;
    private final List<Transaction> recentTransactions;

    public DashboardSnapshot(LocalDate from, LocalDate to, long incomeTotal, long expenseTotal,
                             long netAllTime, List<TimePoint> monthlySeries,
                             List<CategoryTotal> expenseDistribution,
                             List<BudgetProgress> budgetOverviews,
                             List<Transaction> recentTransactions) {
        this.from = from;
        this.to = to;
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
        this.netAllTime = netAllTime;
        this.monthlySeries = monthlySeries;
        this.expenseDistribution = expenseDistribution;
        this.budgetOverviews = budgetOverviews;
        this.recentTransactions = recentTransactions;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public long incomeTotal() {
        return incomeTotal;
    }

    public long expenseTotal() {
        return expenseTotal;
    }

    /** Net worth style balance: all-time income minus all-time expenses. */
    public long netAllTime() {
        return netAllTime;
    }

    public List<TimePoint> monthlySeries() {
        return monthlySeries;
    }

    public List<CategoryTotal> expenseDistribution() {
        return expenseDistribution;
    }

    public List<BudgetProgress> budgetOverviews() {
        return budgetOverviews;
    }

    public List<Transaction> recentTransactions() {
        return recentTransactions;
    }
}
