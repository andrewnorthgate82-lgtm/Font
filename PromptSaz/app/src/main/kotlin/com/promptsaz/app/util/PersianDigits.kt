package com.promptsaz.app.util

/**
 * Converts between Latin (0-9) and Persian (۰-۹) digits.
 *
 * All user-facing numbers in the app go through [toPersianDigits] so the UI
 * never shows mixed digit systems. Internal/storage layers keep Latin digits.
 */

private const val LATIN_DIGITS = "0123456789"
private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Maps every Latin digit in [this] to its Persian counterpart. */
fun String.toPersianDigits(): String = buildString(length) {
    for (ch in this@toPersianDigits) {
        val index = LATIN_DIGITS.indexOf(ch)
        append(if (index >= 0) PERSIAN_DIGITS[index] else ch)
    }
}

/** Maps every Persian (and Arabic) digit in [this] to its Latin counterpart. */
fun String.toEnglishDigits(): String = buildString(length) {
    for (ch in this@toEnglishDigits) {
        append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            },
        )
    }
}

/** Formats an integer with Persian digits. */
fun Int.toPersianDigits(): String = toString().toPersianDigits()

/** Formats a long with Persian digits. */
fun Long.toPersianDigits(): String = toString().toPersianDigits()
