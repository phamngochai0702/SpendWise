package com.spendwise.util;

import com.spendwise.service.ValidationException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilTest {

    @Test
    void formatsGroupedVndStyle() {
        assertEquals("0 ₫", MoneyUtil.format(0, "₫"));
        assertEquals("1.234.567 ₫", MoneyUtil.format(1_234_567, "₫"));
        assertEquals("-9.500 VND", MoneyUtil.format(-9_500, "VND"));
        assertEquals("1.000", MoneyUtil.format(1_000, null));
        assertEquals("+2.000.000 đ", MoneyUtil.formatSigned(2_000_000, "đ"));
        assertEquals("-2.000.000 đ", MoneyUtil.formatSigned(-2_000_000, "đ"));
    }

    @Test
    void parsesAllThousandSeparatorStyles() {
        assertEquals(1_234_567, MoneyUtil.parse("1.234.567"));
        assertEquals(1_234_567, MoneyUtil.parse("1,234,567"));
        assertEquals(1_234_567, MoneyUtil.parse("1 234 567"));
        assertEquals(1_234_567, MoneyUtil.parse("1234567"));
        assertEquals(0, MoneyUtil.parse(""));
        assertEquals(0, MoneyUtil.parse("   "));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThrows(ValidationException.class, () -> MoneyUtil.parse("12abc"));
        assertThrows(ValidationException.class, () -> MoneyUtil.parse("-500"));
        assertThrows(ValidationException.class,
                () -> MoneyUtil.parse("99999999999999999999999999"));
        assertThrows(ValidationException.class, () -> MoneyUtil.parse("5.000 VND"));
    }

    @Test
    void compactAxisLabels() {
        assertEquals("1,5 tỷ", MoneyUtil.compact(1_500_000_000L));
        assertEquals("2,5 tr", MoneyUtil.compact(2_500_000L));
        assertEquals("12,5 k", MoneyUtil.compact(12_500L));
        assertEquals("950", MoneyUtil.compact(950));
        assertEquals("-3,1 tr", MoneyUtil.compact(-3_100_000L));
    }
}