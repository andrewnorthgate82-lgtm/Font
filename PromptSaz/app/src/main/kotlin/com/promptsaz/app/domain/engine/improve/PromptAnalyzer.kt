package com.promptsaz.app.domain.engine.improve

import com.promptsaz.app.domain.model.FindingSeverity
import com.promptsaz.app.domain.model.ImprovementFinding
import com.promptsaz.app.domain.model.ImprovementReport
import com.promptsaz.app.domain.model.PromptSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic weakness analysis of an existing prompt. Works on any text
 * (Persian or English); findings are always Persian. Also powers
 * QualityScorer.scoreText for AI-mode output.
 */
@Singleton
class PromptAnalyzer @Inject constructor() {

    fun analyze(text: String, spec: PromptSpec? = null): ImprovementReport {
        val findings = mutableListOf<ImprovementFinding>()
        val trimmed = text.trim()
        val mediaTarget = spec?.targetAi?.isImageOrVideoModel == true

        if (trimmed.length < MIN_PROMPT_CHARS) {
            findings += ImprovementFinding(
                FindingSeverity.CRITICAL,
                "متن خیلی کوتاه است و به مدل انگشت‌نما می‌دهد.",
                "هدف، مخاطب و قالب خروجی به متن اضافه شود.",
            )
        }

        if (!mediaTarget) {
            if (!hasRole(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.CRITICAL,
                    "نقش متخصص تعریف نشده؛ مدل با هویت مبهم شروع می‌کند.",
                    "اول متن با یک نقش مشخص (تخصص و تجربه) شروع شود.",
                )
            }
            if (!hasOutputFormat(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.CRITICAL,
                    "قالب خروجی مشخص نیست؛ نتیجه هر بار شکل متفاوتی می‌گیرد.",
                    "ساختار، طول و لحن پاسخ به‌صراحت تعیین شود.",
                )
            }
            if (!hasClarification(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MAJOR,
                    "قاعده شفاف‌سازی ندارد؛ مدل در اطلاعات ناقص حدس می‌زند.",
                    "دستور «اگر اطلاعات ضروری کم است، اول بپرس» اضافه شود.",
                )
            }
            if (!hasProcess(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MAJOR,
                    "روند کار گام‌به‌گام ندارد؛ کیفیت پاسخ به شانس واگذار می‌شود.",
                    "مراحل انجام کار شماره‌گذاری و اضافه شود.",
                )
            }
            if (!hasConstraints(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MAJOR,
                    "محدودیت‌ها مشخص نیست؛ مدل ممکن است حاشیه و مطالب ساختگی بیاورد.",
                    "موارد ممنوعه و الزام «بدون داده ساختگی» اضافه شود.",
                )
            }
            if (!hasExamples(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MINOR,
                    "مثالی از سبک مطلوب وجود ندارد.",
                    "یک نمونه کوتاه از خروجی دلخواه اضافه شود.",
                )
            }
            if (!hasQuality(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MINOR,
                    "معیار کیفیت و خودآزمایی تعریف نشده است.",
                    "چند معیار سنجش و دستور بازبینی پیش از پاسخ اضافه شود.",
                )
            }
        } else {
            if (!hasParameters(trimmed)) {
                findings += ImprovementFinding(
                    FindingSeverity.MAJOR,
                    "پارامترهای فنی (نسبت تصویر، نسخه مدل) مشخص نشده است.",
                    "پارامترهایی مثل --ar و --v به انتهای پرامپت اضافه شود.",
                )
            }
            if (trimmed.length < MIN_MEDIA_CHARS) {
                findings += ImprovementFinding(
                    FindingSeverity.MAJOR,
                    "شرح بصری خیلی کلی است؛ سبک، نور و ترکیب‌بندی مشخص نیست.",
                    "سبک بصری، نورپردازی، پالت رنگی و زاویه دوربین توصیف شود.",
                )
            }
        }

        val bracketCount = Regex("\\[[A-Za-z0-9_ ]{2,30}\\]").findAll(trimmed).count()
        if (!mediaTarget && bracketCount == 0 && trimmed.length > MIN_PROMPT_CHARS) {
            findings += ImprovementFinding(
                FindingSeverity.MINOR,
                "متغیری در قالب [نام] تعریف نشده؛ بخش‌های متغیر متن مشخص نیست.",
                "اطلاعات قابل تغییر به شکل [متغیر] جدا شود.",
            )
        }

        return ImprovementReport(findings)
    }

    // --- detection helpers -------------------------------------------------------

    private fun hasRole(t: String) =
        t.contains("نقش") || t.contains("تو یک") || t.contains("تو یک") ||
            t.contains("<role>", ignoreCase = true) || t.contains("act as", ignoreCase = true)

    private fun hasOutputFormat(t: String) =
        t.contains("قالب") || t.contains("ساختار پاسخ") || t.contains("output_format", ignoreCase = true) ||
            t.contains("format:", ignoreCase = true) || t.contains("جدول") || t.contains("فهرست")

    private fun hasClarification(t: String) =
        (t.contains("سؤال") && t.contains("بپرس")) || t.contains("بپرس") ||
            t.contains("ask") && t.contains("question", ignoreCase = true)

    private fun hasProcess(t: String) =
        t.contains("روند") || t.contains("گام") || t.contains("مرحله") ||
            Regex("(^|\\n)\\s*(۱|۱|1)[\\).\\-]").containsMatchIn(t)

    private fun hasConstraints(t: String) =
        t.contains("ممنوع") || t.contains("ننویس") || t.contains("محدودیت") || t.contains("دقت کن") ||
            t.contains("--no") || t.contains("don't", ignoreCase = true) || t.contains("do not", ignoreCase = true)

    private fun hasExamples(t: String) = t.contains("مثال") || t.contains("نمونه") || t.contains("example", ignoreCase = true)

    private fun hasQuality(t: String) = t.contains("معیار") || t.contains("کیفیت") || t.contains("بسنج") ||
        t.contains("criteria", ignoreCase = true)

    private fun hasParameters(t: String) = t.contains("--ar") || t.contains("--v") || t.contains("--")

    private companion object {
        const val MIN_PROMPT_CHARS = 80
        const val MIN_MEDIA_CHARS = 120
    }
}

/** Converts analysis findings into a structural QualityReport. */
fun ImprovementReport.toQualityReport(): com.promptsaz.app.domain.model.QualityReport {
    val deduction = findings.sumOf {
        when (it.severity) {
            FindingSeverity.CRITICAL -> 15
            FindingSeverity.MAJOR -> 8
            FindingSeverity.MINOR -> 4
        }
    }
    return com.promptsaz.app.domain.model.QualityReport(
        total = (100 - deduction).coerceIn(20, 100),
        sectionScores = emptyList(),
        suggestionsFa = findings.map { it.fixFa }.distinct(),
    )
}
