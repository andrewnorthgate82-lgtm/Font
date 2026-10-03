package com.promptsaz.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `chat completion response parses choices content`() {
        val text = """
            {"id":"x","object":"chat.completion","choices":[{"index":0,
            "message":{"role":"assistant","content":"{\"title\":\"تست\",\"prompt\":\"متن\"}"},
            "finish_reason":"stop"}],"usage":{"total_tokens":10}}
        """.trimIndent()
        val parsed = json.decodeFromString(ChatCompletionResponseDto.serializer(), text)
        assertEquals(
            "{\"title\":\"تست\",\"prompt\":\"متن\"}",
            parsed.choices.first().message?.content,
        )
    }

    @Test
    fun `models response parses ids and ignores extra fields`() {
        val text = """
            {"object":"list","data":[{"id":"gpt-4o","object":"model","owned_by":"x"},
            {"id":"gpt-4o-mini","object":"model"}]}
        """.trimIndent()
        val parsed = json.decodeFromString(ModelsResponseDto.serializer(), text)
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), parsed.data.map { it.id })
    }

    @Test
    fun `generation reply tolerates missing fields`() {
        val parsed = json.decodeFromString(GenerationReplyDto.serializer(), """{"title":null}""")
        assertEquals(null, parsed.title)
        assertEquals(null, parsed.prompt)
    }

    @Test
    fun `request serializes to the openai wire format`() {
        val request = ChatCompletionRequestDto(
            model = "gpt-4o-mini",
            messages = listOf(
                ChatMessageDto(role = "system", content = "SYS"),
                ChatMessageDto(role = "user", content = "Hello"),
            ),
            temperature = 0.7,
        )
        val encoded = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        assertTrue(encoded.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(encoded.contains("\"role\":\"system\""))
        assertTrue(encoded.contains("\"content\":\"Hello\""))
        assertTrue(encoded.contains("\"temperature\":0.7"))
    }
}
