package com.spendwise.model;

/** Usage state of a budget, derived from real expense records. */
public enum BudgetStatus {

    OK,
    NEAR,
    EXCEEDED;

    /**
     * "Approaching" threshold: 80% of the limit, evaluated with exact integer
     * math ({@code spent*5 >= limit*4}) — doubles misjudge the 80% boundary.
     */
    public static BudgetStatus of(long spent, long limit) {
        if (limit <= 0) {
            return spent > 0 ? EXCEEDED : OK;
        }
        if (spent >= limit) {
            return EXCEEDED; // 100% hoặc hơn
        }
        if (spent * 5 >= limit * 4) {
            return NEAR; // từ 80% đến dưới 100%
        }
        return OK;
    }

    public String label() {
        return switch (this) {
            case EXCEEDED -> "Vượt hạn mức";
            case NEAR -> "Sắp chạm hạn mức";
            case OK -> "Còn trong hạn mức";
        };
    }
}
