package com.spendwise.service;

import com.spendwise.model.Budget;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.TransactionRepository;
import com.spendwise.util.DateUtil;
import com.spendwise.util.MoneyUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Business rules for (legacy range) budgets of one authenticated user:
 * expense-category restriction, valid inclusive date ranges, conflict
 * prevention and usage figures computed from real expense records.
 */
public final class BudgetService {

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public BudgetService(BudgetRepository budgets, CategoryRepository categories,
                         TransactionRepository transactions) {
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
    }

    public Budget add(long ownerId, long categoryId, long limit, LocalDate start, LocalDate end) {
        validate(ownerId, categoryId, limit, start, end, 0);
        return budgets.insert(ownerId, categoryId, limit, start, end);
    }

    public Budget update(long ownerId, long budgetId, long categoryId, long limit,
                         LocalDate start, LocalDate end) {
        if (budgets.findById(ownerId, budgetId) == null) {
            throw new ValidationException("Ngân sách không tồn tại hoặc đã bị xoá.");
        }
        validate(ownerId, categoryId, limit, start, end, budgetId);
        Budget updated = new Budget(budgetId, ownerId, categoryId, "", null,
                limit, start, end, null);
        return budgets.update(ownerId, updated);
    }

    public void delete(long ownerId, long budgetId) {
        if (budgets.findById(ownerId, budgetId) == null) {
            throw new ValidationException("Ngân sách không tồn tại hoặc đã bị xoá.");
        }
        budgets.deleteById(ownerId, budgetId);
    }

    /** All budgets of the user with real spending, newest range first. */
    public List<BudgetProgress> listWithProgress(long ownerId) {
        List<BudgetProgress> result = new ArrayList<>();
        for (Budget budget : budgets.findAll(ownerId)) {
            long spent = transactions.sumExpenseForCategory(
                    ownerId, budget.categoryId(), budget.start(), budget.end());
            result.add(new BudgetProgress(budget, spent));
        }
        return result;
    }

    public long spentFor(long ownerId, long categoryId, LocalDate from, LocalDate to) {
        return transactions.sumExpenseForCategory(ownerId, categoryId, from, to);
    }

    /**
     * Budgets whose range intersects [from, to]; spending is measured on the
     * intersection only, so a monthly budget half inside a 3-month range is
     * not overestimated.
     */
    public List<BudgetProgress> activeDuring(long ownerId, LocalDate from, LocalDate to) {
        List<BudgetProgress> result = new ArrayList<>();
        for (Budget budget : budgets.findAll(ownerId)) {
            if (!budget.overlapsRange(from, to)) {
                continue;
            }
            LocalDate start = budget.start().isAfter(from) ? budget.start() : from;
            LocalDate end = budget.end().isBefore(to) ? budget.end() : to;
            long spent = transactions.sumExpenseForCategory(ownerId, budget.categoryId(), start, end);
            result.add(new BudgetProgress(budget, spent));
        }
        return result;
    }

    private void validate(long ownerId, long categoryId, long limit, LocalDate start,
                          LocalDate end, long excludeId) {
        Category category = categories.findById(ownerId, categoryId);
        if (category == null) {
            throw new ValidationException("Vui lòng chọn danh mục cho ngân sách.");
        }
        if (category.type() != TransactionType.EXPENSE) {
            throw new ValidationException("Ngân sách chỉ áp dụng cho danh mục chi tiêu.");
        }
        if (limit <= 0) {
            throw new ValidationException("Hạn mức phải lớn hơn 0.");
        }
        if (limit > MoneyUtil.MAX_AMOUNT) {
            throw new ValidationException("Hạn mức vượt quá mức cho phép.");
        }
        if (start == null || end == null) {
            throw new ValidationException("Vui lòng chọn thời gian hiệu lực.");
        }
        if (end.isBefore(start)) {
            throw new ValidationException("Ngày kết thúc không thể trước ngày bắt đầu.");
        }
        long overlapping = budgets.countOverlapping(ownerId, categoryId, start, end, excludeId);
        if (overlapping > 0) {
            throw new ValidationException("Trùng với ngân sách hiện có của danh mục '"
                    + category.name() + "' trong giai đoạn "
                    + DateUtil.formatRange(start, end, "dd/MM/yyyy")
                    + ". Mỗi danh mục chỉ có một ngân sách cho một khoảng thời gian.");
        }
    }
}