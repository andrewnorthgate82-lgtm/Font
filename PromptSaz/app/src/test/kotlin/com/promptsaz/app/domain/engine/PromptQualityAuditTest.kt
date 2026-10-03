package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.engine.facts.IdeaFactsExtractor
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
 *  - facts are extracted cleanly per label: no field glued to the next, no
 *    leftover connector words («تاریخ از»), identical result for the same
 *    idea written multi-line, single-line, with or without colons
 *  - the role, output format, constraints, quality criteria and example all
 *    match the requested deliverable (a poster is a designer brief; an image
 *    prompt is English, 60–150 words, with a separate Persian text layer and
 *    a negative list; never a content calendar unless asked)
 *  - the user's confirmed facts appear structured and are never re-asked;
 *    missing info stays a [NAMED] placeholder and is never invented
 *  - no contradiction, no duplicated rules, no meaningless or half sentences,
 *    no raw knowledge-base metadata, no nested parentheses, and consistent
 *    Persian digits in Persian text
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

    /** Lines of the «اطلاعات قطعی» block ("- label: value") inside the context section. */
    private fun factLines(output: GeneratedPrompt): List<String> {
        val context = output.sections.first { it.titleFa == "زمینه" }.body
        val lines = mutableListOf<String>()
        var inFacts = false
        context.lines().forEach { line ->
            when {
                line.startsWith("اطلاعات قطعی") -> inFacts = true
                inFacts && line.startsWith("پاسخ‌های کاربر") -> inFacts = false
                inFacts && line.startsWith("- ") && line.contains(": ") -> lines += line
            }
        }
        return lines
    }

    private fun sectionBody(output: GeneratedPrompt, title: String): String =
        output.sections.firstOrNull { it.titleFa.startsWith(title) }?.body ?: ""

    private fun assertNoLatinDigits(prompt: String) {
        prompt.lines().forEach { line ->
            if (line.contains("--")) return@forEach // image-tool parameters
            val latin = line.count { it in 'a'..'z' || it in 'A'..'Z' }
            val persian = line.count { it in '\u0600'..'\u06FF' }
            if (latin > persian) return@forEach // English image-prompt paragraph
            val cleaned = line.replace(Regex("\\[[A-Za-z0-9_ ]+\\]"), "[]")
            val match = Regex("[0-9]").find(cleaned)
            assertNull(
                "Latin digit in Persian prompt near: ${line.take(80)}",
                match,
            )
        }
    }

    private fun countOccurrences(text: String, fragment: String): Int =
        text.split(fragment).size - 1

    private val allHeaders = listOf(
        "## نقش", "## هدف", "## زمینه", "## ورودی‌ها و متغیرها", "## روند کار",
        "## قالب خروجی", "## محدودیت‌ها", "## مثال‌ها", "## معیارهای کیفیت", "## قاعده شفاف‌سازی",
    )

    private fun assertCleanStructure(output: GeneratedPrompt) {
        assertFalse("prompt must not contain an ellipsis", output.text.contains("…") || output.text.contains("..."))
        assertNoLatinDigits(output.text)
        allHeaders.forEach { header -> assertTrue("missing $header", output.text.contains(header)) }
        // constraints: no duplicated rule lines
        val constraintLines = sectionBody(output, "محدودیت‌ها").lines().filter { it.isNotBlank() }
        assertEquals(
            "duplicated constraint lines",
            constraintLines.size,
            constraintLines.distinct().size,
        )
        // no nested parentheses anywhere
        assertFalse(
            "nested parentheses: " + Regex("\\([^()]*\\(").find(output.text)?.value,
            Regex("\\([^()]*\\(").containsMatchIn(output.text),
        )
        // every numbered quality criterion ends with a period
        val criteria = sectionBody(output, "معیارهای کیفیت").lines()
            .filter { Regex("^[۰-۹]\\)").containsMatchIn(it.trim()) }
        criteria.forEach { criterion ->
            assertTrue("criterion missing final period: $criterion", criterion.trim().endsWith("."))
        }
    }

    /** No fact value may contain the next field's label or trailing connectors. */
    private fun assertFactsSeparated(output: GeneratedPrompt) {
        val labels = listOf(
            "موضوع", "مدرس", "تاریخ", "مدت", "ساعت", "مکان", "بستر انتشار",
            "قیمت", "مخاطب", "راه ثبت‌نام", "شیوه برگزاری", "نوع رویداد",
        )
        val lines = factLines(output)
        assertTrue("no facts extracted", lines.isNotEmpty())
        lines.forEach { line ->
            val value = line.removePrefix("- ").substringAfter(": ").trim()
            assertTrue("empty fact value: $line", value.isNotEmpty())
            labels.forEach { other ->
                assertFalse("field glued to next label ($other): $line", value.startsWith("$other "))
            }
            listOf("از", "به", "تا", "و", "برای").forEach { connector ->
                assertFalse("trailing connector in fact value: $line", value == connector || value.endsWith(" $connector"))
            }
        }
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

    // --- 0. fact extraction across writing styles (user mandate الف) -------------

    private val regressionMultiLine = """
        می‌خواهم برای دوره آموزشی خودم یک پوستر طراحی کنم.
        موضوع: آموزش هوش مصنوعی
        مدرس: محسن ابوطالبیان
        تاریخ: از ۱۱ مهر به مدت ۳ روز
        بستر تبلیغ و انتشار: اینستاگرام
    """.trimIndent()

    private val regressionSingleLine =
        "میخواهیم برای دوره آموزشی خودم یک پوستر طراحی کنم موضوع آموزش هوش مصنوعی " +
            "مدرس محسن ابوطالبیان تاریخ از ۱۱ مهر به مدت ۳ روز بستر تبلیغ و انتشار اینستاگرام هست"

    private fun factsOf(idea: String): Map<String, String> =
        IdeaFactsExtractor.extract(idea).entries.associate { it.labelFa to it.valueFa }

    @Test
    fun `facts - multi-line with colons extracts clean fields`() {
        val facts = factsOf(regressionMultiLine)
        assertEquals("آموزش هوش مصنوعی", facts["موضوع"])
        assertEquals("محسن ابوطالبیان", facts["مدرس"])
        assertEquals("۱۱ مهر", facts["تاریخ"])
        assertEquals("۳ روز", facts["مدت"])
        assertEquals("اینستاگرام", facts["بستر انتشار"])
        assertEquals("دوره آموزشی", facts["نوع رویداد"])
    }

    @Test
    fun `facts - same idea collapsed to one line without punctuation extracts identically`() {
        val multi = factsOf(regressionMultiLine)
        val single = factsOf(regressionSingleLine)
        listOf("موضوع", "مدرس", "تاریخ", "مدت", "بستر انتشار", "نوع رویداد").forEach { label ->
            assertEquals("fact [$label] differs between multi-line and single-line", multi[label], single[label])
        }
        assertEquals("۱۱ مهر", single["تاریخ"])
        assertEquals("محسن ابوطالبیان", single["مدرس"])
    }

    @Test
    fun `facts - label-free extras (time, place, mode, registration, price) are captured`() {
        val idea = "پوستر سمینار موضوع مدیریت زمان مدرس رضا محمدی تاریخ ۲۰ آبان " +
            "ساعت ۱۰ صبح مکان تهران حضوری ثبت‌نام در site.com قیمت رایگان"
        val facts = factsOf(idea)
        assertEquals("۱۰ صبح", facts["ساعت"])
        assertEquals("تهران", facts["مکان"])
        assertEquals("حضوری", facts["شیوه برگزاری"])
        assertEquals("site.com", facts["راه ثبت‌نام"])
        assertEquals("رایگان", facts["قیمت"])
    }

    // --- 1. the user's regression case -------------------------------------------

    @Test
    fun `regression - course poster routes to a designer brief with clean facts`() {
        val kb = marketing() ?: return
        val asked = clarificationEngine.questionsFor(kb, regressionMultiLine).map { it.id }
        // The five poster/event questions are asked; nothing already said is re-asked.
        assertTrue("deliverable question must be asked", asked.contains("deliverable-type"))
        assertTrue("حضوری/آنلاین must be asked", asked.contains("event-mode"))
        assertTrue("ثبت‌نام must be asked", asked.contains("registration"))
        assertTrue("ساعت must be asked", asked.contains("event-time"))
        assertTrue("سبک و رنگ must be asked", asked.contains("style-colors"))
        assertFalse("topic already given", asked.contains("product-service"))
        assertFalse("platform already given", asked.contains("platform"))

        val output = generate(regressionMultiLine, "marketing", kb)

        assertTrue("role must be a designer", output.text.contains("کارگردان هنری"))
        assertFalse("wrong persona leaked", output.text.contains("گوگل ادز"))
        assertTrue("objective must promise a design brief", output.text.contains("بریف کامل طراحی"))
        assertTrue(output.text.contains("بریف طراحی برای طراح گرافیک"))
        assertFalse("calendar must not appear for a poster", output.text.contains("تقویم محتوایی"))

        // Confirmed facts, cleanly separated
        assertTrue("facts block missing", output.text.contains("اطلاعات قطعی"))
        assertFactsSeparated(output)
        assertTrue(output.text.contains("مدرس: محسن ابوطالبیان"))
        assertTrue(output.text.contains("تاریخ: ۱۱ مهر"))
        assertTrue(output.text.contains("مدت: ۳ روز"))
        assertTrue(output.text.contains("بستر انتشار: اینستاگرام"))
        assertTrue(output.text.contains("موضوع: آموزش هوش مصنوعی"))

        // No repetition, no placeholders for known facts, no fabrication order
        assertEquals("idea sentence repeated", 1, countOccurrences(output.text, "پوستر طراحی کنم"))
        assertFalse("topic must not become a placeholder", output.text.contains("[TOPIC_OR_PRODUCT]"))
        assertFalse("platform must not become a placeholder", output.text.contains("[PLATFORM]"))
        assertFalse("old fabrication order must be gone", output.text.contains("هیچ جای خالی رها نکن"))
        assertTrue("no-fabrication rule expected", output.text.contains("از خودت نساز"))

        // Relevant, complete, same-kind example
        assertTrue("poster example expected", output.text.contains("سارا رستمی"))
        assertFalse("irrelevant example", output.text.contains("A/B"))

        // Quality criterion is a model-facing instruction, not prompt-meta text
        assertTrue(output.text.contains(kb.failureModes.first().fixFa))
        assertFalse("meta phrasing leaked", output.text.contains("در پرامپت،"))

        assertCleanStructure(output)
        assertNoRawPersonaMetadata(output.text, kb)
    }

    @Test
    fun `regression - single-line variant produces the same clean output`() {
        val kb = marketing() ?: return
        val output = generate(regressionSingleLine, "marketing", kb)
        assertFactsSeparated(output)
        assertTrue(output.text.contains("مدرس: محسن ابوطالبیان"))
        assertTrue(output.text.contains("موضوع: آموزش هوش مصنوعی"))
        assertTrue(output.text.contains("بریف کامل طراحی"))
        assertCleanStructure(output)
    }

    // --- 2. image-prompt deliverable (user mandate ب) ------------------------------

    @Test
    fun `image deliverable - english prompt, text layer, negatives, same-kind example`() {
        val kb = marketing() ?: return
        val spec = skippedSpec(regressionMultiLine, "marketing", kb).copy(
            answers = skippedSpec(regressionMultiLine, "marketing", kb).answers +
                ClarifyingAnswer("deliverable-type", "خروجی چه باشد؟", "پرامپت برای ساخت تصویر"),
        )
        val output = engine.generate(spec, kb)

        assertTrue("image deliverable expected", output.text.contains("پرامپت آمادهٔ ساخت تصویر"))
        assertTrue("english instruction expected", output.text.contains("به انگلیسی"))
        assertTrue("60–150 word length expected", output.text.contains("۶۰ تا ۱۵۰"))
        assertTrue("text layer section expected", output.text.contains("لایهٔ متن"))
        assertTrue("empty-space instruction expected", output.text.contains("جای خالی"))
        assertTrue("negative list expected", output.text.contains("بدون هیچ متن"))
        assertTrue("garbled-letters negative expected", output.text.contains("حروف به‌هم‌ریخته"))
        assertFalse("tone is meaningless for image prompts", output.text.contains("لحن:"))
        assertFalse("brief example must not be offered", output.text.contains("ساختار: بریف طراحی برای طراح گرافیک"))
        assertTrue("image example (with --ar) expected", output.text.contains("--ar"))
        assertTrue("image quality criterion expected", output.text.contains("جای متن‌ها خالی بماند"))
        assertFalse(
            "copywriting fix must not be a criterion for image prompts",
            output.text.contains(kb.failureModes.first().fixFa),
        )
        assertNoLatinDigits(output.text)
    }

    @Test
    fun `midjourney target - parameters and a Persian text layer are appended`() {
        val kb = marketing() ?: return
        val spec = skippedSpec(regressionMultiLine, "marketing", kb)
            .copy(targetAi = TargetAi.MIDJOURNEY)
        val output = engine.generate(spec, kb)
        assertTrue("--ar expected", output.text.contains("--ar"))
        assertTrue("strong negative list expected", output.text.contains("--no text, watermark, logo, letters"))
        assertTrue("text layer from facts expected", output.text.contains("لایهٔ متن"))
        assertTrue(output.text.contains("مدرس: محسن ابوطالبیان"))
    }

    // --- 3. deliverable answers switch the output type ----------------------------

    @Test
    fun `deliverable answer controls the output type`() {
        val kb = marketing() ?: return
        val textSpec = skippedSpec(regressionMultiLine, "marketing", kb).copy(
            answers = listOf(ClarifyingAnswer("deliverable-type", "خروجی چه باشد؟", "متن و ساختار")),
        )
        val textOut = engine.generate(textSpec, kb)
        assertTrue("expected a text deliverable", textOut.text.contains("متن ساخت‌یافتهٔ کامل"))
        assertFalse(
            "brief format must not appear for a text deliverable",
            textOut.text.contains("ساختار: بریف طراحی برای طراح گرافیک"),
        )
    }

    // --- 4. marketing domain --------------------------------------------------------

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

    // --- 5. social media domain --------------------------------------------------------

    @Test
    fun `social - cover design routes to designer`() {
        val kb = social() ?: return
        val output = generate("کاور پیج اینستاگرام برای کافه‌ام می‌خوام", "social_media", kb)
        assertTrue("designer role expected", output.text.contains("طراح گرافیک"))
        assertTrue(output.text.contains("بریف کامل طراحی"))
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
        assertCleanStructure(output)
    }

    // --- 6. programming domain -----------------------------------------------------------

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
    fun `programming - short bugfix idea uses the investigation structure`() {
        val kb = programming() ?: return
        val output = generate("فرم ثبت‌نام من خطا می‌دهد، رفع باگ کن", "programming", kb)
        assertTrue("bug investigation structure expected", output.text.contains("بررسی سیستماتیک"))
        assertCleanStructure(output)
    }

    // --- 7. general domain ------------------------------------------------------------------

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
    fun `general - graduation poster routes to design and asks poster questions`() {
        val kb = general() ?: return
        val idea = "پوستر جشن فارغ‌التحصیلی دانشجویان می‌خواهم"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertTrue("style question expected", asked.contains("style-colors"))
        assertTrue("registration question expected", asked.contains("registration"))
        val output = generate(idea, "general", kb)
        assertTrue("designer role expected", output.text.contains("کارگردان هنری"))
        assertTrue(output.text.contains("بریف کامل طراحی"))
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
        assertFalse("broken sentence must be gone", output.text.contains("می‌دانستنی نیست"))
    }

    @Test
    fun `general - no-fabrication rule appears at most once in constraints`() {
        val kb = general() ?: return
        val output = generate("پاسخ ساخت‌یافته به سؤال درباره تاریخ ایران", "general", kb)
        assertTrue(
            "duplicated no-fabrication rules",
            sectionBody(output, "محدودیت‌ها").split("از خودت نساز").size - 1 <= 1,
        )
    }

    // --- 8. image routing ----------------------------------------------------------------------

    @Test
    fun `image model mention routes to an image prompt without asking`() {
        val kb = general() ?: return
        val idea = "با میدجرنی یک پوستر کافه بسازم"
        val asked = clarificationEngine.questionsFor(kb, idea).map { it.id }
        assertFalse("image model is explicit", asked.contains("deliverable-type"))
        val output = generate(idea, "general", kb)
        assertTrue("image prompt deliverable expected", output.text.contains("پرامپت آمادهٔ ساخت تصویر"))
        assertTrue("text layer expected", output.text.contains("لایهٔ متن"))
        assertTrue("image example expected", output.text.contains("--ar"))
        assertCleanStructure(output)
    }

    // --- 9. sweep: every domain, short + text + detail-rich design -----------------------------

    @Test
    fun `sweep - every ready domain stays clean for text, design and detail-rich ideas`() {
        val domains = mapOf(
            "marketing" to marketing(),
            "social_media" to social(),
            "programming" to programming(),
            "general" to general(),
        )
        val detailRich = mapOf(
            "marketing" to "پوستر دوره آموزشی موضوع طراحی سایت مدرس مریم احمدی تاریخ ۵ آذر ساعت ۱۷ مکان اصفهان حضوری ثبت‌نام در register.example قیمت ۸۰۰ هزار تومان",
            "social_media" to "پوستر معرفی محصول جدید کافه موضوع نوشیدنی زمستانی مخاطب جوانان تهران تاریخ ۱۰ دی",
            "programming" to "پوستر معرفی اپ موبایل موضوع مدیریت هزینه مدرس تیم محصول",
            "general" to "پوستر جشنواره موسیقی موضوع موسیقی سنتی تاریخ ۲۲ بهمن ساعت ۱۹ مکان تالار وحدت",
        )
        val textIdea = mapOf(
            "marketing" to "تیتر تبلیغاتی برای فروشگاه آنلاین",
            "social_media" to "کپشن انگیزشی صبحگاهی",
            "programming" to "تابع جاوااسکریپت برای اعتبارسنجی فرم",
            "general" to "پاسخ ساخت‌یافته به سؤال درباره تاریخ ایران",
        )
        domains.forEach { (domainId, kbObj) ->
            val kb = kbObj ?: return@forEach
            // detail-rich design idea: fields separated, brief deliverable
            val designOut = generate(detailRich.getValue(domainId), domainId, kb)
            assertFactsSeparated(designOut)
            assertNoLatinDigits(designOut.text)
            assertTrue("$domainId design idea must yield a brief", designOut.text.contains("بریف"))
            assertFalse(designOut.text.contains("تقویم محتوایی"))
            // plain text idea: clean structure, no calendar
            val textOut = generate(textIdea.getValue(domainId), domainId, kb)
            assertCleanStructure(textOut)
            assertNoRawPersonaMetadata(textOut.text, kb)
            assertFalse("$domainId text idea became a calendar", textOut.text.contains("تقویم محتوایی"))
            // short idea: still generates cleanly
            val shortOut = generate("کمک کن", domainId, kb)
            assertNoLatinDigits(shortOut.text)
        }
    }
}
