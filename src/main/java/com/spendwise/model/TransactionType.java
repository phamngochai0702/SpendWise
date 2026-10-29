package com.spendwise.model;

/** Direction of a transaction / purpose of a category. */
public enum TransactionType {

    INCOME,
    EXPENSE;

    /** Value stored in the SQLite database. */
    public String dbValue() {
        return name();
    }

    public static TransactionType fromDb(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + value);
        }
        return TransactionType.valueOf(value.trim().toUpperCase());
    }

    /** Full Vietnamese display name. */
    public String displayName() {
        return this == INCOME ? "Thu nhập" : "Chi tiêu";
    }

    /** Short label used in tables and badges. */
    public String shortName() {
        return this == INCOME ? "Thu" : "Chi";
    }
}
