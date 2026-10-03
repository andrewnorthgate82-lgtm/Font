package com.promptsaz.app.domain.engine.facts

import com.promptsaz.app.util.toPersianDigits

/**
 * Hard facts extracted from the user's raw idea (names, dates, durations,
 * platforms, prices, …). They are rendered as a «اطلاعات قطعی» block in the
 * prompt context, drive the "never re-ask what the user already said" rule,
 * and suppress placeholder variables for information that is already known.
 *
 * Extraction is LABEL-BASED: a field starts at its label (موضوع، مدرس،
 * تاریخ، مدت، ساعت، مکان، بستر، قیمت، مخاطب، ثبت‌نام…) and ends at the
 * NEXT label, the end of the line, or the end of the sentence — whichever
 * comes first. This works with colons, without colons, multi-line and
 * single-line input. Values are trimmed of connector words («از»، «به»،
 * «هست»…) at both ends. Extractors are deliberately conservative: a missing
 * fact is always better than a wrong one.
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

enum class FactCategory {
    SUBJECT, EVENT, INSTRUCTOR, DATE, TIME, DURATION, PLATFORM,
    LOCATION, MODE, PRICE, AUDIENCE, REGISTRATION,
}

object IdeaFactsExtractor {

    private data class FieldLabel(
        val pattern: Regex,
        val category: FactCategory,
        val labelFa: String,
        /** Value is the label word itself (حضوری / آنلاین). */
        val selfValue: Boolean = false,
    )

    /** All labels that can start a field. Word-boundary guarded at match time. */
    private val labels = listOf(
        FieldLabel(Regex("موضوع\\s*(?:آموزشی|اصلی)?"), FactCategory.SUBJECT, "موضوع"),
        FieldLabel(Regex("(?:مدرس|استاد|سخنران|برگزارکننده|گوینده)"), FactCategory.INSTRUCTOR, "مدرس"),
        FieldLabel(Regex("تاریخ\\s*(?:برگزاری|شروع)?"), FactCategory.DATE, "تاریخ"),
        FieldLabel(Regex("ساعت\\s*(?:برگزاری)?"), FactCategory.TIME, "ساعت"),
        FieldLabel(Regex("مدت(?:\\s*زمان)?"), FactCategory.DURATION, "مدت"),
        FieldLabel(Regex("(?:مکان|محل)(?:\\s*برگزاری)?"), FactCategory.LOCATION, "مکان"),
        FieldLabel(
            Regex("بستر(?:\\s*(?:و\\s*)?(?:تبلیغ|انتشار|نشر))*"),
            FactCategory.PLATFORM,
            "بستر انتشار",
        ),
        FieldLabel(Regex("(?:پلتفرم|کانال\\s*انتشار)"), FactCategory.PLATFORM, "پلتفرم"),
        FieldLabel(Regex("(?:قیمت|هزینه)"), FactCategory.PRICE, "قیمت"),
        FieldLabel(Regex("مخاطبان?"), FactCategory.AUDIENCE, "مخاطب"),
        FieldLabel(
            Regex("(?:ثبت[‌ ]?نام|راه\\s*ارتباطی|شماره\\s*تماس|لینک\\s*ثبت)"),
            FactCategory.REGISTRATION,
            "راه ثبت‌نام",
        ),
        FieldLabel(Regex("حضوری"), FactCategory.MODE, "شیوه برگزاری", selfValue = true),
        FieldLabel(Regex("آنلاین"), FactCategory.MODE, "شیوه برگزاری", selfValue = true),
        FieldLabel(Regex("ترکیبی"), FactCategory.MODE, "شیوه برگزاری", selfValue = true),
    )

    /** Event words for the (label-less) EVENT fact. */
    private val eventRegex = Regex(
        "(دوره|وبینار|کارگاه|سمینار|کلاس|همایش|جلسه|رویداد|جشن|مراسم|کنسرت|نمایشگاه)" +
            "(?:\\s+(آموزشی|تخصصی|آنلاین|حضوری|معرفی))?",
    )

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

    private val dateFallbackRegex = Regex("[۰-۹0-9]{1,2}\\s*(${months.joinToString("|")})")
    private val durationFallbackRegex = Regex(
        "(?:به\\s*مدت|مدت)\\s*([۰-۹0-9]{1,3}|${wordNumbers.keys.joinToString("|")})\\s*(روز|هفته|ماه|ساعت|دقیقه|ثانیه)",
    )

    /** Connector words stripped from the start/end of a field value. */
    private val leadingConnectors = listOf("از", "به", "برای", "با", "در", "که", "و", "هم")
    private val trailingConnectors = listOf(
        "از", "به", "تا", "و", "برای", "است", "هست", "بود", "شد", "می‌باشد", "خواهد بود",
    )

    fun extract(idea: String): IdeaFacts {
        val text = idea
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        if (text.isEmpty()) return IdeaFacts(emptyList())

        val facts = mutableListOf<IdeaFacts.Fact>()
        var locationFromMode: String? = null

        // --- 1. label-based fields ------------------------------------------------
        val matches = findAllLabels(text)
        matches.forEachIndexed { index, match ->
            val valueStart = match.range.last + 1
            val valueEnd = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val raw = text.substring(valueStart, valueEnd)

            val value = when {
                match.label.selfValue -> {
                    // «حضوری در تهران» → MODE=حضوری + LOCATION=تهران
                    Regex("\\s*در\\s+([^،.؛:!؟\\n]{2,30})").find(raw)?.let { placeMatch ->
                        locationFromMode = cleanValue(placeMatch.groupValues[1], cutAtConjunction = false)
                    }
                    match.text
                }
                else -> cleanValue(raw, cutAtConjunction = match.label.category == FactCategory.INSTRUCTOR)
            }
            if (value.isNotBlank()) {
                facts += IdeaFacts.Fact(match.label.labelFa, normalizeNumbers(value), match.label.category)
            }
        }
        locationFromMode?.let { facts += IdeaFacts.Fact("مکان", it, FactCategory.LOCATION) }

        // --- 2. label-less fallbacks (only for categories not found yet) ----------
        val present = facts.map { it.category }.toSet()
        if (FactCategory.EVENT !in present) {
            eventRegex.find(text)?.let { match ->
                val phrase = (match.groupValues[1] + " " + match.groupValues[2]).trim()
                facts += IdeaFacts.Fact("نوع رویداد", phrase, FactCategory.EVENT)
            }
        }
        if (FactCategory.DATE !in present) {
            dateFallbackRegex.find(text)?.let { match ->
                facts += IdeaFacts.Fact(
                    "تاریخ",
                    normalizeNumbers(match.groupValues[0]).replace("  ", " "),
                    FactCategory.DATE,
                )
            }
        }
        if (FactCategory.DURATION !in present) {
            durationFallbackRegex.find(text)?.let { match ->
                val number = normalizeNumbers(match.groupValues[1])
                facts += IdeaFacts.Fact("مدت", "$number ${match.groupValues[2]}", FactCategory.DURATION)
            }
        }
        if (FactCategory.PLATFORM !in present) {
            platforms.firstOrNull { text.contains(it) }?.let { platform ->
                facts += IdeaFacts.Fact("پلتفرم/بستر انتشار", platform, FactCategory.PLATFORM)
            }
        }
        if (FactCategory.PRICE !in present) {
            when {
                text.contains("رایگان") -> facts += IdeaFacts.Fact("قیمت", "رایگان", FactCategory.PRICE)
                Regex("([۰-۹0-9،٬]+\\s*(?:تومان|ریال|میلیون))").find(text)?.let { match ->
                    facts += IdeaFacts.Fact("قیمت", normalizeNumbers(match.groupValues[1]), FactCategory.PRICE)
                    true
                } != null -> Unit
                else -> Unit
            }
        }

        // Keep first (earliest, most reliable) fact per category, in text order.
        val deduped = facts.distinctBy { it.category }.sortedBy { fact ->
            matches.firstOrNull { it.label.category == fact.category }?.range?.first ?: Int.MAX_VALUE
        }
        return IdeaFacts(deduped)
    }

    private data class LabelMatch(
        val range: IntRange,
        val label: FieldLabel,
        val text: String,
    )

    /** Finds all label occurrences that stand as whole words, sorted by position. */
    private fun findAllLabels(text: String): List<LabelMatch> {
        val result = mutableListOf<LabelMatch>()
        labels.forEach { label ->
            label.pattern.findAll(text).forEach { match ->
                var start = match.range.first
                var end = match.range.last
                // Label patterns like «موضوع\s*(…)» swallow the separator
                // space; trim it back so the word-boundary check and the
                // value slice stay correct.
                while (end > start && text[end].isWhitespace()) end--
                while (start < end && text[start].isWhitespace()) start++
                val beforeOk = start == 0 || !text[start - 1].isLetterOrDigit()
                val afterOk = end == text.length - 1 || !text[end + 1].isLetterOrDigit()
                if (beforeOk && afterOk) {
                    result += LabelMatch(start..end, label, text.substring(start, end + 1))
                }
            }
        }
        return result
            .sortedWith(compareBy({ it.range.first }, { it.range.last }))
            .fold(mutableListOf<LabelMatch>()) { acc, match ->
                // drop matches overlapping an already-accepted label span
                if (acc.none { it.range.overlaps(match.range) }) acc += match
                acc
            }
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    /**
     * Index of the first real sentence boundary (end of line, ؛!؟, or a
     * period followed by space/end). A period between letters or digits
     * (site.com، ۴.۵) is not a boundary. Null when there is none.
     */
    private fun sentenceCut(value: String): Int? {
        var best: Int? = null
        fun consider(index: Int) {
            if (index >= 0 && (best == null || index < best!!)) best = index
        }
        for (ch in listOf('؛', '!', '؟', '\n')) consider(value.indexOf(ch))
        var dot = value.indexOf('.')
        while (dot >= 0) {
            val next = dot + 1
            if (next >= value.length || !value[next].isLetterOrDigit()) {
                consider(dot)
                break
            }
            dot = value.indexOf('.', next)
        }
        return best
    }

    /**
     * Trims a raw field slice into a clean value: stops at the first sentence
     * boundary or line break, strips punctuation and connector words at both
     * ends, and (for person names) cuts at conjunctions. A period inside a
     * word (site.com، ۴.۵) is NOT a sentence boundary.
     */
    private fun cleanValue(raw: String, cutAtConjunction: Boolean): String {
        var value = raw
        val cut = sentenceCut(value)
        if (cut != null) value = value.substring(0, cut)
        value = value.trim()
            .trim(':', '،', ';', ' ')
        // Person values stop at conjunctions/relative pronouns.
        if (cutAtConjunction) {
            value = value.split(Regex("\\s+(?:و|که)\\s+"))[0]
        }
        // Strip leading connector words (از، به، برای…).
        var stripped = value
        var changed = true
        while (changed) {
            changed = false
            leadingConnectors.forEach { connector ->
                val token = "$connector "
                if (stripped.startsWith(token)) {
                    stripped = stripped.removePrefix(token).trim(); changed = true
                }
            }
        }
        // Strip trailing connector words (به، تا، است، هست…).
        changed = true
        while (changed) {
            changed = false
            trailingConnectors.forEach { connector ->
                val token = " $connector"
                if (stripped.endsWith(token)) {
                    stripped = stripped.removeSuffix(token).trim(); changed = true
                }
            }
        }
        stripped = stripped.trim(':', '،', '.', '؛', ' ')
        return if (stripped.length > MAX_VALUE_LEN) stripped.take(MAX_VALUE_LEN).trim() else stripped
    }

    private fun normalizeNumbers(value: String): String {
        val english = StringBuilder()
        for (ch in value) {
            english.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
        val text = english.toString()
        return wordNumbers.entries.fold(text) { acc, (word, digit) ->
            acc.replace(Regex("(?<![\\p{L}\\p{N}])$word(?![\\p{L}\\p{N}])"), digit)
        }.toPersianDigits()
    }

    private const val MAX_VALUE_LEN = 60
}
