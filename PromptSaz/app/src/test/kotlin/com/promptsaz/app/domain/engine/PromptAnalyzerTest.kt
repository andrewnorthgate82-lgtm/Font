package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAnalyzerTest {

    private val analyzer = PromptAnalyzer()
    private val spec = PromptSpec(
        idea = "تست",
        domainId = "general",
        targetAi = TargetAi.CHATGPT,
        outputLanguage = com.promptsaz.app.domain.model.OutputLanguage.PERSIAN,
        detailLevel = DetailLevel.STANDARD,
    )

    @Test
    fun `lazy prompt collects critical findings`() {
        val report = analyzer.analyze("یه متن خوب برای ایمیل بده", spec)
        assertTrue(report.criticalCount >= 2)
        assertTrue(report.findings.any { it.messageFa.contains("نقش") })
        assertTrue(report.findings.any { it.messageFa.contains("قالب") })
    }

    @Test
    fun `structured prompt has few findings`() {
        val structured = """
            نقش: تو یک کپی‌رایتر ارشد هستی.
            هدف: نوشتن ۵ تیتر.
            زمینه: مخاطب ما مدیران هستند.
            روند کار:
            ۱) درد مخاطب را پیدا کن.
            ۲) تیترها را بنویس.
            قالب خروجی: جدول ۵ ردیفی با ستون تیتر و ساختار.
            محدودیت‌ها: صفت اغراق‌آمیز ننویس؛ آمار جعلی ممنوع.
            مثال: «۵ راه تست‌شده…»
            معیار کیفیت: انطباق با قالب؛ بسنج و اصلاح کن.
            اگر اطلاعات کم است، اول سؤال بپرس.
        """.trimIndent()
        val report = analyzer.analyze(structured, spec)
        assertTrue(report.findings.none { it.severity == com.promptsaz.app.domain.model.FindingSeverity.CRITICAL })
    }

    @Test
    fun `media prompts check parameters instead of sections`() {
        val mediaSpec = spec.copy(targetAi = TargetAi.MIDJOURNEY)
        val withoutParams = analyzer.analyze("a cozy coffee cup on a wooden table, warm light", mediaSpec)
        assertTrue(withoutParams.findings.any { it.messageFa.contains("پارامتر") })

        val withParams = analyzer.analyze(
            "a cozy coffee cup on a wooden table, warm morning light, soft shadows, " +
                "shallow depth of field, editorial photography style, cohesive palette --ar 4:5 --v 6 --no text",
            mediaSpec,
        )
        assertTrue(withParams.findings.none { it.messageFa.contains("پارامتر") })
    }

    @Test
    fun `findings convert to a quality report with fix suggestions`() {
        val report = analyzer.analyze("کمک کن", spec)
        val quality = com.promptsaz.app.domain.engine.improve.toQualityReport(report)
        assertTrue(quality.total < 100)
        assertTrue(quality.suggestionsFa.isNotEmpty())
        assertEquals(report.findings.map { it.fixFa }.distinct(), quality.suggestionsFa)
    }
}
