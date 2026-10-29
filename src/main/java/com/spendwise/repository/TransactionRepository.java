package com.spendwise.repository;

import com.spendwise.model.Transaction;
import com.spendwise.model.TransactionFilter;
import com.spendwise.model.TransactionType;
import com.spendwise.util.DateUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC data access for the {@code transactions} table, scoped per owning user.
 */
public final class TransactionRepository {

    private static final String BASE_SELECT = """
            SELECT t.id, t.type, t.amount, t.category_id,
                   c.name AS category_name, c.color AS category_color,
                   t.date, t.note, t.created_at, t.updated_at
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            """;

    private final DatabaseManager database;

    public TransactionRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Inserts a new transaction for {@code ownerId}; returns the persisted row. */
    public Transaction insert(long ownerId, Transaction transaction) {
        LocalDateTime now = LocalDateTime.now();
        long id = database.insertWithId("""
                        INSERT INTO transactions (owner_id, type, amount, category_id, date, note,
                                                   created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ownerId, transaction.type().dbValue(), transaction.amount(), transaction.categoryId(),
                transaction.date(), transaction.note(), now, now);
        return findById(ownerId, id);
    }

    public Transaction update(long ownerId, Transaction transaction) {
        database.update("""
                        UPDATE transactions
                        SET type = ?, amount = ?, category_id = ?, date = ?, note = ?, updated_at = ?
                        WHERE id = ? AND owner_id = ?
                        """,
                transaction.type().dbValue(), transaction.amount(), transaction.categoryId(),
                transaction.date(), transaction.note(), LocalDateTime.now(),
                transaction.id(), ownerId);
        return findById(ownerId, transaction.id());
    }

    public boolean deleteById(long ownerId, long id) {
        return database.update("DELETE FROM transactions WHERE id = ? AND owner_id = ?",
                id, ownerId) > 0;
    }

    public Transaction findById(long ownerId, long id) {
        return database.queryOne(BASE_SELECT + " WHERE t.id = ? AND t.owner_id = ?",
                TransactionRepository::mapRow, id, ownerId);
    }

    public long countAll(long ownerId) {
        return database.queryLong("SELECT COUNT(*) FROM transactions WHERE owner_id = ?", ownerId);
    }

    public long countByCategory(long ownerId, long categoryId) {
        return database.queryLong(
                "SELECT COUNT(*) FROM transactions WHERE owner_id = ? AND category_id = ?",
                ownerId, categoryId);
    }

    public List<Transaction> search(long ownerId, TransactionFilter filter) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        sql.append(" WHERE t.owner_id = ?");
        params.add(ownerId);
        if (filter.type() != null) {
            sql.append(" AND t.type = ?");
            params.add(filter.type().dbValue());
        }
        if (filter.categoryId() != null) {
            sql.append(" AND t.category_id = ?");
            params.add(filter.categoryId());
        }
        if (filter.from() != null) {
            sql.append(" AND t.date >= ?");
            params.add(filter.from());
        }
        if (filter.to() != null) {
            sql.append(" AND t.date <= ?");
            params.add(filter.to());
        }
        if (filter.minAmount() != null) {
            sql.append(" AND t.amount >= ?");
            params.add(filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            sql.append(" AND t.amount <= ?");
            params.add(filter.maxAmount());
        }
        if (filter.keyword() != null) {
            sql.append(" AND (t.note LIKE ? OR c.name LIKE ?)");
            String like = "%" + filter.keyword() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY ").append(orderClause(filter.sort()));
        return database.query(sql.toString(), TransactionRepository::mapRow, params.toArray());
    }

    private static String orderClause(TransactionFilter.Sort sort) {
        return switch (sort == null ? TransactionFilter.Sort.DATE_DESC : sort) {
            case DATE_DESC -> "t.date DESC, t.id DESC";
            case DATE_ASC -> "t.date ASC, t.id ASC";
            case AMOUNT_DESC -> "t.amount DESC, t.date DESC, t.id DESC";
            case AMOUNT_ASC -> "t.amount ASC, t.date DESC, t.id ASC";
            case CATEGORY -> "c.name COLLATE NOCASE ASC, t.date DESC, t.id DESC";
        };
    }

    public List<Transaction> recent(long ownerId, int limit) {
        return database.query(BASE_SELECT
                        + " WHERE t.owner_id = ? ORDER BY t.date DESC, t.id DESC LIMIT ?",
                TransactionRepository::mapRow, ownerId, limit);
    }

    public List<Transaction> byCategory(long ownerId, long categoryId) {
        return database.query(BASE_SELECT
                        + " WHERE t.category_id = ? AND t.owner_id = ? ORDER BY t.date DESC, t.id DESC",
                TransactionRepository::mapRow, categoryId, ownerId);
    }

    /** Moves all transactions of one category to another (same-type) category. */
    public long reassignCategory(long ownerId, long fromCategoryId, long toCategoryId) {
        return database.update(
                "UPDATE transactions SET category_id = ? WHERE category_id = ? AND owner_id = ?",
                toCategoryId, fromCategoryId, ownerId);
    }

    public long sumByTypeBetween(long ownerId, TransactionType type,
                                 LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = ? AND owner_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(type.dbValue());
        params.add(ownerId);
        if (from != null) {
            sql.append(" AND date >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND date <= ?");
            params.add(to);
        }
        return database.queryLong(sql.toString(), params.toArray());
    }

    public long sumAllTimeByType(long ownerId, TransactionType type) {
        return database.queryLong(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = ? AND owner_id = ?",
                type.dbValue(), ownerId);
    }

    /** Real expense total of one category inside an inclusive date range. */
    public long sumExpenseForCategory(long ownerId, long categoryId,
                                      LocalDate from, LocalDate to) {
        return database.queryLong("""
                        SELECT COALESCE(SUM(amount), 0) FROM transactions
                        WHERE type = 'EXPENSE' AND category_id = ? AND owner_id = ?
                          AND date BETWEEN ? AND ?
                        """,
                categoryId, ownerId, from, to);
    }

    /** categoryId -> [transactionCount, amountSum] for one type and optional range. */
    public Map<Long, long[]> totalsByCategory(long ownerId, TransactionType type,
                                              LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT category_id, COUNT(*) AS cnt, SUM(amount) AS total
                FROM transactions WHERE type = ? AND owner_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(type.dbValue());
        params.add(ownerId);
        if (from != null) {
            sql.append(" AND date >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND date <= ?");
            params.add(to);
        }
        sql.append(" GROUP BY category_id");
        Map<Long, long[]> result = new LinkedHashMap<>();
        for (long[] row : database.query(sql.toString(),
                rs -> new long[]{rs.getLong("category_id"), rs.getLong("cnt"), rs.getLong("total")},
                params.toArray())) {
            result.put(row[0], new long[]{row[1], row[2]});
        }
        return result;
    }

    /** "yyyy-MM" -> [income, expense] inside the inclusive range. */
    public Map<String, long[]> groupByYearMonth(long ownerId, LocalDate from, LocalDate to) {
        return grouped(ownerId, from, to, "substr(t.date, 1, 7)");
    }

    /** ISO day -> [income, expense] inside the inclusive range. */
    public Map<String, long[]> groupByDay(long ownerId, LocalDate from, LocalDate to) {
        return grouped(ownerId, from, to, "t.date");
    }

    /** Year -> [income, expense] inside the inclusive range. */
    public Map<String, long[]> groupByYear(long ownerId, LocalDate from, LocalDate to) {
        return grouped(ownerId, from, to, "substr(t.date, 1, 4)");
    }

    private Map<String, long[]> grouped(long ownerId, LocalDate from, LocalDate to,
                                        String bucketExpression) {
        Map<String, long[]> result = new LinkedHashMap<>();
        List<Object[]> rows = database.query(
                "SELECT " + bucketExpression + " AS bucket, t.type, SUM(t.amount) AS total "
                        + "FROM transactions t WHERE t.owner_id = ? AND t.date BETWEEN ? AND ? "
                        + "GROUP BY bucket, t.type ORDER BY bucket",
                rs -> {
                    Object[] row = new Object[3];
                    row[0] = rs.getString("bucket");
                    row[1] = TransactionType.fromDb(rs.getString("type"));
                    row[2] = rs.getLong("total");
                    return row;
                },
                ownerId, from, to);
        for (Object[] row : rows) {
            String bucket = (String) row[0];
            TransactionType type = (TransactionType) row[1];
            long total = (Long) row[2];
            long[] pair = result.computeIfAbsent(bucket, key -> new long[2]);
            if (type == TransactionType.INCOME) {
                pair[0] += total;
            } else {
                pair[1] += total;
            }
        }
        return result;
    }

    private static Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                TransactionType.fromDb(rs.getString("type")),
                rs.getLong("amount"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("category_color"),
                DateUtil.parseIsoDate(rs.getString("date")),
                rs.getString("note"),
                DateUtil.parseIsoDateTime(rs.getString("created_at")),
                DateUtil.parseIsoDateTime(rs.getString("updated_at")));
    }
}