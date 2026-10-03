package com.promptsaz.app.data.export

import com.promptsaz.app.data.db.toDomain
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveDtoTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun samplePrompt() = ArchivedPrompt(
        id = 7L,
        groupId = "group-1",
        title = "کپی تبلیغ اینستاگرام",
        promptText = "نقش: کپی‌رایتر ارشد...\nقالب خروجی: جدول ۵ ردیفی",
        originalIdea = "یه تبلیغ برای فروش دوره آنلاین",
        domainId = "marketing",
        targetAi = TargetAi.CHATGPT,
        outputLanguage = OutputLanguage.PERSIAN,
        detailLevel = DetailLevel.STANDARD,
        score = 86,
        isFavorite = true,
        tags = listOf("فروش", "اینستاگرام"),
        notes = "نتیجه خوبی داد",
        isAiGenerated = false,
        variantStyle = "standard",
        createdAt = 1_760_000_000_000L,
        updatedAt = 1_760_000_500_000L,
    )

    @Test
    fun `export file serializes and parses back losslessly`() {
        val dto = samplePrompt().toEntity().toExportDto()
        val file = ArchiveExportFile(
            exportedAt = 1_760_001_000_000L,
            prompts = listOf(dto),
        )
        val text = json.encodeToString(ArchiveExportFile.serializer(), file)
        val back = json.decodeFromString(ArchiveExportFile.serializer(), text)

        assertEquals("PromptSaz", back.app)
        assertEquals(1, back.formatVersion)
        assertEquals(1, back.prompts.size)

        val prompt = back.prompts[0]
        assertEquals("کپی تبلیغ اینستاگرام", prompt.title)
        assertEquals(listOf("فروش", "اینستاگرام"), prompt.tags)
        assertEquals(86, prompt.score)
        assertEquals(true, prompt.isFavorite)
    }

    @Test
    fun `imported dto becomes a new row with a fresh id`() {
        val dto = samplePrompt().toEntity().toExportDto()
        val entity = dto.toEntity()
        assertEquals(0L, entity.id)

        val domain = entity.toDomain()
        assertEquals(samplePrompt().title, domain.title)
        assertEquals(TargetAi.CHATGPT, domain.targetAi)
        assertEquals(listOf("فروش", "اینستاگرام"), domain.tags)
    }

    @Test
    fun `api key material is not part of the export format`() {
        val fields = PromptExportDto.serializer()
            .descriptor
            .elementNames
            .toList()
        val suspicious = fields.filter {
            it.contains("key", ignoreCase = true) || it.contains("token", ignoreCase = true)
        }
        assertEquals(emptyList<String>(), suspicious)
    }
}
