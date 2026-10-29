package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionFilter;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.TransactionRepository;
import com.spendwise.util.MoneyUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rules for income/expense records of one authenticated user.
 * Money is handled in whole VND units ({@code long}).
 */
public final class TransactionService {

    public static final int MAX_NOTE_LENGTH = 500;

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    public Transaction add(long ownerId, TransactionType type, long amount, long categoryId,
                           LocalDate date, String note) {
        validate(ownerId, type, amount, categoryId, date, note);
        return transactions.insert(ownerId,
                Transaction.draft(type, amount, categoryId, date, normalizedNote(note)));
    }

    public Transaction update(long ownerId, long id, TransactionType type, long amount,
                              long categoryId, LocalDate date, String note) {
        requireExisting(ownerId, id);
        validate(ownerId, type, amount, categoryId, date, note);
        Transaction existing = requireExisting(ownerId, id);
        Transaction edited = new Transaction(
                id, type, amount, categoryId,
                existing.categoryName(), existing.categoryColor(),
                date, normalizedNote(note), existing.createdAt(), LocalDateTime.now());
        return transactions.update(ownerId, edited);
    }

    public void delete(long ownerId, long id) {
        requireExisting(ownerId, id);
        transactions.deleteById(ownerId, id);
    }

    /** Also proves the row belongs to {@code ownerId} (404-style, no leak). */
    public Transaction requireExisting(long ownerId, long id) {
        Transaction transaction = transactions.findById(ownerId, id);
        if (transaction == null) {
            throw new ValidationException("Giao dịch không tồn tại hoặc đã bị xoá.");
        }
        return transaction;
    }

    public List<Transaction> search(long ownerId, TransactionFilter filter) {
        return transactions.search(ownerId, filter);
    }

    public List<Transaction> recent(long ownerId, int limit) {
        return transactions.recent(ownerId, limit);
    }

    public List<Transaction> byCategory(long ownerId, long categoryId) {
        return transactions.byCategory(ownerId, categoryId);
    }

    private void validate(long ownerId, TransactionType type, long amount, long categoryId,
                          LocalDate date, String note) {
        if (type == null) {
            throw new ValidationException("Vui lòng chọn loại giao dịch.");
        }
        if (amount <= 0) {
            throw new ValidationException("Số tiền phải lớn hơn 0.");
        }
        if (amount > MoneyUtil.MAX_AMOUNT) {
            throw new ValidationException("Số tiền vượt quá mức cho phép.");
        }
        Category category = categories.findById(ownerId, categoryId);
        if (category == null) {
            throw new ValidationException("Vui lòng chọn danh mục.");
        }
        if (category.type() != type) {
            throw new ValidationException("Danh mục '" + category.name()
                    + "' không thuộc loại giao dịch đã chọn.");
        }
        if (date == null) {
            throw new ValidationException("Ngày giao dịch không hợp lệ.");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }
    }

    private static String normalizedNote(String note) {
        return note == null ? "" : note.trim();
    }
}