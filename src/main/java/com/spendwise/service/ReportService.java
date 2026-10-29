package com.spendwise.service;

import com.spendwise.model.BudgetProgress;
import com.spendwise.model.Category;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.DashboardSnapshot;
import com.spendwise.model.PeriodKey;
import com.spendwise.model.RangeSummary;
import com.spendwise.model.TimePoint;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionFilter;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.TransactionRepository;
import com.spendwise.util.DateUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds every dashboard value and report of one authenticated user from real
 * database records. No fabricated data ever enters these figures.
 */
public final class ReportService {

    private static final int RECENT_LIMIT = 6;

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final BudgetService budgetService;

    public ReportService(TransactionRepository transactions, CategoryRepository categories,
                         BudgetService budgetService) {
        this.transactions = transactions;
        this.categories = categories;
        this.budgetService = budgetService;
    }

    /** All-time income minus all-time expenses of the user. */
    public long netAllTime(long ownerId) {
        return transactions.sumAllTimeByType(ownerId, TransactionType.INCOME)
                - transactions.sumAllTimeByType(ownerId, TransactionType.EXPENSE);
    }

    public DashboardSnapshot buildDashboard(long ownerId, PeriodKey period, LocalDate today) {
        LocalDate[] range = period.range(today);
        long income = transactions.sumByTypeBetween(ownerId, TransactionType.INCOME, range[0], range[1]);
        long expense = transactions.sumByTypeBetween(ownerId, TransactionType.EXPENSE, range[0], range[1]);

        YearMonth endMonth = YearMonth.from(range[1]);
        LocalDate seriesStart = endMonth.minusMonths(5).atDay(1);
        List<TimePoint> monthly = monthlySeries(ownerId, seriesStart, range[1]);

        List<CategoryTotal> distribution = expenseDistribution(ownerId, range[0], range[1]);

        List<BudgetProgress> budgetRows = new ArrayList<>(
                budgetService.activeDuring(ownerId, range[0], range[1]));
        budgetRows.sort(Comparator.comparingDouble(BudgetProgress::usedRatio).reversed());

        List<Transaction> recent = transactions.recent(ownerId, RECENT_LIMIT);
        return new DashboardSnapshot(range[0], range[1], income, expense, netAllTime(ownerId),
                monthly, distribution, budgetRows, recent);
    }

    /**
     * Income vs expenses over time; the bucket size adapts to the range length
     * (daily up to ~2 months, monthly up to ~2 years, yearly beyond that).
     */
    public List<TimePoint> seriesWithGranularity(long ownerId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return List.of();
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 62) {
            return dailySeries(ownerId, from, to);
        }
        if (days <= 800) {
            return monthlySeries(ownerId, from, to);
        }
        return yearlySeries(ownerId, from, to);
    }

    public List<TimePoint> monthlySeries(long ownerId, LocalDate from, LocalDate to) {
        Map<String, long[]> grouped = transactions.groupByYearMonth(ownerId, from, to);
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        List<TimePoint> points = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            String key = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            long[] pair = grouped.getOrDefault(key, new long[2]);
            points.add(new TimePoint(key, DateUtil.monthShortLabel(cursor), pair[0], pair[1]));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    private List<TimePoint> dailySeries(long ownerId, LocalDate from, LocalDate to) {
        Map<String, long[]> grouped = transactions.groupByDay(ownerId, from, to);
        List<TimePoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            long[] pair = grouped.getOrDefault(day.toString(), new long[2]);
            points.add(new TimePoint(day.toString(), DateUtil.format(day, "dd/MM"), pair[0], pair[1]));
        }
        return points;
    }

    private List<TimePoint> yearlySeries(long ownerId, LocalDate from, LocalDate to) {
        Map<String, long[]> grouped = transactions.groupByYear(ownerId, from, to);
        List<TimePoint> points = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            long[] pair = grouped.getOrDefault(Integer.toString(year), new long[2]);
            points.add(new TimePoint(Integer.toString(year), "Năm " + year, pair[0], pair[1]));
        }
        return points;
    }

    /** Expense distribution for the donut chart; only used categories are returned. */
    public List<CategoryTotal> expenseDistribution(long ownerId, LocalDate from, LocalDate to) {
        return categoryTotals(ownerId, TransactionType.EXPENSE, from, to, false);
    }

    public List<CategoryTotal> categoryTotals(long ownerId, TransactionType type,
                                              LocalDate from, LocalDate to, boolean includeEmpty) {
        List<Category> categoryList = categories.findByType(ownerId, type);
        Map<Long, long[]> totals = transactions.totalsByCategory(ownerId, type, from, to);
        List<CategoryTotal> result = new ArrayList<>();
        for (Category category : categoryList) {
            long[] pair = totals.getOrDefault(category.id(), new long[2]);
            if (!includeEmpty && pair[0] == 0) {
                continue;
            }
            result.add(new CategoryTotal(category.id(), category.name(), category.color(),
                    type, pair[0], pair[1]));
        }
        result.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        return result;
    }

    /** Transaction summary of one date range: totals, extremes and averages. */
    public RangeSummary rangeSummary(long ownerId, LocalDate from, LocalDate to) {
        List<Transaction> rows = transactions.search(ownerId,
                new TransactionFilter().between(from, to));
        long income = 0;
        long expense = 0;
        long largestExpense = 0;
        long largestIncome = 0;
        long expenseCount = 0;
        for (Transaction transaction : rows) {
            if (transaction.type() == TransactionType.INCOME) {
                income += transaction.amount();
                if (transaction.amount() > largestIncome) {
                    largestIncome = transaction.amount();
                }
            } else {
                expense += transaction.amount();
                expenseCount++;
                if (transaction.amount() > largestExpense) {
                    largestExpense = transaction.amount();
                }
            }
        }
        long averageExpense = expenseCount == 0 ? 0 : expense / expenseCount;
        return new RangeSummary(rows.size(), income, expense, largestExpense, largestIncome,
                averageExpense);
    }
}