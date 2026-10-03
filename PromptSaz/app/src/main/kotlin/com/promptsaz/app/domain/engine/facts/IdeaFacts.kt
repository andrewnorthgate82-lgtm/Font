package com.promptsaz.app.domain.engine.facts

import com.promptsaz.app.util.toPersianDigits

/**
 * Hard facts extracted from the user's raw idea (names, dates, durations,
 * platforms, prices, …). They are rendered as a «اطلاعات قطعی» block in the
 * prompt context, drive the "never re-ask what the user already said" rule,
 * and suppress placeholder variables for information that is already known.
 *
 * Extractors are deliberately conservative: a missing fact is always better
 * than a wrong one.
 */
data class IdeaFacts(val entries: List<Fact>) {

    data class Fact(
        /** Persian label shown in the prompt, e.g. «مدرس». */
        val labelFa: String,
        /** Normalized Persian value, e.g. «محسن ابوطالبیان». */
        val valueFa: String,
        val category: FactCategory,
    )

    fun has(category: FactCategory): Boolean = entries.any { it.category == category }

    fun valueOf(category: FactCategory): String? = entries.firstOrNull { it.category == category }?.valueFa

    val isEmpty: Boolean get() = entries.isEmpty()
}

enum class FactCategory { SUBJECT, EVENT, INSTRUCTOR, DATE, DURATION, PLATFORM, PRICE, AUDIENCE }

object IdeaFactsExtractor {

    private val months = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val wordNumbers = mapOf(
        "یک" to "۱", "دو" to "۲", "سه" to "۳", "چهار" to "۴", "پنج" to "۵",
        "شش" to "۶", "هفت" to "۷", "هشت" to "۸", "نه" to "۹", "ده" to "۱۰",
        "دوازده" to "۱۲", "چهارده" to "۱۴", "پانزده" to "۱۵", "بیست" to "۲۰",
        "سی" to "۳۰", "چهل" to "۴۰", "پنجاه" to "۵۰",
    )

    private val platforms = listOf(
        "اینستاگرام", "تلگرام", "واتساپ", "لینکدین", "یوتیوب", "توییتر", "ایکس",
        "فیسبوک", "آپارات", "وبسایت", "وب‌سایت", "سایت", "وبلاگ", "پادکست",
    )

    private val dateRegex = Regex("[۰-۹0-9]{1,2}\\s*(${months.joinToString("|")})")
    private val durationRegex = Regex(
        "(?:به\\s*مدت|مدت)\\s*([۰-۹0-9]{1,3}|${wordNumbers.keys.joinToString("|")})\\s*(روز|هفته|ماه|ساعت|دقیقه|ثانیه)",
    )
    private val bareDurationRegex = Regex(
        "([۰-۹0-9]{1,3})\\s*(روز|هفته|ماه)ه?\\s",
    )
    private val instructorRegex =
        Regex("(?:مدرس|استاد|سخنران|برگزارکننده|گوینده)\\s*[:،]?\\s*([آ-ی ة‌]{3,40})")
    private val subjectRegex = Regex("موضوع\\s*(?:آموزشی|اصلی)?\\s*[:،]?\\s*([^،.؛:\\n\\r]{3,50})")
    private val audienceRegex = Regex("(?:مخاطب|مخاطبان)\\s*[:،]?\\s*([^،.؛:\\n\\r]{3,50})")
    private val brandRegex = Regex("(?:برند|شرکت)\\s*[:،]?\\s*([^،.؛:\\n\\r]{2,30})")
    private val eventRegex =
        Regex("(دوره|وبینار|کارگاه|سمینار|کلاس|همایش|جلسه|رویداد)(?:\\s+(آموزشی|تخصصی|آنلاین|حضوری|معرفی))?")

    fun extract(idea: String): IdeaFacts {
        val text = idea
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace(Regex("\\s+"), " ")
            .trim()
        val facts = mutableListOf<IdeaFacts.Fact>()

        eventRegex.find(text)?.let { match ->
            val phrase = (match.groupValues[1] + " " + match.groupValues[2]).trim()
            facts += IdeaFacts.Fact("نوع رویداد", phrase, FactCategory.EVENT)
        }
        subjectRegex.find(text)?.let { match ->
            facts += IdeaFacts.Fact("موضوع", clean(match.groupValues[1]), FactCategory.SUBJECT)
        }
        instructorRegex.find(text)?.let { match ->
            facts += IdeaFacts.Fact("مدرس", clean(match.groupValues[1]), FactCategory.INSTRUCTOR)
        }
        dateRegex.find(text)?.let { match ->
            facts += IdeaFacts.Fact(
                "تاریخ",
                normalizeNumber(match.groupValues[0]).replace("  ", " "),
                FactCategory.DATE,
            )
        }
        (durationRegex.find(text) ?: bareDurationRegex.find(text))?.let { match ->
            val number = normalizeNumber(match.groupValues[1])
            facts += IdeaFacts.Fact("مدت", "$number ${match.groupValues[2]}", FactCategory.DURATION)
        }
        platforms.filter { text.contains(it) }.forEach { platform ->
            facts += IdeaFacts.Fact("پلتفرم/بستر انتشار", platform, FactCategory.PLATFORM)
        }
        if (text.contains("رایگان")) {
            facts += IdeaFacts.Fact("قیمت", "رایگان", FactCategory.PRICE)
        } else {
            Regex("([۰-۹0-9،٬]+\\s*(?:تومان|ریال|میلیون))").find(text)?.let { match ->
                facts += IdeaFacts.Fact("قیمت", normalizeNumber(match.groupValues[1]), FactCategory.PRICE)
            }
        }
        audienceRegex.find(text)?.let { match ->
            facts += IdeaFacts.Fact("مخاطب", clean(match.groupValues[1]), FactCategory.AUDIENCE)
        }
        brandRegex.find(text)?.let { match ->
            facts += IdeaFacts.Fact("برند", clean(match.groupValues[1]), FactCategory.BRAND)
        }

        // De-duplicate by category, keep first (most reliable) occurrence.
        return IdeaFacts(facts.distinctBy { it.category to it.labelFa })
    }

    /** Trims filler verbs and whitespace the user may have trailing in the sentence. */
    private fun clean(raw: String): String = raw
        .trim()
        .replace(Regex("\\s*(هست|است|می\\s*باشد|بود)\\s*$"), "")
        .trim()

    private fun normalizeNumber(raw: String): String {
        val latin = raw.trim().toEnglishDigitsOrWord()
        return latin.toPersianDigits()
    }

    private fun String.toEnglishDigitsOrWord(): String {
        val asEnglish = StringBuilder()
        for (ch in this) {
            asEnglish.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
        val text = asEnglish.toString()
        return wordNumbers[text] ?: text
    }
}
