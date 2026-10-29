package com.spendwise.model;

/** Summary of all transactions inside one date range (report tab "Tổng quan"). */
public final class RangeSummary {

    private final long transactionCount;
    private final long incomeTotal;
    private final long expenseTotal;
    private final long largestExpense;
    private final long largestIncome;
    private final long averageExpense;

    public RangeSummary(long transactionCount, long incomeTotal, long expenseTotal,
                        long largestExpense, long largestIncome, long averageExpense) {
        this.transactionCount = transactionCount;
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
        this.largestExpense = largestExpense;
        this.largestIncome = largestIncome;
        this.averageExpense = averageExpense;
    }

    public long transactionCount() {
        return transactionCount;
    }

    public long incomeTotal() {
        return incomeTotal;
    }

    public long expenseTotal() {
        return expenseTotal;
    }

    public long largestExpense() {
        return largestExpense;
    }

    public long largestIncome() {
        return largestIncome;
    }

    public long averageExpense() {
        return averageExpense;
    }

    /** (income - expense). May be negative. */
    public long net() {
        return incomeTotal - expenseTotal;
    }
}
