package com.promptsaz.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `moments ago`() {
        assertEquals("لحظاتی پیش", TimeAgo.format(now - 30_000L, now))
    }

    @Test
    fun `minutes ago uses persian digits`() {
        assertEquals("۵ دقیقه پیش", TimeAgo.format(now - 5 * 60_000L, now))
    }

    @Test
    fun `hours ago uses persian digits`() {
        assertEquals("۳ ساعت پیش", TimeAgo.format(now - 3 * 3_600_000L, now))
    }

    @Test
    fun `days ago uses persian digits`() {
        assertEquals("۲ روز پیش", TimeAgo.format(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `older than a week shows jalali date`() {
        val stamp = now - 45L * 86_400_000L
        val expected = JalaliCalendar.formatShort(JalaliCalendar.fromEpochMillis(stamp))
        assertEquals(expected, TimeAgo.format(stamp, now))
    }

    @Test
    fun `future timestamps are clamped to moments ago`() {
        assertEquals("لحظاتی پیش", TimeAgo.format(now + 5 * 60_000L, now))
    }

    @Test
    fun `boundary just under a minute`() {
        assertEquals("۵۹ دقیقه پیش", TimeAgo.format(now - 59 * 60_000L, now))
    }

    @Test
    fun `boundary just under a day`() {
        assertEquals("۲۳ ساعت پیش", TimeAgo.format(now - 23 * 3_600_000L, now))
    }
}
