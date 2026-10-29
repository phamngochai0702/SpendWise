package com.spendwise.model;

/**
 * One data point of an "income vs expense" chart series. Depending on the report
 * granularity the key is a year ("2026"), a year-month ("2026-03") or an ISO
 * date ("2026-03-14"); {@code label} is the pre-formatted Vietnamese label.
 */
public final class TimePoint {

    private final String key;
    private final String label;
    private final long income;
    private final long expense;

    public TimePoint(String key, String label, long income, long expense) {
        this.key = key;
        this.label = label;
        this.income = income;
        this.expense = expense;
    }

    public TimePoint with(String label, long income, long expense) {
        return new TimePoint(key, label, income, expense);
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public long income() {
        return income;
    }

    public long expense() {
        return expense;
    }
}
