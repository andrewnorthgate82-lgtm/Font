package com.promptsaz.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliCalendarTest {

    // --- known anchor dates -----------------------------------------------------

    @Test
    fun `1 farvardin 1403 equals 20 march 2024`() {
        val jdn = JalaliCalendar.jalaliToJdn(1403, 1, 1)
        val g = JalaliCalendar.jdnToGregorian(jdn)
        assertEquals(2024, g.year)
        assertEquals(3, g.month)
        assertEquals(20, g.dayOfMonth)
    }

    @Test
    fun `1 farvardin 1404 equals 21 march 2025`() {
        val jdn = JalaliCalendar.jalaliToJdn(1404, 1, 1)
        val g = JalaliCalendar.jdnToGregorian(jdn)
        assertEquals(2025, g.year)
        assertEquals(3, g.month)
        assertEquals(21, g.dayOfMonth)
    }

    @Test
    fun `1 farvardin 1405 equals 21 march 2026`() {
        val jdn = JalaliCalendar.jalaliToJdn(1405, 1, 1)
        val g = JalaliCalendar.jdnToGregorian(jdn)
        assertEquals(2026, g.year)
        assertEquals(3, g.month)
        assertEquals(21, g.dayOfMonth)
    }

    @Test
    fun `1 farvardin 1400 equals 21 march 2021`() {
        val jdn = JalaliCalendar.jalaliToJdn(1400, 1, 1)
        val g = JalaliCalendar.jdnToGregorian(jdn)
        assertEquals(2021, g.year)
        assertEquals(3, g.month)
        assertEquals(21, g.dayOfMonth)
    }

    @Test
    fun `15 khordad 1403 equals 4 june 2024`() {
        val jdn = JalaliCalendar.jalaliToJdn(1403, 3, 15)
        val g = JalaliCalendar.jdnToGregorian(jdn)
        assertEquals(2024, g.year)
        assertEquals(6, g.month)
        assertEquals(4, g.dayOfMonth)
    }

    @Test
    fun `unix epoch equals 11 dey 1348`() {
        val date = JalaliCalendar.fromEpochMillis(0L)
        assertEquals(1348, date.year)
        assertEquals(10, date.month)
        assertEquals(11, date.dayOfMonth)
    }

    // --- leap years -------------------------------------------------------------

    @Test
    fun `leap years are detected correctly`() {
        assertTrue(JalaliCalendar.isLeapYear(1399))
        assertTrue(JalaliCalendar.isLeapYear(1403))
        assertTrue(JalaliCalendar.isLeapYear(1408))
        assertFalse(JalaliCalendar.isLeapYear(1400))
        assertFalse(JalaliCalendar.isLeapYear(1404))
        assertFalse(JalaliCalendar.isLeapYear(1405))
    }

    @Test
    fun `esfand has 30 days only in leap years`() {
        assertEquals(30, JalaliCalendar.daysInMonth(1403, 12))
        assertEquals(29, JalaliCalendar.daysInMonth(1404, 12))
    }

    @Test
    fun `first six months have 31 days`() {
        for (month in 1..6) {
            assertEquals(31, JalaliCalendar.daysInMonth(1404, month))
        }
    }

    // --- round trips ------------------------------------------------------------

    @Test
    fun `jalali to jdn and back round trips over a decade`() {
        // Walk every day of 1400..1409 (Persian) and check the round trip.
        var jdn = JalaliCalendar.jalaliToJdn(1400, 1, 1)
        val endJdn = JalaliCalendar.jalaliToJdn(1410, 1, 1)
        while (jdn < endJdn) {
            val date = JalaliCalendar.jdnToJalali(jdn)
            assertEquals(jdn, JalaliCalendar.jalaliToJdn(date.year, date.month, date.dayOfMonth))
            jdn++
        }
    }

    @Test
    fun `epoch millis round trip`() {
        val date = JalaliCalendar.fromEpochMillis(1_735_689_600_000L) // 2025-01-01
        val back = JalaliCalendar.toEpochMillis(date)
        assertEquals(1_735_689_600_000L, back)
    }

    @Test
    fun `year length is 365 or 366 days`() {
        for (year in 1400..1410) {
            val length = JalaliCalendar.jalaliToJdn(year + 1, 1, 1) -
                JalaliCalendar.jalaliToJdn(year, 1, 1)
            val expected = if (JalaliCalendar.isLeapYear(year)) 366 else 365
            assertEquals("year $year", expected, length)
        }
    }

    // --- formatting -------------------------------------------------------------

    @Test
    fun `short format uses persian digits`() {
        val date = JalaliCalendar.JalaliDate(1403, 2, 15)
        assertEquals("۱۴۰۳/۰۲/۱۵", JalaliCalendar.formatShort(date))
    }

    @Test
    fun `long format uses month name`() {
        val date = JalaliCalendar.JalaliDate(1403, 3, 15)
        assertEquals("۱۵ خرداد ۱۴۰۳", JalaliCalendar.formatLong(date))
    }

    @Test
    fun `month names are complete`() {
        assertEquals(12, JalaliCalendar.MONTH_NAMES_FA.size)
        assertEquals("فروردین", JalaliCalendar.MONTH_NAMES_FA[0])
        assertEquals("اسفند", JalaliCalendar.MONTH_NAMES_FA[11])
    }
}
