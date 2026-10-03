package com.promptsaz.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the @Serializable knowledge-base models decode the exact JSON shape
 * shipped in assets (see also KbAssetFilesTest, which parses the real files).
 */
class KbModelsParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `domain knowledge parses with all sections`() {
        val text = """
        {
          "id": "marketing",
          "nameFa": "بازاریابی و تبلیغات",
          "status": "ready",
          "personas": [
            {"id": "growth", "titleFa": "متخصص رشد", "expertiseFa": "قیف فروش و تست A/B با تجربه طولانی", "whenToUseFa": "وقتی هدف عددی است"}
          ],
          "terminology": [
            {"termFa": "قلاب", "termEn": "Hook", "definitionFa": "سه ثانیه اول محتوا که مخاطب را نگه می‌دارد"}
          ],
          "outputStructures": [
            {"id": "aida", "titleFa": "AIDA", "descriptionFa": "ساختار متقاعدسازی برای متن تبلیغ", "templateFa": "توجه، علاقه، تمایل، اقدام به همین ترتیب"}
          ],
          "guardrails": [
            {"id": "no-hype", "doFa": "از اعداد واقعی برای مزیت استفاده کن", "dontFa": "صفت اغراق‌آمیز ننویس", "whyFa": "اعتماد مخاطب"}
          ],
          "failureModes": [
            {"id": "generic", "symptomFa": "متن برای هر محصولی صادق است", "fixFa": "مخاطب و مزیت را دقیق بده"}
          ],
          "examples": [
            {"id": "ex1", "titleFa": "نمونه", "targetAi": "chatgpt", "promptFa": "متن کامل پرامپت نمونه"}
          ],
          "clarifyingQuestions": [
            {"id": "audience", "questionFa": "مخاطب کیست؟", "priority": 1, "chips": [{"id": "b2c", "labelFa": "مصرف‌کننده"}], "allowFreeText": true, "gapKeywordsFa": ["مخاطب"]}
          ]
        }
        """.trimIndent()

        val kb = json.decodeFromString<DomainKnowledge>(text)

        assertEquals("marketing", kb.id)
        assertEquals(1, kb.personas.size)
        assertEquals("متخصص رشد", kb.personas[0].titleFa)
        assertEquals(1, kb.terminology.size)
        assertEquals(1, kb.outputStructures.size)
        assertEquals(1, kb.guardrails.size)
        assertEquals(1, kb.failureModes.size)
        assertEquals(1, kb.examples.size)
        assertEquals(1, kb.clarifyingQuestions.size)
        assertEquals("b2c", kb.clarifyingQuestions[0].chips[0].id)
    }

    @Test
    fun `unknown fields are ignored so kb files can evolve without code changes`() {
        val text = """
        {
          "id": "general",
          "nameFa": "عمومی",
          "status": "ready",
          "personas": [],
          "terminology": [],
          "outputStructures": [],
          "guardrails": [],
          "failureModes": [],
          "examples": [],
          "clarifyingQuestions": [],
          "futureField": {"anything": true}
        }
        """.trimIndent()

        val kb = json.decodeFromString<DomainKnowledge>(text)
        assertEquals("general", kb.id)
    }

    @Test
    fun `registry parses with ready and coming_soon entries`() {
        val text = """
        {
          "version": 1,
          "domains": [
            {"id": "general", "nameFa": "عمومی", "descriptionFa": "ایده‌های عمومی", "status": "ready", "kbFile": "general.json"},
            {"id": "ai_image", "nameFa": "تولید تصویر", "descriptionFa": "پرامپت تصویری", "status": "coming_soon"}
          ]
        }
        """.trimIndent()

        val registry = json.decodeFromString<KbRegistry>(text)

        assertEquals(1, registry.version)
        assertEquals(2, registry.domains.size)
        assertTrue(registry.domains[0].isReady)
        assertEquals("general.json", registry.domains[0].kbFile)
        assertTrue(!registry.domains[1].isReady)
        assertEquals(null, registry.domains[1].kbFile)
    }

    @Test
    fun `chips and keywords default to empty collections`() {
        val text = """
        {
          "id": "q", "questionFa": "سؤال؟", "priority": 2
        }
        """.trimIndent()

        val question = json.decodeFromString<KbClarifyingQuestion>(text)
        assertEquals(emptyList<KbChip>(), question.chips)
        assertEquals(emptyList<String>(), question.gapKeywordsFa)
        assertEquals(true, question.allowFreeText)
    }
}
