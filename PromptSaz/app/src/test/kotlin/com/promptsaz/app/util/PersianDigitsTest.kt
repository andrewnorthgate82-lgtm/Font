package com.promptsaz.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDigitsTest {

    @Test
    fun `converts latin digits to persian`() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", "0123456789".toPersianDigits())
    }

    @Test
    fun `converts persian digits back to latin`() {
        assertEquals("0123456789", "۰۱۲۳۴۵۶۷۸۹".toEnglishDigits())
    }

    @Test
    fun `converts arabic digits to latin`() {
        assertEquals("0123", "٠١٢٣".toEnglishDigits())
    }

    @Test
    fun `leaves letters untouched and converts only digits`() {
        assertEquals("Room ۲۴", "Room 24".toPersianDigits())
    }

    @Test
    fun `mixed persian text keeps its letters`() {
        val input = "۱۴۰۳/۰۲/۱۵ و 3 روز پیش"
        val output = input.toPersianDigits()
        assertEquals("۱۴۰۳/۰۲/۱۵ و ۳ روز پیش", output)
    }

    @Test
    fun `int and long formatting`() {
        assertEquals("۴۲", 42.toPersianDigits())
        assertEquals("۱۹۷۰", 1970L.toPersianDigits())
    }

    @Test
    fun `round trip preserves text`() {
        val original = "abc123def456"
        val roundTrip = original.toPersianDigits().toEnglishDigits()
        assertEquals(original, roundTrip)
    }
}
