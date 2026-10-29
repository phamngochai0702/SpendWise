package com.spendwise.util;

import com.spendwise.repository.DataAccessException;
import com.spendwise.service.ValidationException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateUtilTest {

    @Test
    void parsesDisplayPatternStrictly() {
        assertEquals(LocalDate.of(2026, 3, 14),
                DateUtil.parse("14/03/2026", "dd/MM/yyyy", "Ngày không hợp lệ."));
    }

    @Test
    void rejectsGarbageDates() {
        assertThrows(ValidationException.class,
                () -> DateUtil.parse("ngày nào đó", "dd/MM/yyyy", "Ngày không hợp lệ."));
        assertThrows(ValidationException.class,
                () -> DateUtil.parse("2026-13-99", "yyyy-MM-dd", "Ngày không hợp lệ."));
        assertThrows(ValidationException.class,
                () -> DateUtil.parse("", "dd/MM/yyyy", "Ngày không hợp lệ."));
    }

    @Test
    void monthLabels() {
        assertEquals("Tháng 3/2026", DateUtil.monthLabel("2026-03"));
        assertEquals("2026/03", DateUtil.monthLabel("2026/03"));
        assertEquals("T3/26", DateUtil.monthShortLabel(java.time.YearMonth.of(2026, 3)));
        assertEquals("T12/99", DateUtil.monthShortLabel(java.time.YearMonth.of(1999, 12)));
    }

    @Test
    void isoStorageRoundTrip() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        assertEquals(date, DateUtil.parseIsoDate(DateUtil.toIsoDate(date)));

        LocalDateTime moment = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        assertEquals(moment, DateUtil.parseIsoDateTime("2026-01-02 03:04:05"));
        assertNull(DateUtil.parseIsoDateTime(null));
        assertNull(DateUtil.parseIsoDateTime(""));

        assertThrows(DataAccessException.class, () -> DateUtil.parseIsoDate("không-phải-ngày"));
    }

    @Test
    void formatRangeUsesGivenPattern() {
        assertEquals("01/03/2026 – 31/03/2026", DateUtil.formatRange(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "dd/MM/yyyy"));
    }
}