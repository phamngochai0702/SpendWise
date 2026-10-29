package com.spendwise.repository;

import com.spendwise.app.AppContext;
import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionFilter;
import com.spendwise.model.TransactionType;
import com.spendwise.service.ValidationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real CRUD + filtering + per-user isolation against temporary SQLite databases. */
class RepositoryCrudAndFilterTest {

    @TempDir
    Path tempDir;

    private final List<AppContext> contexts = new ArrayList<>();

    @AfterEach
    void closeDatabases() {
        for (AppContext context : contexts) {
            context.database.close();
        }
    }

    /** Registers a fresh user (seeding gives them 12 default categories). */
    private AppContext newContext() {
        AppContext context = new AppContext(
                tempDir.resolve("crud-" + System.nanoTime() + ".db").toString());
        contexts.add(context);
        context.authService.register("u" + System.nanoTime(), "mat-khau-123", "");
        return context;
    }

    private void addTransaction(AppContext ctx, long owner, TransactionType type,
                                long categoryId, long amount, String isoDate, String note) {
        ctx.transactionService.add(owner, type, amount, categoryId,
                LocalDate.parse(isoDate), note);
    }

    @Test
    void insertUpdateDeleteLifecycle() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long category = ctx.categoryService.add(owner, "test-food",
                TransactionType.EXPENSE, "#0F766E").id();
        long id = ctx.transactionService.add(owner, TransactionType.EXPENSE, 50_000,
                category, LocalDate.of(2026, 3, 10), "cơm trưa").id();

        Transaction stored = ctx.transactionService.requireExisting(owner, id);
        assertEquals(TransactionType.EXPENSE, stored.type());
        assertEquals(50_000, stored.amount());
        assertEquals("cơm trưa", stored.note());
        assertEquals("test-food", stored.categoryName());
        assertNotNull(stored.createdAt());

        ctx.transactionService.update(owner, id, TransactionType.EXPENSE, 75_000,
                category, LocalDate.of(2026, 3, 11), "cơm tối");
        assertEquals(75_000, ctx.transactionService.requireExisting(owner, id).amount());

        ctx.transactionService.delete(owner, id);
        assertThrows(ValidationException.class,
                () -> ctx.transactionService.requireExisting(owner, id));
    }

    @Test
    void anotherUserCannotSeeOrChangeForeignRows() {
        AppContext ctx = newContext();
        long alice = ctx.users.findFirst().id();
        long category = ctx.categoryService.add(alice, "secret-food",
                TransactionType.EXPENSE, "#0F766E").id();
        long id = ctx.transactionService.add(alice, TransactionType.EXPENSE, 123_000,
                category, LocalDate.of(2026, 3, 10), "riêng tư").id();

        ctx.authService.register("noel.gru", "mat-khau-456", "");
        long bob = ctx.users.findByUsername("noel.gru").id();
        assertEquals(0, ctx.transactions.countAll(bob));
        assertEquals(12, ctx.categories.countAll(bob)); // only their own seeds

        assertNull(ctx.transactions.findById(bob, id));
        assertNull(ctx.categories.findById(bob, category));
        assertFalse(ctx.transactions.deleteById(bob, id));
        assertTrue(ctx.transactions.deleteById(alice, id));
    }

    @Test
    void searchFiltersAndSorting() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long food = ctx.categoryService.add(owner, "test-food",
                TransactionType.EXPENSE, "#0F766E").id();
        long transport = ctx.categoryService.add(owner, "test-transport",
                TransactionType.EXPENSE, "#0F766E").id();
        long salary = ctx.categoryService.add(owner, "test-salary",
                TransactionType.INCOME, "#0F766E").id();

        addTransaction(ctx, owner, TransactionType.EXPENSE, food, 100_000, "2026-03-01", "groceries");
        addTransaction(ctx, owner, TransactionType.EXPENSE, transport, 250_000, "2026-03-05", "bus tickets");
        addTransaction(ctx, owner, TransactionType.INCOME, salary, 9_000_000, "2026-03-06", "salary");
        addTransaction(ctx, owner, TransactionType.EXPENSE, food, 50_000, "2026-02-20", "coffee");

        List<Transaction> keywordHits = ctx.transactionService.search(owner,
                new TransactionFilter().keyword("bus"));
        assertEquals(1, keywordHits.size());
        assertEquals("bus tickets", keywordHits.get(0).note());

        assertEquals(1, ctx.transactionService.search(owner,
                new TransactionFilter().keyword("test-salary")).size());

        List<Transaction> marchExpenses = ctx.transactionService.search(owner,
                new TransactionFilter()
                        .type(TransactionType.EXPENSE)
                        .between(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertEquals(2, marchExpenses.size());

        List<Transaction> amountRange = ctx.transactionService.search(owner,
                new TransactionFilter().amountRange(100_000L, 300_000L));
        assertEquals(2, amountRange.size());

        assertEquals(2, ctx.transactionService.search(owner,
                new TransactionFilter().categoryId(food)).size());

        List<Transaction> cheapestFirst = ctx.transactionService.search(owner,
                new TransactionFilter().sort(TransactionFilter.Sort.AMOUNT_ASC));
        assertEquals(50_000, cheapestFirst.get(0).amount());

        List<Transaction> newestFirst = ctx.transactionService.search(owner, new TransactionFilter());
        assertEquals("2026-03-06", newestFirst.get(0).date().toString());

        List<Transaction> byCategoryName = ctx.transactionService.search(owner,
                new TransactionFilter().sort(TransactionFilter.Sort.CATEGORY));
        assertTrue(byCategoryName.get(0).categoryName()
                .compareToIgnoreCase(byCategoryName.get(1).categoryName()) <= 0);

        assertTrue(ctx.transactionService.search(owner,
                new TransactionFilter().type(TransactionType.INCOME)
                        .between(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31))).isEmpty());
    }

    @Test
    void aggregationsAndReassignment() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long food = ctx.categoryService.add(owner, "agg-food",
                TransactionType.EXPENSE, "#0F766E").id();
        long other = ctx.categoryService.add(owner, "agg-other",
                TransactionType.EXPENSE, "#0F766E").id();
        long salary = ctx.categoryService.add(owner, "agg-salary",
                TransactionType.INCOME, "#0F766E").id();

        addTransaction(ctx, owner, TransactionType.EXPENSE, food, 200_000, "2026-03-01", "a");
        addTransaction(ctx, owner, TransactionType.EXPENSE, food, 300_000, "2026-03-10", "b");
        addTransaction(ctx, owner, TransactionType.EXPENSE, other, 150_000, "2026-04-02", "c");
        addTransaction(ctx, owner, TransactionType.INCOME, salary, 5_000_000, "2026-03-15", "d");

        assertEquals(500_000, ctx.transactions.sumByTypeBetween(owner,
                TransactionType.EXPENSE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertEquals(650_000, ctx.transactions.sumAllTimeByType(owner, TransactionType.EXPENSE));
        assertEquals(500_000, ctx.transactions.sumExpenseForCategory(owner, food,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        var byMonth = ctx.transactions.groupByYearMonth(owner,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        assertEquals(5_000_000, byMonth.get("2026-03")[0]);
        assertEquals(500_000, byMonth.get("2026-03")[1]);
        assertEquals(150_000, byMonth.get("2026-04")[1]);
        assertFalse(byMonth.containsKey("2026-05"));

        var byCategory = ctx.transactions.totalsByCategory(owner,
                TransactionType.EXPENSE, null, null);
        assertEquals(2, byCategory.get(food)[0]);
        assertEquals(500_000, byCategory.get(food)[1]);

        ctx.transactions.reassignCategory(owner, food, other);
        assertEquals(3, ctx.transactions.byCategory(owner, other).size());
        assertTrue(ctx.transactions.byCategory(owner, food).isEmpty());
    }

    @Test
    void typeMismatchTransactionIsRejected() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long income = ctx.categoryService.add(owner, "val-income",
                TransactionType.INCOME, "#0F766E").id();
        assertThrows(ValidationException.class, () ->
                ctx.transactionService.add(owner, TransactionType.EXPENSE, 10_000,
                        income, LocalDate.of(2026, 3, 1), "sai loại"));
    }
}