package com.spendwise.service;

import com.spendwise.app.AppContext;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.DashboardSnapshot;
import com.spendwise.model.PeriodKey;
import com.spendwise.model.RangeSummary;
import com.spendwise.model.TimePoint;
import com.spendwise.model.TransactionType;
import com.spendwise.util.CsvExporter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Report mathematics + CSV export correctness, user-scoped. */
class ReportAndExportTest {

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
                tempDir.resolve("report-" + System.nanoTime() + ".db").toString());
        contexts.add(context);
        context.authService.register("u" + System.nanoTime(), "mat-khau-123", "");
        return context;
    }

    @Test
    void dashboardFiguresComeFromRealRecords() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long salary = ctx.categories.findByType(owner, TransactionType.INCOME).get(0).id();
        long food = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(0).id();

        ctx.transactionService.add(owner, TransactionType.INCOME, 10_000_000, salary,
                LocalDate.of(2026, 3, 5), "salary");
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 3_000_000, food,
                LocalDate.of(2026, 3, 10), "gros");
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 5_000_000, food,
                LocalDate.of(2026, 3, 15), "rent");
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 1_000, food,
                LocalDate.of(2026, 2, 20), "outside period");

        DashboardSnapshot snapshot = ctx.reportService.buildDashboard(
                owner, PeriodKey.THIS_MONTH, LocalDate.of(2026, 3, 31));
        assertEquals(10_000_000, snapshot.incomeTotal());
        assertEquals(8_000_000, snapshot.expenseTotal());
        assertEquals(1_999_000, snapshot.netAllTime());

        TimePoint march = snapshot.monthlySeries().get(snapshot.monthlySeries().size() - 1);
        assertEquals("2026-03", march.key());
        assertEquals(10_000_000, march.income());
        assertEquals(8_000_000, march.expense());
        assertEquals(6, snapshot.monthlySeries().size());

        List<CategoryTotal> distribution = snapshot.expenseDistribution();
        assertEquals(1, distribution.size());
        assertEquals(8_000_000, distribution.get(0).amount());
    }

    @Test
    void rangeSummaryComputesExtremesAndAverage() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        long food = ctx.categories.findByType(owner, TransactionType.EXPENSE).get(0).id();
        long salary = ctx.categories.findByType(owner, TransactionType.INCOME).get(0).id();
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 3_000_000, food,
                LocalDate.of(2026, 3, 10), "a");
        ctx.transactionService.add(owner, TransactionType.EXPENSE, 5_000_000, food,
                LocalDate.of(2026, 3, 15), "b");
        ctx.transactionService.add(owner, TransactionType.INCOME, 20_000_000, salary,
                LocalDate.of(2026, 3, 1), "c");

        RangeSummary summary = ctx.reportService.rangeSummary(
                owner, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(3, summary.transactionCount());
        assertEquals(20_000_000, summary.incomeTotal());
        assertEquals(8_000_000, summary.expenseTotal());
        assertEquals(5_000_000, summary.largestExpense());
        assertEquals(20_000_000, summary.largestIncome());
        assertEquals(4_000_000, summary.averageExpense());
        assertEquals(12_000_000, summary.net());
    }

    @Test
    void granularityAdaptsToRangeLength() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        List<TimePoint> daily = ctx.reportService.seriesWithGranularity(owner,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertEquals(31, daily.size());

        List<TimePoint> monthly = ctx.reportService.seriesWithGranularity(owner,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(12, monthly.size());

        List<TimePoint> yearly = ctx.reportService.seriesWithGranularity(owner,
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(3, yearly.size());
    }

    @Test
    void emptyDatabaseYieldsZeroedReports() {
        AppContext ctx = newContext();
        long owner = ctx.users.findFirst().id();
        DashboardSnapshot snapshot = ctx.reportService.buildDashboard(
                owner, PeriodKey.THIS_MONTH, LocalDate.now());
        assertEquals(0, snapshot.incomeTotal());
        assertEquals(0, snapshot.expenseTotal());
        assertEquals(0, snapshot.netAllTime());
        assertTrue(snapshot.expenseDistribution().isEmpty());
        assertTrue(snapshot.recentTransactions().isEmpty());
    }

    @Test
    void csvExportEscapesAndWritesBom() throws Exception {
        Path target = tempDir.resolve("export.csv");
        CsvExporter.export(target, ",", List.of(
                new String[]{"Tên", "Ghi chú", "Số tiền"},
                new String[]{"a\"b", "x,y", "plain"}));

        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"), "UTF-8 BOM required for Excel VN");
        assertTrue(content.contains("Tên,Ghi chú,Số tiền"));
        assertTrue(content.contains("\"a\"\"b\",\"x,y\",plain"));
    }

    @Test
    void csvSemicolonDelimiterWorks() throws Exception {
        Path target = tempDir.resolve("semi.csv");
        CsvExporter.export(target, ";", List.of(
                new String[]{"a", "b", "c"},
                new String[]{"1", "2", "3"}));
        String content = Files.readString(target, StandardCharsets.UTF_8)
                .substring(1); // drop BOM
        assertTrue(content.contains("a;b;c\r\n1;2;3\r\n"));
    }
}