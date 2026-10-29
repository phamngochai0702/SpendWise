package com.spendwise.model;

import java.time.LocalDate;

/**
 * Discriminates transaction search/filter queries. Every field is optional
 * (null = ignore) so the repository can build the matching SQL dynamically.
 */
public final class TransactionFilter {

    public enum Sort {
        DATE_DESC("Ngày mới nhất"),
        DATE_ASC("Ngày cũ nhất"),
        AMOUNT_DESC("Số tiền giảm dần"),
        AMOUNT_ASC("Số tiền tăng dần"),
        CATEGORY("Danh mục (A-Z)");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private TransactionType type;
    private Long categoryId;
    private LocalDate from;
    private LocalDate to;
    private Long minAmount;
    private Long maxAmount;
    private String keyword;
    private Sort sort = Sort.DATE_DESC;

    public TransactionFilter type(TransactionType type) {
        this.type = type;
        return this;
    }

    public TransactionFilter categoryId(Long categoryId) {
        this.categoryId = categoryId;
        return this;
    }

    public TransactionFilter between(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
        return this;
    }

    public TransactionFilter amountRange(Long minAmount, Long maxAmount) {
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        return this;
    }

    public TransactionFilter keyword(String keyword) {
        this.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return this;
    }

    public TransactionFilter sort(Sort sort) {
        this.sort = sort == null ? Sort.DATE_DESC : sort;
        return this;
    }

    public TransactionType type() {
        return type;
    }

    public Long categoryId() {
        return categoryId;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public Long minAmount() {
        return minAmount;
    }

    public Long maxAmount() {
        return maxAmount;
    }

    public String keyword() {
        return keyword;
    }

    public Sort sort() {
        return sort;
    }
}
