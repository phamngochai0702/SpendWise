package com.spendwise.service;

import com.spendwise.model.Category;
import com.spendwise.model.CategoryTotal;
import com.spendwise.model.TransactionType;
import com.spendwise.repository.BudgetRepository;
import com.spendwise.repository.CategoryRepository;
import com.spendwise.repository.DatabaseManager;
import com.spendwise.repository.TransactionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business rules for categories of one authenticated user: creation/editing
 * with uniqueness checks, default seeding on registration and safe deletion
 * (reassignment of transactions + budget clean-up in one DB transaction).
 */
public final class CategoryService {

    public static final int MAX_NAME_LENGTH = 100;

    /** Seeded for every new account so the app is usable immediately. */
    private static final String[][] DEFAULT_CATEGORIES = {
            {"Ăn uống", "EXPENSE", "#F59E0B"},
            {"Di chuyển", "EXPENSE", "#0EA5A4"},
            {"Mua sắm", "EXPENSE", "#6366F1"},
            {"Hóa đơn", "EXPENSE", "#94A3B8"},
            {"Giải trí", "EXPENSE", "#EC4899"},
            {"Sức khoẻ", "EXPENSE", "#84CC16"},
            {"Giáo dục", "EXPENSE", "#A855F7"},
            {"Nhà ở", "EXPENSE", "#F97316"},
            {"Lương", "INCOME", "#15803D"},
            {"Thưởng", "INCOME", "#10B981"},
            {"Đầu tư", "INCOME", "#0EA5E9"},
            {"Thu nhập khác", "INCOME", "#6366F1"},
    };

    private final DatabaseManager database;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;

    public CategoryService(DatabaseManager database, CategoryRepository categories,
                           TransactionRepository transactions, BudgetRepository budgets) {
        this.database = database;
        this.categories = categories;
        this.transactions = transactions;
        this.budgets = budgets;
    }

    public Category add(long ownerId, String name, TransactionType type, String color) {
        String clean = validateName(name);
        if (type == null) {
            throw new ValidationException("Vui lòng chọn loại danh mục.");
        }
        ensureUnique(ownerId, clean, type, -1);
        String safeColor = (color == null || color.isBlank()) ? "#0F766E" : color.trim();
        return categories.insert(ownerId, clean, type, safeColor);
    }

    /** Renames/recolors a category; its type never changes (protected historical data). */
    public Category update(long ownerId, long categoryId, String newName, String newColor) {
        Category stored = categories.findById(ownerId, categoryId);
        if (stored == null) {
            throw new ValidationException("Danh mục không tồn tại hoặc đã bị xoá.");
        }
        String clean = validateName(newName);
        ensureUnique(ownerId, clean, stored.type(), stored.id());
        stored.rename(clean);
        stored.setColor(newColor);
        categories.update(ownerId, stored);
        return stored;
    }

    /**
     * Deletes a category. When the category still owns transactions the caller
     * must supply a replacement of the same type; the move plus budget removal
     * happens in a single transaction.
     */
    public void delete(long ownerId, long categoryId, Long reassignToCategoryId) {
        Category category = categories.findById(ownerId, categoryId);
        if (category == null) {
            return;
        }
        long transactionCount = transactions.countByCategory(ownerId, categoryId);
        long budgetCount = budgets.countByCategory(ownerId, categoryId);
        if (transactionCount > 0) {
            if (reassignToCategoryId == null) {
                throw new ValidationException("Danh mục đang có " + transactionCount
                        + " giao dịch. Hãy chuyển giao dịch sang danh mục khác trước khi xoá.");
            }
            Category target = categories.findById(ownerId, reassignToCategoryId);
            if (target == null) {
                throw new ValidationException("Danh mục đích không tồn tại.");
            }
            if (target.id() == category.id()) {
                throw new ValidationException("Danh mục đích phải khác danh mục cần xoá.");
            }
            if (target.type() != category.type()) {
                throw new ValidationException("Danh mục đích phải cùng loại ('"
                        + category.type().displayName() + "').");
            }
        }
        long finalTransactionCount = transactionCount;
        database.inTransaction(() -> {
            if (finalTransactionCount > 0) {
                transactions.reassignCategory(ownerId, categoryId, reassignToCategoryId);
            }
            if (budgetCount > 0) {
                budgets.deleteByCategory(ownerId, categoryId);
            }
            categories.deleteById(ownerId, categoryId);
        });
    }

    /** Seeds the 12 default categories for a freshly registered account. */
    public void seedDefaultsForOwner(long ownerId) {
        if (categories.countAll(ownerId) > 0) {
            return;
        }
        for (String[] seed : DEFAULT_CATEGORIES) {
            categories.insert(ownerId, seed[0], TransactionType.valueOf(seed[1]), seed[2]);
        }
    }

    public long transactionCount(long ownerId, long categoryId) {
        return transactions.countByCategory(ownerId, categoryId);
    }

    public long budgetCount(long ownerId, long categoryId) {
        return budgets.countByCategory(ownerId, categoryId);
    }

    /**
     * Every category of {@code type} joined with its aggregated totals in the
     * range. When {@code includeEmpty} is false only used categories are kept.
     * {@code type == null} combines income and expense totals.
     */
    public List<CategoryTotal> withTotals(long ownerId, TransactionType type,
                                          LocalDate from, LocalDate to, boolean includeEmpty) {
        Map<Long, long[]> totals = new HashMap<>();
        if (type == null) {
            totals.putAll(transactions.totalsByCategory(ownerId, TransactionType.INCOME, from, to));
            for (Map.Entry<Long, long[]> entry : transactions
                    .totalsByCategory(ownerId, TransactionType.EXPENSE, from, to).entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(),
                        (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
            }
        } else {
            totals.putAll(transactions.totalsByCategory(ownerId, type, from, to));
        }
        List<Category> categoryList = type == null
                ? categories.findAll(ownerId)
                : categories.findByType(ownerId, type);
        List<CategoryTotal> result = new ArrayList<>();
        for (Category category : categoryList) {
            long[] pair = totals.getOrDefault(category.id(), new long[2]);
            if (!includeEmpty && pair[0] == 0) {
                continue;
            }
            result.add(new CategoryTotal(category.id(), category.name(), category.color(),
                    category.type(), pair[0], pair[1]));
        }
        return result;
    }

    /** Ordered for tables/legends: biggest amounts first, then by name. */
    public List<CategoryTotal> totalsForScreen(long ownerId, TransactionType type) {
        List<CategoryTotal> rows = withTotals(ownerId, type, null, null, true);
        rows.sort((a, b) -> {
            int byAmount = Long.compare(b.amount(), a.amount());
            return byAmount != 0 ? byAmount : a.name().compareToIgnoreCase(b.name());
        });
        return rows;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Tên danh mục không được để trống.");
        }
        String clean = name.trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Tên danh mục tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        return clean;
    }

    private void ensureUnique(long ownerId, String name, TransactionType type, long excludeId) {
        Category existing = categories.findByNameAndType(ownerId, name, type);
        if (existing != null && existing.id() != excludeId) {
            throw new ValidationException("Danh mục '" + name + "' đã tồn tại trong nhóm "
                    + type.displayName() + ".");
        }
    }
}