package com.spendwise.util;

import com.spendwise.service.ValidationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Date helpers: ISO storage format, Vietnamese display labels, strict parsing. */
public final class DateUtil {

    /** Pattern used for date columns in SQLite ("YYYY-MM-DD" sorts correctly as text). */
    public static final String ISO_DATE = "yyyy-MM-dd";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();

    private DateUtil() {
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    // ---------- storage ----------

    public static String toIsoDate(LocalDate date) {
        return date.toString();
    }

    public static String toIsoDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME);
    }

    public static LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            throw new com.spendwise.repository.DataAccessException("Dữ liệu ngày không hợp lệ trong cơ sở dữ liệu.");
        }
        try {
            return LocalDate.parse(value.trim(), ISO);
        } catch (DateTimeParseException ex) {
            throw new com.spendwise.repository.DataAccessException("Dữ liệu ngày không hợp lệ trong cơ sở dữ liệu: " + value);
        }
    }

    public static LocalDateTime parseIsoDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    // ---------- display ----------

    public static DateTimeFormatter formatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    public static String format(LocalDate date, String pattern) {
        return date.format(formatter(pattern));
    }

    public static String formatRange(LocalDate from, LocalDate to, String pattern) {
        return format(from, pattern) + " – " + format(to, pattern);
    }

    /** Strictly parses a date typed by the user; throws a Vietnamese error otherwise. */
    public static LocalDate parse(String text, String pattern, String errorMessage) {
        if (text == null || text.isBlank()) {
            throw new ValidationException(errorMessage);
        }
        try {
            return LocalDate.parse(text.trim(), formatter(pattern));
        } catch (DateTimeParseException ex) {
            throw new ValidationException(errorMessage);
        }
    }

    /** "2026-03" -> "Tháng 3/2026" */
    public static String monthLabel(String yearMonth) {
        String[] parts = yearMonth.split("-");
        if (parts.length != 2) {
            return yearMonth;
        }
        try {
            int month = Integer.parseInt(parts[1]);
            return "Tháng " + month + "/" + parts[0];
        } catch (NumberFormatException ex) {
            return yearMonth;
        }
    }

    /** Compact axis label: "T3/26" */
    public static String monthShortLabel(YearMonth month) {
        return "T" + month.getMonthValue() + "/" + String.format("%02d", month.getYear() % 100);
    }
}
