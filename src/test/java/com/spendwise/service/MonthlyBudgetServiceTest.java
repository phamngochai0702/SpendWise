package com.spendwise.service;

import com.spendwise.app.AppContext;
import com.spendwise.model.BudgetStatus;
import com.spendwise.model.CategoryBudgetStatus;
import com.spendwise.model.MonthlyBudget;
import com.spendwise.model.MonthlyBudgetOverview;
import com.spendwise.model.RecentIncomeSummary;
import com.spendwise.model.TransactionType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Required monthly-budget cases from the feature specification. */
class MonthlyBudgetServiceTest {

    @TempDir
    Path tempDir;

    private final List<AppContext> contexts = new ArrayList<>();

    @AfterEach
    void closeDatabases() {
        for (AppContext context : contexts) {
            context.database.close();
        }
    }

    private AppContext newContext() {
        AppContext context = new AppContext(
                tempDir.resolve("mb-" + System.nanoTime() + ".db").toString());
        contexts.add(context);
        context.authService.register("u" + System.nanoTime(), "mat-khau-123", "");
        return context;
    }

    private long ownerOf(AppContext ctx) {
        return ctx.users.findFirst().id();
    }

    private long firstExpenseCategory(AppContext ctx, long owner) {
        return ctx.categories.findByType(owner, TransactionType.EXPENSE).get(0).id();
    }

    private void addExpense(AppContext ctx, long owner, long categoryId,
                            YearMonth month, long amount) {
        ctx.transactionService.add(owner, TransactionType.EXPENSE, amount, categoryId,
                month.atDay(Math.min(15, month.lengthOfMonth())), "spent");
    }

    private void addIncome(AppContext ctx, long owner, long categoryId,
                           YearMonth month, long amount) {
        ctx.transactionService.add(owner, TransactionType.INCOME, amount, categoryId,
                month.atDay(10), "income");
    }

    /** Case 1: 20tr với 30% giữ lại → tiết kiệm 6tr, ngân sách chi 14tr. */
    @Test
    void planMathTwentyMillionThirtyPercent() {
        assertEquals(6_000_000L, MonthlyBudgetService.savingsTarget(20_000_000L, 30));
        assertEquals(14_000_000L, MonthlyBudgetService.spendingBudget(20_000_000L, 30));
        assertEquals(0L, MonthlyBudgetService.savingsTarget(20_000_000L, 0));
        assertEquals(20_000_000L, MonthlyBudgetService.spendingBudget(20_000_000L, 0));
        assertEquals(20_000_000L, MonthlyBudgetService.savingsTarget(20_000_000L, 100));
        assertEquals(0L, MonthlyBudgetService.spendingBudget(20_000_000L, 100));
    }

    @Test
    void spendStatusThresholdsAndExactOvershoot() {
        assertEquals(BudgetStatus.NEAR, MonthlyBudgetService.statusFor(800_000, 1_000_000));
        assertEquals(BudgetStatus.OK, MonthlyBudgetService.statusFor(799_999, 1_000_000));
        assertEquals(BudgetStatus.EXCEEDED, MonthlyBudgetService.statusFor(1_000_000, 1_000_000));
        assertEquals(BudgetStatus.EXCEEDED, MonthlyBudgetService.statusFor(1_300_000, 1_000_000));
        assertEquals(300_000L, 1_300_000L - 1_000_000L);
    }

    /** Ví dụ đặc tả: 18 + 22 + 20(tháng 7) + 20 → TB = 20tr trên 4 tháng có thu. */
    @Test
    void averageIncomeIgnoresMonthsWithoutIncome() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long salary = ctx.categories.findByType(owner, TransactionType.INCOME).get(0).id();
        addIncome(ctx, owner, salary, YearMonth.of(2026, 4), 18_000_000);
        addIncome(ctx, owner, salary, YearMonth.of(2026, 5), 22_000_000);
        addIncome(ctx, owner, salary, YearMonth.of(2026, 7), 20_000_000);
        addIncome(ctx, owner, salary, YearMonth.of(2026, 9), 20_000_000);

        RecentIncomeSummary summary = ctx.monthlyBudgetService.recentIncome(
                owner, YearMonth.of(2026, 10));
        assertEquals(4, summary.monthsWithData());
        assertEquals(80_000_000, summary.totalIncome());
        assertEquals(20_000_000, summary.averageIncome());
    }

    @Test
    void incomeForMonthUsesOnlyRecordedIncomeInThatMonth() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long salary = ctx.categories.findByType(owner, TransactionType.INCOME).get(0).id();
        long expense = firstExpenseCategory(ctx, owner);
        YearMonth target = YearMonth.of(2026, 10);
        addIncome(ctx, owner, salary, target.minusMonths(1), 8_000_000);
        addIncome(ctx, owner, salary, target, 12_000_000);
        addExpense(ctx, owner, expense, target, 3_000_000);

        assertEquals(12_000_000, ctx.monthlyBudgetService.incomeForMonth(owner, target));
    }

    /** Không thu nhập 6 tháng → monthsWithData=0; lưu với thu nhập nhập tay vẫn hoạt động. */
    @Test
    void noIncomeInSixMonthsMeansManualEntry() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        RecentIncomeSummary summary = ctx.monthlyBudgetService.recentIncome(
                owner, YearMonth.of(2026, 10));
        assertEquals(0, summary.monthsWithData());
        assertEquals(0, summary.averageIncome());

        long category = firstExpenseCategory(ctx, owner);
        Map<Long, Long> allocations = new HashMap<>();
        allocations.put(category, 3_000_000L);
        MonthlyBudget saved = ctx.monthlyBudgetService.save(owner,
                YearMonth.of(2026, 10), 5_000_000, 20, allocations);
        assertEquals(5_000_000, saved.averageIncome());
        assertEquals(1_000_000, saved.savingsTarget());
        assertEquals(4_000_000, saved.totalBudget());
    }

    /** Case 2–3: phân bổ 5tr → còn 9tr; thêm 2tr → còn 7tr. Case 4: vượt → chặn. */
    @Test
    void allocationLimitAndLiveRemainingMath() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long food = firstExpenseCategory(ctx, owner);
        long transport = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(1).id();

        long totalBudget = 14_000_000L;
        Map<Long, Long> allocations = new HashMap<>();
        allocations.put(food, 5_000_000L);
        assertEquals(9_000_000L, MonthlyBudgetService.remainingOf(totalBudget, 5_000_000L));

        allocations.put(transport, 2_000_000L);
        long sum = allocations.get(food) + allocations.get(transport);
        assertEquals(7_000_000L, MonthlyBudgetService.remainingOf(totalBudget, sum));

        // vượt 1tr → ValidationException nêu đúng số vượt; không tạo bản ngân sách
        allocations.put(food, 13_000_000L);
        ValidationException ex = assertThrows(ValidationException.class, () ->
                ctx.monthlyBudgetService.save(owner, YearMonth.of(2026, 10),
                        20_000_000, 30, allocations));
        assertTrue(ex.getMessage().contains("vượt giới hạn 1.000.000"));
        assertNull(ctx.monthlyBudgetService.overview(owner, YearMonth.of(2026, 10)));
    }

    /** Case 5–7 + 8: chi 80%/100%/vượt → đúng trạng thái; thêm/sửa/xoá cập nhật ngay. */
    @Test
    void trackingFollowsTransactionsLive() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long food = firstExpenseCategory(ctx, owner);
        Map<Long, Long> allocations = new HashMap<>();
        allocations.put(food, 5_000_000L);

        MonthlyBudget saved = ctx.monthlyBudgetService.save(owner,
                YearMonth.of(2026, 10), 20_000_000, 30, allocations);
        assertNotNull(saved);

        // 80% → NEAR (4tr/5tr = 80%) — trạng thái của danh mục, tổng 14tr mới 28.6%
        addExpense(ctx, owner, food, YearMonth.of(2026, 10), 4_000_000);
        MonthlyBudgetOverview near = ctx.monthlyBudgetService.overview(
                owner, YearMonth.of(2026, 10));
        assertEquals(4_000_000, near.totalSpent());
        assertEquals(BudgetStatus.NEAR, near.statuses().get(0).status());
        assertEquals(80, Math.round(near.statuses().get(0).usedRatio() * 100));
        assertEquals(1_000_000, near.statuses().get(0).remaining());

        // Case 6: 100% ở danh mục → EXCEEDED, còn lại 0 (tổng 14tr mới ~35.7%)
        addExpense(ctx, owner, food, YearMonth.of(2026, 10), 1_000_000);
        MonthlyBudgetOverview hit = ctx.monthlyBudgetService.overview(owner, YearMonth.of(2026, 10));
        assertEquals(5_000_000, hit.totalSpent());
        assertEquals(0, hit.statuses().get(0).remaining());
        assertEquals(BudgetStatus.EXCEEDED, hit.statuses().get(0).status());

        // Case 7: 106% ở danh mục → vượt đúng 300k
        addExpense(ctx, owner, food, YearMonth.of(2026, 10), 300_000);
        MonthlyBudgetOverview over = ctx.monthlyBudgetService.overview(owner, YearMonth.of(2026, 10));
        assertEquals(5_300_000, over.totalSpent());
        assertEquals(106, Math.round(over.statuses().get(0).usedRatio() * 100));
        assertEquals(-300_000, over.statuses().get(0).remaining());

        // Case 8: sửa giao dịch (300k -> 700k) → tổng 5.7M, danh mục còn -700k ngay
        var rows = ctx.transactionService.byCategory(owner, food);
        long editId = rows.get(0).id();
        ctx.transactionService.update(owner, editId, TransactionType.EXPENSE, 700_000,
                food, rows.get(0).date(), "spent");
        MonthlyBudgetOverview afterEdit = ctx.monthlyBudgetService.overview(
                owner, YearMonth.of(2026, 10));
        assertEquals(5_700_000, afterEdit.totalSpent());
        assertEquals(-700_000, afterEdit.statuses().get(0).remaining());

        // xoá một giao dịch → cập nhật ngay: về đúng 5M (100%), còn lại 0
        ctx.transactionService.delete(owner, editId);
        MonthlyBudgetOverview afterDelete = ctx.monthlyBudgetService.overview(
                owner, YearMonth.of(2026, 10));
        assertEquals(5_000_000, afterDelete.totalSpent());
        assertEquals(0, afterDelete.statuses().get(0).remaining());
    }

    /** Case 10: mỗi user một ngân sách/tháng; dữ liệu tách biệt giữa các user. */
    @Test
    void oneBudgetPerUserPerMonthAndIsolation() {
        AppContext ctx = newContext();
        long ownerA = ctx.users.findFirst().id();
        long catA = firstExpenseCategory(ctx, ownerA);

        Map<Long, Long> allocationsA = new HashMap<>();
        allocationsA.put(catA, 1_000_000L);
        ctx.monthlyBudgetService.save(ownerA, YearMonth.of(2026, 10), 10_000_000, 20, allocationsA);

        // lưu lần 2 trong cùng tháng → cập nhật, KHÔNG tạo bản trùng
        ctx.monthlyBudgetService.save(ownerA, YearMonth.of(2026, 10), 10_000_000, 25, allocationsA);
        MonthlyBudgetOverview reloaded = ctx.monthlyBudgetService.overview(
                ownerA, YearMonth.of(2026, 10));
        assertEquals(25, reloaded.budget().savingRatePercent());
        assertEquals(2_500_000, reloaded.budget().savingsTarget());
        assertEquals(1, ctx.monthlyBudgets.countByOwner(ownerA));

        // user B không thấy dữ liệu của A và tạo ngân sách riêng
        ctx.authService.register("user.b.x", "mat_khau_999", "");
        long ownerB = ctx.users.findByUsername("user.b.x").id();
        long catB = ctx.categories.findByType(ownerB, TransactionType.EXPENSE).get(0).id();
        Map<Long, Long> allocationsB = new HashMap<>();
        allocationsB.put(catB, 500_000L);
        ctx.monthlyBudgetService.save(ownerB, YearMonth.of(2026, 10), 8_000_000, 10, allocationsB);

        MonthlyBudgetOverview forA = ctx.monthlyBudgetService.overview(
                ownerA, YearMonth.of(2026, 10));
        MonthlyBudgetOverview forB = ctx.monthlyBudgetService.overview(
                ownerB, YearMonth.of(2026, 10));
        assertEquals(10_000_000, forA.budget().averageIncome());
        assertEquals(8_000_000, forB.budget().averageIncome());
        assertEquals(1_000_000, forA.statuses().get(0).allocated(),
                "statusesA=" + dump(forA));
        assertEquals(500_000, forB.statuses().get(0).allocated(),
                "statusesB=" + dump(forB));
        assertEquals(ownerA, forA.budget().ownerId());
        assertEquals(ownerB, forB.budget().ownerId());
    }

    private String dump(MonthlyBudgetOverview overview) {
        StringBuilder sb = new StringBuilder();
        sb.append("budget{id=").append(overview.budget().id())
                .append(", owner=").append(overview.budget().ownerId())
                .append(", total=").append(overview.budget().totalBudget())
                .append("} rows=[");
        for (CategoryBudgetStatus s : overview.statuses()) {
            sb.append(s.name()).append("(id=").append(s.categoryId())
                    .append(", alloc=").append(s.allocated())
                    .append(", spent=").append(s.spent())
                    .append("), ");
        }
        return sb.append("]").toString();
    }
}