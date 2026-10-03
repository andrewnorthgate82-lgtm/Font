package com.promptsaz.app.util

/**
 * Jalali (Solar Hijri) calendar utilities — self-written, dependency-free.
 *
 * The conversion math is a Kotlin port of the well-known jalaali-js algorithms
 * (based on the 33-year cycle break points used in the Iranian astronomical
 * calendar). All functions use truncated integer division/remainder that
 * matches the original JavaScript semantics.
 *
 * Dates are handled as Julian Day Numbers (JDN, integer days) and epoch
 * milliseconds — no java.time, so no core library desugaring is required on
 * minSdk 24.
 */
object JalaliCalendar {

    /** Jalali years covered by the break-point table (33-year cycle data). */
    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060,
        2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    val MONTH_NAMES_FA = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** A specific Jalali date. */
    data class JalaliDate(val year: Int, val month: Int, val dayOfMonth: Int) {
        val monthNameFa: String get() = MONTH_NAMES_FA[month - 1]
    }

    /** A specific Gregorian date. */
    data class GregorianDate(val year: Int, val month: Int, val dayOfMonth: Int)

    /** True when the given Jalali year is a leap year (Esfand has 30 days). */
    fun isLeapYear(jalaliYear: Int): Boolean = jalCal(jalaliYear).leap == 0

    /** Number of days in a Jalali month (1..12) for the given year. */
    fun daysInMonth(jalaliYear: Int, jalaliMonth: Int): Int = when {
        jalaliMonth <= 6 -> 31
        jalaliMonth <= 11 -> 30
        isLeapYear(jalaliYear) -> 30
        else -> 29
    }

    /** Converts a Gregorian date to a Julian Day Number. */
    fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    /** Converts a Julian Day Number to a Gregorian date. */
    fun jdnToGregorian(jdn: Int): GregorianDate {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return GregorianDate(gy, gm, gd)
    }

    /** Converts a Jalali date to a Julian Day Number. */
    fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return gregorianToJdn(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    /** Converts a Julian Day Number to a Jalali date. */
    fun jdnToJalali(jdn: Int): JalaliDate {
        val gy = jdnToGregorian(jdn).year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = gregorianToJdn(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                val jm = 1 + div(k, 31)
                val jd = mod(k, 31) + 1
                return JalaliDate(jy, jm, jd)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        val jm = 7 + div(k, 30)
        val jd = mod(k, 30) + 1
        return JalaliDate(jy, jm, jd)
    }

    /** Epoch milliseconds -> Jalali date (days rounded toward negative infinity). */
    fun fromEpochMillis(epochMillis: Long): JalaliDate =
        jdnToJalali(epochDayToJdn(Math.floorDiv(epochMillis, MILLIS_PER_DAY)))

    /** Jalali date -> epoch milliseconds at the start of that day (UTC). */
    fun toEpochMillis(date: JalaliDate): Long =
        jdnToEpochDay(jalaliToJdn(date.year, date.month, date.dayOfMonth)) * MILLIS_PER_DAY

    /** Short numeric format with Persian digits: «۱۴۰۳/۰۲/۱۵». */
    fun formatShort(date: JalaliDate): String =
        "%04d/%02d/%02d".format(date.year, date.month, date.dayOfMonth).toPersianDigits()

    /** Long format with Persian digits: «۱۵ خرداد ۱۴۰۳». */
    fun formatLong(date: JalaliDate): String =
        "${date.dayOfMonth.toPersianDigits()} ${date.monthNameFa} ${date.year.toPersianDigits()}"

    // --- internals -------------------------------------------------------------

    private const val MILLIS_PER_DAY = 86_400_000L

    /** The Unix epoch day (1970-01-01) expressed in this JDN scale. */
    private val EPOCH_JDN = gregorianToJdn(1970, 1, 1)

    private fun epochDayToJdn(epochDay: Long): Int = epochDay.toInt() + EPOCH_JDN

    private fun jdnToEpochDay(jdn: Int): Long = jdn.toLong() - EPOCH_JDN

    /**
     * Core leap-year computation for the Jalali calendar.
     * Returns (leap, gy, march) where leap==0 means [jy] is a leap year,
     * gy is the corresponding Gregorian year and march is the Gregorian
     * day of March on which Nowruz (1 Farvardin) falls.
     */
    private fun jalCal(jy: Int): JcalResult {
        require(jy >= BREAKS.first() && jy < BREAKS.last()) { "Jalali year out of range: $jy" }
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1

        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JcalResult(leap, gy, march)
    }

    private data class JcalResult(val leap: Int, val gy: Int, val march: Int)

    /** Truncated division (matches JavaScript `~~(a/b)` in the reference algorithm). */
    private fun div(a: Int, b: Int): Int = a / b

    /** Truncated remainder (matches JavaScript `a - ~~(a/b)*b`). */
    private fun mod(a: Int, b: Int): Int = a % b
}
