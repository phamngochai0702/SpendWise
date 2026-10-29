package com.spendwise.service;

import com.spendwise.app.AppContext;
import com.spendwise.model.Category;
import com.spendwise.model.TransactionType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Category rules: seeding per account, uniqueness, protection, safe deletion. */
class CategoryServiceTest {

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
                tempDir.resolve("cat-" + System.nanoTime() + ".db").toString());
        contexts.add(context);
        context.authService.register("u" + System.nanoTime(), "mat-khau-123", "");
        return context;
    }

    private long ownerOf(AppContext ctx) {
        return ctx.users.findFirst().id();
    }

    @Test
    void seedsTwelveDefaultsExactlyOncePerAccount() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        assertEquals(12, ctx.categories.countAll(owner));
        ctx.categoryService.seedDefaultsForOwner(owner);
        assertEquals(12, ctx.categories.countAll(owner));

        AppContext reopened = new AppContext(ctx.dbPath);
        contexts.add(reopened);
        assertEquals(12, reopened.categories.countAll(owner));
    }

    @Test
    void rejectsDuplicateNameInsideSameType() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        ctx.categoryService.add(owner, "Học phí test", TransactionType.EXPENSE, null);
        assertThrows(ValidationException.class, () ->
                ctx.categoryService.add(owner, "  học phí test ", TransactionType.EXPENSE, null));
        Category incomeTuition = ctx.categoryService.add(owner, "Học phí test",
                TransactionType.INCOME, "#0F766E");
        assertEquals(TransactionType.INCOME, incomeTuition.type());
    }

    @Test
    void rejectsBlankAndOversizedNames() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        assertThrows(ValidationException.class, () ->
                ctx.categoryService.add(owner, "   ", TransactionType.EXPENSE, null));
        assertThrows(ValidationException.class, () ->
                ctx.categoryService.add(owner, "x".repeat(101), TransactionType.EXPENSE, null));
        assertThrows(ValidationException.class, () ->
                ctx.categoryService.add(owner, null, TransactionType.EXPENSE, null));
    }

    @Test
    void updateRenamesAndRecolorsButKeepsType() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        Category created = ctx.categoryService.add(owner, "cũ",
                TransactionType.EXPENSE, "#0F766E");
        Category updated = ctx.categoryService.update(owner, created.id(), "mới", "#BE123C");
        assertEquals("mới", updated.name());
        assertEquals("#BE123C", updated.color());
        assertEquals(TransactionType.EXPENSE, updated.type());
        assertEquals(created.id(), updated.id());
    }

    @Test
    void deletingCategoryWithTransactionsRequiresReassignment() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        Category target = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(0);
        Category source = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(1);

        ctx.transactionService.add(owner, TransactionType.EXPENSE, 250_000, source.id(),
                java.time.LocalDate.of(2026, 3, 2), "giao dịch cần giữ");

        assertThrows(ValidationException.class,
                () -> ctx.categoryService.delete(owner, source.id(), null));

        ctx.categoryService.delete(owner, source.id(), target.id());
        assertTrue(ctx.transactions.byCategory(owner, source.id()).isEmpty());
        var moved = ctx.transactions.byCategory(owner, target.id());
        assertEquals(1, moved.size());
        assertEquals("giao dịch cần giữ", moved.get(0).note());
        assertNotNull(ctx.categories.findById(owner, target.id()));
    }

    @Test
    void deletingCategoryRemovesItsBudgetsAndKeepsOthers() {
        AppContext ctx = newContext();
        long owner = ownerOf(ctx);
        Category food = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(0);
        Category other = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(1);

        ctx.budgetService.add(owner, food.id(), 2_000_000,
                java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 3, 31));
        long keep = ctx.budgetService.add(owner, other.id(), 1_000_000,
                java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 3, 31)).id();
        assertEquals(2, ctx.budgets.countAll(owner));

        ctx.categoryService.delete(owner, food.id(), null);
        assertEquals(0, ctx.budgets.countByCategory(owner, food.id()));
        assertEquals(1, ctx.budgets.countAll(owner));
        assertNotNull(ctx.budgets.findById(owner, keep));
    }
}