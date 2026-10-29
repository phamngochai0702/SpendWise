package com.spendwise.service;

import com.spendwise.model.BudgetStatus;
import com.spendwise.model.Category;
import com.spendwise.model.CategoryBudgetStatus;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.MonthlyBudgetItem;
import com.spendwise.model.MonthlyBudgetOverview;
import com.spendwise.model.RecentIncomeSummary;
import com.spendwise.model.TimePoint;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.DatabaseManager;
import com.spendwise.repository.MonthlyBudgetRepository;
import com.spendwise.repository.TransactionRepository;
import com.spendwise.util.DateUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly planning budgets: income recorded for the planning month is the
 * default basis, with average income over the 6 most recent months available
 * as a suggestion. A saving rate is applied to the selected income, followed
 * by per-category allocation with a hard limit check and live tracking of real
 * spending. Everything is user-scoped and totals are re-computed server-side.
 */
public final class MonthlyBudgetService {

    /** Number of completed months examined for the average income. */
    public static final int RECENT_MONTHS = 6;
    public static final int MIN_SAVING_RATE = 0;
    public static final int MAX_SAVING_RATE = 100;

    private final MonthlyBudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final DatabaseManager database;

    public MonthlyBudgetService(MonthlyBudgetRepository budgets, CategoryRepository categories,
                                TransactionRepository transactions, DatabaseManager database) {
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
        this.database = database;
    }

    // ------------------------------------------------------------------
    // Average income (the 6 months before the planning month)
    // ------------------------------------------------------------------

    /**
     * @param targetMonth the month being planned; the 6 months immediately
     *                    before it are examined
     */
    public RecentIncomeSummary recentIncome(long ownerId, YearMonth targetMonth) {
        YearMonth first = targetMonth.minusMonths(RECENT_MONTHS);
        LocalDate from = first.atDay(1);
        LocalDate to = targetMonth.minusMonths(1).atEndOfMonth();

        Map<String, long[]> byMonth = transactions.groupByYearMonth(ownerId, from, to);

        List<TimePoint> months = new ArrayList<>();
        int monthsWithData = 0;
        long total = 0;
        YearMonth cursor = first;
        while (cursor.isBefore(targetMonth)) {
            String key = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            long[] pair = byMonth.getOrDefault(key, new long[2]);
            if (pair[0] > 0) {
                monthsWithData++; // months without income are NOT counted as 0
                total += pair[0];
            }
            months.add(new TimePoint(key, DateUtil.monthShortLabel(cursor), pair[0], pair[1]));
            cursor = cursor.plusMonths(1);
        }
        long average = monthsWithData == 0 ? 0 : total / monthsWithData;
        return new RecentIncomeSummary(from, to, monthsWithData, total, average, months);
    }

    /** Total income recorded for the selected planning month. */
    public long incomeForMonth(long ownerId, YearMonth month) {
        return transactions.sumByTypeBetween(ownerId, TransactionType.INCOME,
                month.atDay(1), month.atEndOfMonth());
    }

    // ------------------------------------------------------------------
    // Plan math (single source of truth, also used by the UI live preview)
    // ------------------------------------------------------------------

    public static long savingsTarget(long incomeBasis, int savingRatePercent) {
        return incomeBasis * clampRate(savingRatePercent) / 100L;
    }

    public static long spendingBudget(long incomeBasis, int savingRatePercent) {
        return incomeBasis - savingsTarget(incomeBasis, savingRatePercent);
    }

    public static long remainingOf(long totalBudget, long allocatedSum) {
        return totalBudget - allocatedSum;
    }

    private static int clampRate(int rate) {
        return Math.max(MIN_SAVING_RATE, Math.min(MAX_SAVING_RATE, rate));
    }

    // ------------------------------------------------------------------
    // Save (create or edit) — one transaction, totals re-verified here
    // ------------------------------------------------------------------

    /**
     * Creates or updates the user's budget for the month. Allocations may
     * total at most the spending budget; anything else is rejected with the
     * exact overshoot so the user can adjust instead of losing input.
     */
    public MonthlyBudget save(long ownerId, YearMonth month, long averageIncome,
                              int savingRatePercent, Map<Long, Long> allocations) {
        if (ownerId <= 0) {
            throw new ValidationException("Phiên đăng nhập không hợp lệ.");
        }
        if (averageIncome < 0) {
            throw new ValidationException("Thu nhập dự kiến không thể âm.");
        }
        if (averageIncome > com.spendwise.util.MoneyUtil.MAX_AMOUNT) {
            throw new ValidationException("Thu nhập dự kiến vượt quá mức cho phép.");
        }
        int rate = clampRate(savingRatePercent);
        long savingsTarget = savingsTarget(averageIncome, rate);
        long totalBudget = averageIncome - savingsTarget;

        Map<Long, Long> cleanAllocations = validateAllocations(ownerId, allocations);
        long allocatedSum = 0;
        for (long amount : cleanAllocations.values()) {
            allocatedSum += amount;
        }
        if (allocatedSum > totalBudget) {
            long overshoot = allocatedSum - totalBudget;
            throw new ValidationException("Tổng ngân sách đã phân bổ vượt giới hạn "
                    + com.spendwise.util.MoneyUtil.format(overshoot, "đ")
                    + ". Hãy giảm bớt để không vượt quá giới hạn "
                    + com.spendwise.util.MoneyUtil.format(totalBudget, "đ") + ".");
        }

        database.inTransaction(() -> {
            MonthlyBudget existing = budgets.findByOwnerAndMonth(ownerId,
                    month.getYear(), month.getMonthValue());
            long budgetId;
            if (existing == null) {
                budgetId = budgets.insert(ownerId, month.getYear(), month.getMonthValue(),
                        averageIncome, rate, savingsTarget, totalBudget);
            } else {
                budgetId = existing.id();
                budgets.update(budgetId, averageIncome, rate, savingsTarget, totalBudget);
                budgets.deleteItems(budgetId);
            }
            for (Map.Entry<Long, Long> entry : cleanAllocations.entrySet()) {
                budgets.insertItem(budgetId, entry.getKey(), entry.getValue());
            }
        });
        return budgets.findByOwnerAndMonth(ownerId, month.getYear(), month.getMonthValue());
    }

    /**
     * Drops zero entries, rejects negatives/oversize and verifies each category
     * belongs to the owner and is an expense category.
     */
    private Map<Long, Long> validateAllocations(long ownerId, Map<Long, Long> allocations) {
        Map<Long, Long> clean = new HashMap<>();
        if (allocations == null) {
            return clean;
        }
        for (Map.Entry<Long, Long> entry : allocations.entrySet()) {
            long amount = entry.getValue() == null ? 0 : entry.getValue();
            if (amount == 0) {
                continue;
            }
            if (amount < 0) {
                throw new ValidationException("Ngân sách cho một danh mục không thể âm.");
            }
            Category category = categories.findById(ownerId, entry.getKey());
            if (category == null) {
                throw new ValidationException("Danh mục không tồn tại trong tài khoản của bạn.");
            }
            if (category.type() != TransactionType.EXPENSE) {
                throw new ValidationException("Ngân sách tháng chỉ áp dụng cho danh mục chi tiêu.");
            }
            if (amount > com.spendwise.util.MoneyUtil.MAX_AMOUNT) {
                throw new ValidationException("Ngân sách cho '"
                        + category.name() + "' vượt quá mức cho phép.");
            }
            clean.put(entry.getKey(), amount);
        }
        return clean;
    }

    /**
     * The saved plan joined with REAL spending of the month (sum of valid
     * expense transactions per category). Returns null when no plan exists.
     */
    public MonthlyBudgetOverview overview(long ownerId, YearMonth month) {
        MonthlyBudget budget =
                budgets.findByOwnerAndMonth(ownerId, month.getYear(), month.getMonthValue());
        if (budget == null) {
            return null;
        }
        Map<Long, Long> allocated = new HashMap<>();
        for (MonthlyBudgetItem item : budgets.listItems(budget.id())) {
            allocated.put(item.categoryId(), item.amount());
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<CategoryBudgetStatus> statuses = new ArrayList<>();
        for (Category category : categories.findByType(ownerId, TransactionType.EXPENSE)) {
            long alloc = allocated.getOrDefault(category.id(), 0L);
            long spent = transactions.sumExpenseForCategory(ownerId, category.id(), start, end);
            statuses.add(new CategoryBudgetStatus(category.id(), category.name(),
                    category.color(), alloc, spent));
        }
        // Budgeted categories first (biggest plan), then the rest alphabetically.
        statuses.sort((a, b) -> {
            int byAllocated = Long.compare(b.allocated(), a.allocated());
            return byAllocated != 0 ? byAllocated : a.name().compareToIgnoreCase(b.name());
        });
        return new MonthlyBudgetOverview(budget, statuses);
    }

    /** Status threshold: below 80% OK, 80..100% NEAR, 100%+ EXCEEDED. */
    public static BudgetStatus statusFor(long spent, long allocated) {
        return BudgetStatus.of(spent, allocated);
    }
}