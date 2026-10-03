package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.engine.variant.VariantGenerator
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.VariantStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-style tests over the REAL knowledge bases: they parse the assets
 * from the repo (skipped when unreachable) and assert the engine output.
 */
class PromptEngineTest {

    private val engine = PromptEngine(
        clarificationEngine = ClarificationEngine(),
        assembler = PromptAssembler(),
        scorer = QualityScorer(PromptAnalyzer()),
        variants = VariantGenerator(),
    )

    private fun kbText(name: String): String? =
        listOf(
            "app/src/main/assets/knowledge/$name",
            "src/main/assets/knowledge/$name",
        ).firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }

    private fun marketingKb(): com.promptsaz.app.domain.model.DomainKnowledge? {
        val text = kbText("marketing.json") ?: return null
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(com.promptsaz.app.domain.model.DomainKnowledge.serializer(), text)
    }

    private fun spec(
        target: TargetAi = TargetAi.CHATGPT,
        detail: DetailLevel = DetailLevel.STANDARD,
        idea: String = "می‌خواهم برای فروش دوره آنلاینم تبلیغ اینستاگرام بنویسم",
    ) = PromptSpec(
        idea = idea,
        domainId = "marketing",
        targetAi = target,
        outputLanguage = OutputLanguage.PERSIAN,
        detailLevel = detail,
    )

    @Test
    fun `chatgpt output contains all ten section headers`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(), kb)
        listOf(
            "## نقش", "## هدف", "## زمینه", "## ورودی‌ها و متغیرها", "## روند کار",
            "## قالب خروجی", "## محدودیت‌ها", "## مثال‌ها", "## معیارهای کیفیت", "## قاعده شفاف‌سازی",
        ).forEach { header ->
            assertTrue("missing header: $header", output.text.contains(header))
        }
    }

    @Test
    fun `claude output uses xml tags`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(target = TargetAi.CLAUDE), kb)
        assertTrue(output.text.contains("<role>"))
        assertTrue(output.text.contains("</role>"))
        assertTrue(output.text.contains("<output_format>"))
    }

    @Test
    fun `gemini output groups into three blocks`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(target = TargetAi.GEMINI), kb)
        assertTrue(output.text.contains("## دستورالعمل"))
        assertTrue(output.text.contains("## زمینه"))
        assertTrue(output.text.contains("## قالب پاسخ"))
    }

    @Test
    fun `midjourney output is descriptive with parameters`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(
            spec(target = TargetAi.MIDJOURNEY, idea = "تصویر یک لیوان قهوه داغ روی میز چوبی"),
            kb,
        )
        assertTrue(output.text.contains("--ar"))
        assertTrue(output.text.contains("--no"))
        assertTrue(output.text.contains("قهوه"))
        assertTrue(!output.text.contains("## نقش"))
    }

    @Test
    fun `video output has timed shots`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(target = TargetAi.VIDEO_GENERATORS), kb)
        assertTrue(output.text.contains("صحنه ۱"))
        assertTrue(output.text.contains("صحنه ۴"))
        assertTrue(output.text.contains("دوربین"))
    }

    @Test
    fun `standard generation scores full structure`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(), kb)
        assertEquals(100, output.score.total)
    }

    @Test
    fun `quick level drops examples and quality and scores below 100`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(detail = DetailLevel.QUICK), kb)
        assertTrue(output.score.total < 100)
        assertTrue(output.score.suggestionsFa.isNotEmpty())
        assertTrue(!output.text.contains("## مثال‌ها"))
    }

    @Test
    fun `media target folds irrelevant sections and still scores high`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(spec(target = TargetAi.MIDJOURNEY), kb)
        assertTrue("score was ${output.score.total}", output.score.total >= 90)
    }

    @Test
    fun `skipped answers become bracketed variables`() {
        val kb = marketingKb() ?: return
        val skipped = com.promptsaz.app.domain.model.ClarifyingAnswer(
            questionId = "audience",
            questionFa = "مخاطب اصلی کیست؟",
            value = null,
        )
        val output = engine.generate(spec().copy(answers = listOf(skipped)), kb)
        assertTrue(output.text.contains("[AUDIENCE]"))
    }

    @Test
    fun `answered questions land in context`() {
        val kb = marketingKb() ?: return
        val answered = com.promptsaz.app.domain.model.ClarifyingAnswer(
            questionId = "audience",
            questionFa = "مخاطب اصلی کیست؟",
            value = "زنان ۲۵ تا ۴۰ سال",
        )
        val output = engine.generate(spec().copy(answers = listOf(answered)), kb)
        assertTrue(output.text.contains("زنان ۲۵ تا ۴۰ سال"))
    }

    @Test
    fun `concise variant is shorter and creative adds flavor`() {
        val kb = marketingKb() ?: return
        val base = engine.generate(spec(), kb)
        val concise = engine.variantOf(base, VariantStyle.CONCISE)
        val creative = engine.variantOf(base, VariantStyle.CREATIVE)
        assertTrue(concise.text.length < base.text.length)
        assertTrue(creative.text.contains("غیرکلیشه‌ای"))
    }

    @Test
    fun `title is capped at 40 chars and contains domain name`() {
        val kb = marketingKb() ?: return
        val output = engine.generate(
            spec(idea = "یک کمپین بسیار طولانی با توضیحات بسیار زیاد و ادامه دار و خسته کننده برای تست عنوان"),
            kb,
        )
        assertTrue(output.title.length <= 40)
        assertTrue(output.title.startsWith("بازاریابی"))
    }
}
