package com.promptsaz.app.util

/**
 * Persian relative-time formatting for archive timestamps
 * («۳ ساعت پیش»، «۲ روز پیش»، falls back to a Jalali date for anything older).
 */
object TimeAgo {

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * Formats the distance between [timestamp] and [now].
     * Future timestamps (clock skew) are clamped to «لحظاتی پیش».
     */
    fun format(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        return when {
            diff < 45 * SECOND -> "لحظاتی پیش"
            diff < 60 * MINUTE -> "${(diff / MINUTE).toPersianDigits()} دقیقه پیش"
            diff < 24 * HOUR -> "${(diff / HOUR).toPersianDigits()} ساعت پیش"
            diff < 7 * DAY -> "${(diff / DAY).toPersianDigits()} روز پیش"
            else -> JalaliCalendar.formatShort(JalaliCalendar.fromEpochMillis(timestamp))
        }
    }
}
