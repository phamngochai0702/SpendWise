package com.spendwise.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Average income computed from recent completed months. Months WITHOUT any
 * income transaction are excluded (they are NOT counted as 0), per the
 * business rule — that's why we carry the month list for the UI to explain.
 */
public final class RecentIncomeSummary {

    private final LocalDate from;
    private final LocalDate to;
    private final int monthsWithData;
    private final long totalIncome;
    private final long averageIncome;
    private final List<TimePoint> months;

    public RecentIncomeSummary(LocalDate from, LocalDate to, int monthsWithData,
                               long totalIncome, long averageIncome, List<TimePoint> months) {
        this.from = from;
        this.to = to;
        this.monthsWithData = monthsWithData;
        this.totalIncome = totalIncome;
        this.averageIncome = averageIncome;
        this.months = months;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    /** Months (of the 6 examined) that had at least one income transaction. */
    public int monthsWithData() {
        return monthsWithData;
    }

    public long totalIncome() {
        return totalIncome;
    }

    /** totalIncome / monthsWithData; 0 when no data at all. */
    public long averageIncome() {
        return averageIncome;
    }

    /** Per-month income inside the examined range (0 for empty months). */
    public List<TimePoint> months() {
        return months;
    }
}