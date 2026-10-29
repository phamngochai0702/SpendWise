package com.spendwise.service;

import com.spendwise.app.AppContext;
import com.spendwise.model.Budget;
import com.spendwise.model.BudgetProgress;
import com.spendwise.model.BudgetStatus;
import com.spendwise.model.TransactionType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Legacy range-budget rules scoped per user. */
class BudgetServiceTest {

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
                tempDir.resolve("budget-" + System.nanoTime() + ".db").toString());
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

    @Test
    void budgetsAreExpenseOnly() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long incomeCategory = ctx.categories.findByType(owner, TransactionType.INCOME).get(0).id();
        assertThrows(ValidationException.class, () -> ctx.budgetService.add(owner, incomeCategory,
                1_000_000, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test
    void preventsOverlappingBudgetsPerCategory() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long category = firstExpenseCategory(ctx, owner);
        ctx.budgetService.add(owner, category, 1_000_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThrows(ValidationException.class, () -> ctx.budgetService.add(owner, category,
                500_000, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15)));
        assertThrows(ValidationException.class, () -> ctx.budgetService.add(owner, category,
                500_000, LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 2)));
        assertThrows(ValidationException.class, () -> ctx.budgetService.add(owner, category,
                500_000, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        ctx.budgetService.add(owner, category, 500_000,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertEquals(2, ctx.budgets.findAll(owner).size());
    }

    @Test
    void updateMayKeepSameRangeButNotOverlapOthers() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long category = firstExpenseCategory(ctx, owner);
        Budget original = ctx.budgetService.add(owner, category, 1_000_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        ctx.budgetService.add(owner, category, 800_000,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        ctx.budgetService.update(owner, original.id(), category, 1_200_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(1_200_000, ctx.budgets.findById(owner, original.id()).limitAmount());

        assertThrows(ValidationException.class, () -> ctx.budgetService.update(owner,
                original.id(), category, 1_000_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30)));
    }

    @Test
    void statusThresholds() {
        assertEquals(BudgetStatus.OK, BudgetStatus.of(0, 1000));
        assertEquals(BudgetStatus.OK, BudgetStatus.of(799, 1000));
        assertEquals(BudgetStatus.NEAR, BudgetStatus.of(800, 1000));
        assertEquals(BudgetStatus.NEAR, BudgetStatus.of(999, 1000));
        assertEquals(BudgetStatus.EXCEEDED, BudgetStatus.of(1000, 1000));
        assertEquals(BudgetStatus.EXCEEDED, BudgetStatus.of(1500, 1000));
    }

    @Test
    void spentUsesRealExpenseRecordsAdjacentToRange() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long category = firstExpenseCategory(ctx, owner);
        ctx.budgetService.add(owner, category, 1_000_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        ctx.transactionService.add(owner, TransactionType.EXPENSE, 850_000, category,
                LocalDate.of(2026, 3, 10), "trong kỳ");
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 500_000, category,
                LocalDate.of(2026, 4, 5), "ngoài kỳ");

        List<BudgetProgress> rows = ctx.budgetService.listWithProgress(owner);
        assertEquals(1, rows.size());
        assertEquals(850_000, rows.get(0).spent());
        assertEquals(BudgetStatus.NEAR, rows.get(0).status());
        assertEquals(150_000, rows.get(0).remaining());

        List<BudgetProgress> window = ctx.budgetService.activeDuring(owner,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, window.size());
        assertEquals(850_000, window.get(0).spent());

        assertTrue(ctx.budgetService.activeDuring(owner,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)).isEmpty());
    }

    @Test
    void deleteRemovesBudget() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        long category = firstExpenseCategory(ctx, owner);
        Budget budget = ctx.budgetService.add(owner, category, 1_000_000,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        ctx.budgetService.delete(owner, budget.id());
        assertThrows(ValidationException.class, () -> ctx.budgetService.delete(owner, budget.id()));
        assertEquals(0, ctx.budgets.countAll(owner));
    }
}