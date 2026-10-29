package com.spendwise.model;

import java.time.LocalDate;

/** Named preset date ranges shared by the dashboard and the report screens. */
public enum PeriodKey {

    THIS_MONTH("Tháng này"),
    LAST_MONTH("Tháng trước"),
    LAST_3_MONTHS("3 tháng gần nhất"),
    THIS_YEAR("Năm nay"),
    ALL_TIME("Toàn bộ");

    private final String label;

    PeriodKey(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Inclusive range [from, to] of this period, seen from {@code today}. */
    public LocalDate[] range(LocalDate today) {
        return switch (this) {
            case THIS_MONTH -> new LocalDate[]{
                    today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())};
            case LAST_MONTH -> new LocalDate[]{
                    today.minusMonths(1).withDayOfMonth(1),
                    today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())};
            case LAST_3_MONTHS -> new LocalDate[]{
                    today.minusMonths(2).withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())};
            case THIS_YEAR -> new LocalDate[]{
                    LocalDate.of(today.getYear(), 1, 1),
                    LocalDate.of(today.getYear(), 12, 31)};
            case ALL_TIME -> new LocalDate[]{
                    LocalDate.of(1970, 1, 1),
                    LocalDate.of(2999, 12, 31)};
        };
    }
}
