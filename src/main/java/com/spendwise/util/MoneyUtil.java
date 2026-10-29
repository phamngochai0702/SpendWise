package com.spendwise.util;

import com.spendwise.service.ValidationException;

/**
 * Money formatting and parsing utilities. All amounts are {@code long} whole
 * VND units, so there are never floating point rounding errors. The grouping
 * separator is generated manually ("1.234.567") instead of relying on the
 * machine locale.
 */
public final class MoneyUtil {

    /** Upper bound for a single amount (~1 nghìn tỷ VND). */
    public static final long MAX_AMOUNT = 999_999_999_999L;

    private MoneyUtil() {
    }

    /** "1.234.567 ₫" style formatting; negative amounts get a "-" prefix. */
    public static String format(long amount, String symbol) {
        String grouped = group(Math.abs(amount));
        String sign = amount < 0 ? "-" : "";
        String suffix = (symbol == null || symbol.isBlank()) ? "" : " " + symbol.trim();
        return sign + grouped + suffix;
    }

    public static String formatSigned(long amount, String symbol) {
        return amount > 0 ? "+" + format(amount, symbol)
                : amount < 0 ? format(amount, symbol)
                : format(0, symbol);
    }

    /** Short label for chart axes: "1,2 tỷ", "350 tr", "12,5 k". */
    public static String compact(long amount) {
        double a = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (a >= 1_000_000_000L) {
            return sign + trimValue(a / 1_000_000_000.0) + " tỷ";
        }
        if (a >= 1_000_000L) {
            return sign + trimValue(a / 1_000_000.0) + " tr";
        }
        if (a >= 1_000L) {
            return sign + trimValue(a / 1_000.0) + " k";
        }
        return sign + Long.toString((long) a);
    }

    private static String trimValue(double value) {
        String s;
        if (value >= 100) {
            s = String.format("%.0f", value);
        } else if (value >= 10) {
            s = String.format("%.1f", value);
        } else {
            s = String.format("%.2f", value);
        }
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s.replace('.', ','); // Vietnamese decimal comma
    }

    /** Groups digits with "." every three positions: 1234567 -> "1.234.567". */
    public static String group(long positive) {
        String digits = Long.toString(Math.abs(positive));
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
        int length = digits.length();
        for (int i = 0; i < length; i++) {
            out.append(digits.charAt(i));
            int remaining = length - i - 1;
            if (remaining > 0 && remaining % 3 == 0) {
                out.append('.');
            }
        }
        return out.toString();
    }

    /**
     * Parses typed money input: "1.234.567", "1,234,567", "1 234 567" and
     * "1234567" are all accepted. Empty input yields 0 (callers decide whether
     * 0 is valid). Anything else raises a {@link ValidationException}.
     */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        StringBuilder digits = new StringBuilder(text.length());
        for (char c : text.trim().toCharArray()) {
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == '.' || c == ',' || c == ' ') {
                // thousand separator - ignored
            } else {
                throw new ValidationException("Số tiền chỉ được gồm chữ số và dấu phân cách nghìn.");
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (NumberFormatException ex) {
            throw new ValidationException("Số tiền quá lớn hoặc không hợp lệ.");
        }
    }
}
