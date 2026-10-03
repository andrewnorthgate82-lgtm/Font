package com.promptsaz.app.domain.engine.score

import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.engine.improve.toQualityReport
import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.QualityReport
import com.promptsaz.app.domain.model.SectionKind
import com.promptsaz.app.domain.model.SectionScore
import com.promptsaz.app.domain.model.PromptSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structural-completeness scorer: weighted rubric over the 10 anatomy parts.
 * For image/video targets, parts that the adapter legitimately folds into the
 * scene description count as covered (per the product spec).
 */
@Singleton
class QualityScorer @Inject constructor(
    private val analyzer: PromptAnalyzer,
) {

    fun scoreSections(sections: List<PromptSection>, spec: PromptSpec): QualityReport {
        val byKind = sections.groupBy { it.kind }
        val media = spec.targetAi.isImageOrVideoModel
        val scores = SectionKind.entries.map { kind ->
            val weight = WEIGHTS.getValue(kind)
            val group = byKind[kind]
            val present = !group.isNullOrEmpty()
            val thin = present && group.first().body.trim().length < THIN_THRESHOLD
            val coveredByAdapter = media && kind in MEDIA_FOLDED
            val (earned, note) = when {
                coveredByAdapter && !present -> weight to "برای مدل تصویری/ویدیویی در شرح صحنه ادغام می‌شود."
                coveredByAdapter && present -> weight to "داده شده و در شرح صحنه هم لحاظ شده است."
                !present -> 0 to "این بخش در پرامپت نیست."
                thin -> weight / 2 to "موجود اما محتوای آن کم است."
                else -> weight to "کامل."
            }
            SectionScore(
                kind = kind,
                titleFa = TITLES.getValue(kind),
                earned = earned,
                max = weight,
                present = present || coveredByAdapter,
                noteFa = note,
            )
        }
        val total = scores.sumOf { it.earned }.coerceIn(0, 100)
        val suggestions = buildSuggestions(scores, spec)
        return QualityReport(total = total, sectionScores = scores, suggestionsFa = suggestions)
    }

    /** Scores a free-form text (AI-mode output or an imported prompt) via marker detection. */
    fun scoreText(text: String, spec: PromptSpec): QualityReport = analyzer.analyze(text, spec).toQualityReport()

    private fun buildSuggestions(scores: List<SectionScore>, spec: PromptSpec): List<String> {
        val suggestions = mutableListOf<String>()
        scores.filter { !it.present }.forEach { score ->
            suggestions += FIX_HINTS[score.kind] ?: "بخش «${score.titleFa}» اضافه شود."
        }
        scores.filter { it.present && it.earned < it.max }.forEach { score ->
            suggestions += "بخش «${score.titleFa}» محتوای بیشتری بگیرد (مثلاً یک مثال یا عدد مشخص)."
        }
        if (!spec.targetAi.isImageOrVideoModel && spec.detailLevel == com.promptsaz.app.domain.model.DetailLevel.QUICK) {
            suggestions += "با سطح جزئیات «استاندارد» یا «حرفه‌ای» دوباره بساز تا مثال و معیار کیفیت هم بیاید."
        }
        return suggestions.distinct().take(6)
    }

    private companion object {
        const val THIN_THRESHOLD = 40
        val WEIGHTS: Map<SectionKind, Int> = mapOf(
            SectionKind.ROLE to 10,
            SectionKind.OBJECTIVE to 12,
            SectionKind.CONTEXT to 12,
            SectionKind.INPUTS to 10,
            SectionKind.PROCESS to 10,
            SectionKind.OUTPUT_FORMAT to 14,
            SectionKind.CONSTRAINTS to 12,
            SectionKind.EXAMPLES to 8,
            SectionKind.QUALITY to 7,
            SectionKind.CLARIFICATION to 5,
        )
        val TITLES: Map<SectionKind, String> = mapOf(
            SectionKind.ROLE to "نقش",
            SectionKind.OBJECTIVE to "هدف",
            SectionKind.CONTEXT to "زمینه",
            SectionKind.INPUTS to "ورودی‌ها و متغیرها",
            SectionKind.PROCESS to "روند کار",
            SectionKind.OUTPUT_FORMAT to "قالب خروجی",
            SectionKind.CONSTRAINTS to "محدودیت‌ها",
            SectionKind.EXAMPLES to "مثال‌ها",
            SectionKind.QUALITY to "معیارهای کیفیت",
            SectionKind.CLARIFICATION to "قاعده شفاف‌سازی",
        )
        val MEDIA_FOLDED: Set<SectionKind> = setOf(
            SectionKind.PROCESS,
            SectionKind.EXAMPLES,
            SectionKind.QUALITY,
            SectionKind.CLARIFICATION,
        )
        val FIX_HINTS: Map<SectionKind, String> = mapOf(
            SectionKind.ROLE to "یک نقش متخصص مشخص به جای دستیار عمومی تعریف شود.",
            SectionKind.OBJECTIVE to "هدف و خروجی دقیق در یک جمله شفاف نوشته شود.",
            SectionKind.CONTEXT to "زمینه، مخاطب و محدودیت‌های پروژه اضافه شود.",
            SectionKind.INPUTS to "اطلاعات نامعلوم به شکل [متغیر] تعریف شود.",
            SectionKind.PROCESS to "روند کار گام‌به‌گام اضافه شود.",
            SectionKind.OUTPUT_FORMAT to "قالب، طول و لحن خروجی دقیق تعیین شود.",
            SectionKind.CONSTRAINTS to "محدودیت‌ها و موارد ممنوعه اضافه شود.",
            SectionKind.EXAMPLES to "یک نمونه کوتاه از سبک مطلوب اضافه شود.",
            SectionKind.QUALITY to "معیارهای سنجش کیفیت و خودآزمایی اضافه شود.",
            SectionKind.CLARIFICATION to "قاعده «اول بپرس، بعد حدس بزن» اضافه شود.",
        )
    }
}
