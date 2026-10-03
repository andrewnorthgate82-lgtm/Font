package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.engine.improve.PromptAnalyzer
import com.promptsaz.app.domain.engine.score.QualityScorer
import com.promptsaz.app.domain.engine.variant.VariantGenerator
import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.GeneratedPrompt
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated prompt-quality audit over the REAL knowledge bases, per user
 * feedback. For every ready domain it runs ambiguous / short / detail-rich /
 * cross-domain ideas through the engine and verifies:
 *
 *  - the role fits the domain AND the task type (poster → designer, not ads writer)
 *  - the output format matches the deliverable (never a content calendar
 *    unless a calendar was requested)
 *  - the user's confirmed facts appear structured and are never re-asked
 *  - no repetition, no half sentences, no raw knowledge-base metadata,
 *    no placeholder contradictions, and consistent Persian digits
 *
 * The KB helper returns null (and the test silently passes) when the asset
 * files are not reachable from the working directory — same convention as
 * PromptEngineTest. tools/validate_kb.py is the authoritative standalone check.
 */
class PromptQualityAuditTest {

    private val clarificationEngine = ClarificationEngine()

    private val engine = PromptEngine(
        clarificationEngine = clarificationEngine,
        assembler = PromptAssembler(),
        scorer = QualityScorer(PromptAnalyzer()),
        variants = VariantGenerator(),
    )

    // --- real-KB loading -------------------------------------------------------

    private fun kb(name: String): DomainKnowledge? {
        val file = listOf(
            "app/src/main/assets/knowledge/$name",
            "src/main/assets/knowledge/$name",
        ).firstOrNull { java.io.File(it).exists() } ?: return null
        return runCatching {
            val text = java.io.File(file).readText()
            Json { ignoreUnknownKeys = true }.decodeFromString(DomainKnowledge.serializer(), text)
        }.getOrNull()
    }

    private fun marketing(): DomainKnowledge? = kb("marketing.json")
    private fun social(): DomainKnowledge? = kb("social_media.json")
    private fun programming(): DomainKnowledge? = kb("programming.json")
    private fun general(): DomainKnowledge? = kb("general.json")

    // --- helpers -----------------------------------------------------------------

    /** Builds a spec whose clarifying questions were all skipped (worst case). */
    private fun skippedSpec(idea: String, domainId: String, kbObj: DomainKnowledge?): PromptSpec {
        val questions = clarificationEngine.questionsFor(kbObj, idea)
        return PromptSpec(
            idea = idea,
            domainId = domainId,
            targetAi = TargetAi.CHATGPT,
            outputLanguage = OutputLanguage.PERSIAN,
            detailLevel = DetailLevel.STANDARD,
            answers = questions.map { ClarifyingAnswer(it.id, it.questionFa, null) },
        )
    }

    private fun generate(idea: String, domainId: String, kbObj: DomainKnowledge?): GeneratedPrompt {
        val spec = skippedSpec(idea, domainId, kbObj)
        return engine.generate(spec, kbObj)
    }

    private fun assertNoLatinDigits(prompt: String) {
        val cleaned = prompt.lines()
            .filterNot { it.contains("--") }
            .joinToString("\n")
            .replace(Regex("\\[[A-Za-z0-9_ ]+\\]"), "[]")
        val match = Regex("[0-9]").find(cleaned)
        assertNull(
            "Latin digit in Persian prompt near: " +
                (match?.let {
                    cleaned.substring(
                        maxOf(0, it.range.first - 30),
                        minOf(cleaned.length, it.range.first + 30),
                    )
                } ?: ""),
            match,
        )
    }

    private fun countOccurrences(text: String, fragment: String): Int =
        text.split(fragment).size - 1

    private fun assertCleanStructure(output: GeneratedPrompt) {
        assertFalse("prompt must not contain an ellipsis", output.text.contains("…"))
        assertNoLatinDigits(output.text)
        listOf(
            "## نقش", "## هدف", "## زمینه", "## ورودی‌ها و متغیرها", "## روند کار",
            "## قالب خروجی", "## محدودیت‌ها", "## مثال‌ها", "## معیارهای کیفیت", "## قاعده شفاف‌سازی",
        ).forEach { header -> assertTrue("missing $header", output.text.contains(header)) }
    }

    /** No raw persona metadata (whenToUseFa) may ever leak into a prompt. */
    private fun assertNoRawPersonaMetadata(output: String, kbObj: DomainKnowledge) {
        kbObj.personas.forEach { persona ->
            val marker = persona.whenToUseFa.trim()
            if (marker.length > 10) {
                assertFalse(
                    "raw metadata leaked: ${marker.take(30)}",
                    output.contains(marker),
                )
            }
        }
    }

    // --- 1. the user's regression case -------------------------------------------

    private val regressionIdea =
        "میخواهیم برای دوره آموزشی خودم یک پوستر طراحی کنم. موضوع آموزش هوش مصنوعی. " +
            "مدرس محسن ابوطالبیان. تاریخ از ۱۱ مهر به مدت ۳ روز. بستر تبلیغ و انتشار اینستاگرام هست"

    @Test
    fun `regression - course poster with full details routes to a designer brief`() {
        val kb = marketing() ?: return
        val asked = clarificationEngine.questionsFor(kb, regressionIdea).map { it.id }
        // The deliverable question must be asked (ambiguous poster → brief or image),
        // but nothing the user already said may be asked again.
        assertTrue("deliverable question must be asked", asked.contains("deliverable-type"))
        assertFalse("topic already given", asked.contains("product-service"))
        assertFalse("platform already given", asked.contains("platform"))

        val output = generate(regressionIdea, "marketing", kb)

        // Role: designer, not ads copywriter
        assertTrue("role must be a designer, was: ${output.text.take(200)}", output.text.contains("کارگردان هنری"))
        assertFalse("wrong persona leaked", output.text.contains("گوگل ادز"))

        // Deliverable: designer brief, never a content calendar
        assertTrue("objective must promise a design brief", output.text.contains("بریف کامل طراحی"))
        assertTrue(output.text.contains("بریف طراحی برای طراح گرافیک"))
        assertFalse("calendar must not appear for a poster", output.text.contains("تقویم محتوایی"))

        // Confirmed facts, structured once
        assertTrue("facts block missing", output.text.contains("اطلاعات قطعی"))
        assertTrue(output.text.contains("مدرس: محسن ابوطالبیان"))
        assertTrue(output.text.contains("۱۱ مهر"))
        assertTrue(output.text.contains("۳ روز"))
        assertTrue(output.text.contains("اینستاگرام"))
        assertTrue(output.text.contains("موضوع: آموزش هوش مصنوعی"))

        // No repetition of the idea sentence, no placeholders for known facts
        assertEquals("idea sentence repeated", 1, countOccurrences(output.text, "پوستر طراحی کنم"))
        assertFalse("topic must not become a placeholder", output.text.contains("[TOPIC_OR_PRODUCT]"))
        assertFalse("platform must not become a placeholder", output.text.contains("[PLATFORM]"))

        // Relevant, complete example (the poster brief, not the A/B headline test)
        assertTrue("poster example expected", output.text.contains("سارا رستمی"))
        assertFalse("irrelevant example", output.text.contains("A/B"))

        // Quality criterion is a directive built from the KB fix, not a raw symptom
        assertTrue(output.text.contains(kb.failureModes.first().fixFa))

        assertCleanStructure(output)
        assertNoRawPersonaMetadata(output.text, kb)
    }

    // --- 2. deliverable answers switch the output type ----------------------------

    @Test
    fun `deliverable answer controls the output type`() {
        val kb = marketing() ?: return
        val brief = generate(regressionIdea, "marketing", kb)
        assertTrue(brief.text.contains("بریف کامل طراحی"))

        val textSpec = skippedSpec(regressionIdea, "marketing", kb).copy(
            answers = listOf(
                ClarifyingAnswer("deliverable-type", "خروجی چه باشد؟", "متن و ساختار"),
            ),
        )
        val textOut = engine.generate(textSpec, kb)
        assertTrue("expected a text deliverable", textOut.text.contains("متن ساخت‌یافتهٔ کامل"))
        assertFalse(
            "brief format must not appear for a text deliverable",
            textOut.text.contains("ساختار: بریف طراحی برای طراح گرافیک"),
        )

        val imageSpec = skippedSpec(regressionIdea, "marketing", kb).copy(
            answers = listOf(
                ClarifyingAnswer("deliverable-type", "خروجی چه باشد؟", "پرامپت برای ساخت تصویر"),
            ),
        )
        val imageOut = engine.generate(imageSpec, kb)
        assertTrue("expected an image-prompt deliverable", imageOut.text.contains("مدل تولید تصویر"))
    }

    // --- 3. marketing domain ---------------------------------------------------------

    @Test
    fun `marketing - content calendar only when requested`() {
        val kb = marketing() ?: return
        val idea = "برای پیج لباسم یک تقویم محتوایی یک‌ماهه می‌خواهم"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse("calendar is explicit — no deliverable question", asked.contains("deliverable-type"))
        val output = generate(idea, "marketing", kb)
        assertTrue("expected a calendar deliverable", output.text.contains("تقویم محتوایی"))
        assertCleanStructure(output)
    }

    @Test
    fun `marketing - short ambiguous idea still produces full clean structure`() {
        val kb = marketing() ?: return
        val output = generate("تبلیغ بنویس", "marketing", kb)
        assertCleanStructure(output)
        assertTrue("score was ${output.score.total}", output.score.total >= 85)
    }

    @Test
    fun `marketing - clear text idea skips the deliverable question`() {
        val kb = marketing() ?: return
        val idea = "ایمیل خوش‌آمدگویی برای مشتریان جدید فروشگاه"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse(asked.contains("deliverable-type"))
        val output = generate(idea, "marketing", kb)
        assertFalse(output.text.contains("تقویم محتوایی"))
        assertCleanStructure(output)
    }

    // --- 4. social media domain ----------------------------------------------------------

    @Test
    fun `social - cover design routes to designer`() {
        val kb = social() ?: return
        val output = generate("کاور پیج اینستاگرام برای کافه‌ام می‌خوام", "social_media", kb)
        assertTrue("designer role expected", output.text.contains("طراح گرافیک"))
        assertTrue(output.text.contains("بریف کامل طراحی"))
        assertFalse(output.text.contains("تقویم محتوایی"))
        assertCleanStructure(output)
        assertNoRawPersonaMetadata(output.text, kb)
    }

    @Test
    fun `social - reel script keeps the script persona`() {
        val kb = social() ?: return
        val output = generate("سناریوی ریلز ۳۰ ثانیه‌ای برای معرفی اپلیکیشن", "social_media", kb)
        assertTrue("role should stay a script writer", output.text.contains("سناریونویس"))
        assertCleanStructure(output)
    }

    @Test
    fun `social - caption idea uses caption structure`() {
        val kb = social() ?: return
        val output = generate("کپشن معرفی محصول جدید عطر", "social_media", kb)
        assertTrue(output.text.contains("کپشن"))
        assertFalse(output.text.contains("تقویم محتوایی"))
        assertCleanStructure(output)
    }

    // --- 5. programming domain ------------------------------------------------------------

    @Test
    fun `programming - android app idea picks the android persona and code process`() {
        val kb = programming() ?: return
        val idea = "یک اپ اندروید برای لیست کارها با کاتلین می‌خواهم"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse("stack already given", asked.contains("language-stack"))
        val output = generate(idea, "programming", kb)
        assertTrue("android persona expected", output.text.contains("توسعه‌دهنده ارشد اندروید"))
        assertTrue(output.text.contains("کد کامل"))
        assertCleanStructure(output)
    }

    @Test
    fun `programming - python idea stays a coding task`() {
        val kb = programming() ?: return
        val output = generate("کد پایتون برای خواندن فایل اکسل و ساخت گزارش", "programming", kb)
        assertTrue(output.text.contains("کد کامل"))
        assertCleanStructure(output)
    }

    @Test
    fun `programming - short bugfix idea uses the investigation structure`() {
        val kb = programming() ?: return
        val output = generate("فرم ثبت‌نام من خطا می‌دهد، رفع باگ کن", "programming", kb)
        assertTrue("bug investigation structure expected", output.text.contains("بررسی سیستماتیک"))
        assertCleanStructure(output)
    }

    // --- 6. general domain -------------------------------------------------------------------

    @Test
    fun `general - website text stays text`() {
        val kb = general() ?: return
        val idea = "متن معرفی رستوران ایرانی برای وبسایت"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse(asked.contains("deliverable-type"))
        val output = generate(idea, "general", kb)
        assertTrue("text deliverable expected", output.text.contains("متن ساخت‌یافتهٔ کامل"))
        assertCleanStructure(output)
    }

    @Test
    fun `general - graduation poster routes to design (cross-domain)`() {
        val kb = general() ?: return
        val output = generate("پوستر جشن فارغ‌التحصیلی دانشجویان می‌خواهم", "general", kb)
        assertTrue("designer role expected", output.text.contains("کارگردان هنری"))
        assertTrue(output.text.contains("بریف کامل طراحی"))
        assertFalse(output.text.contains("تقویم محتوایی"))
        assertCleanStructure(output)
    }

    @Test
    fun `general - vague idea asks the deliverable question and still generates`() {
        val kb = general() ?: return
        val idea = "یک ایده کوتاه بده"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertTrue("deliverable question expected for a vague idea", asked.contains("deliverable-type"))
        val output = generate(idea, "general", kb)
        assertCleanStructure(output)
    }

    // --- 7. image-prompt routing ----------------------------------------------------------------

    @Test
    fun `image model mention routes to an image prompt without asking`() {
        val kb = general() ?: return
        val idea = "با میدجرنی یک پوستر کافه بسازم"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse("image model is explicit", asked.contains("deliverable-type"))
        val output = generate(idea, "general", kb)
        assertTrue("image prompt deliverable expected", output.text.contains("مدل تولید تصویر"))
        assertCleanStructure(output)
    }

    // --- 8. sweep: every ready domain, text and design ------------------------------------------

    @Test
    fun `sweep - text and design ideas stay clean in every ready domain`() {
        val domains = mapOf(
            "marketing" to marketing(),
            "social_media" to social(),
            "programming" to programming(),
            "general" to general(),
        )
        domains.forEach { (domainId, kbObj) ->
            val kb = kbObj ?: return@forEach
            val textIdea = when (domainId) {
                "marketing" -> "تیتر تبلیغاتی برای فروشگاه آنلاین"
                "social_media" -> "کپشن انگیزشی صبحگاهی"
                "programming" -> "تابع جاوااسکریپت برای اعتبارسنجی فرم"
                else -> "پاسخ ساخت‌یافته به سؤال درباره تاریخ ایران"
            }
            val textOut = generate(textIdea, domainId, kb)
            assertCleanStructure(textOut)
            assertNoRawPersonaMetadata(textOut.text, kb)
            assertFalse("$domainId text idea became a calendar", textOut.text.contains("تقویم محتوایی"))

            val designIdea = "پوستر معرفی محصول جدید"
            val designOut = generate(designIdea, domainId, kb)
            assertTrue("$domainId design idea must yield a brief", designOut.text.contains("بریف"))
            assertFalse(designOut.text.contains("تقویم محتوایی"))
            assertNoLatinDigits(designOut.text)
        }
    }
}
