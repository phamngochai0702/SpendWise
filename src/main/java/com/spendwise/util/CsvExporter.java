package com.spendwise.util;

import com.spendwise.service.ValidationException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CSV export used by the transactions and report screens. Rows are written as
 * UTF-8 with a BOM so Microsoft Excel renders Vietnamese diacritics correctly,
 * and every ambiguous field is quoted/escaped per RFC 4180.
 */
public final class CsvExporter {

    private static final char BOM = (char) 0xFEFF;

    private CsvExporter() {
    }

    /**
     * Writes rows to {@code file} using the given delimiter.
     *
     * @return the written file (same as {@code file})
     * @throws IOException when the file cannot be written
     */
    public static Path export(Path file, String delimiter, List<String[]> rows) throws IOException {
        String delim = (delimiter == null || delimiter.isBlank()) ? "," : delimiter;
        if (rows == null) {
            rows = List.of();
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(BOM);
            for (String[] row : rows) {
                writer.write(toLine(row, delim));
                writer.write("\r\n");
            }
        }
        return file;
    }

    private static String toLine(String[] row, String delimiter) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                line.append(delimiter);
            }
            line.append(escape(row[i], delimiter));
        }
        return line.toString();
    }

    static String escape(String value, String delimiter) {
        String text = value == null ? "" : value;
        boolean mustQuote = text.contains(delimiter) || text.contains("\"")
                || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return mustQuote ? '"' + escaped + '"' : escaped;
    }
}
