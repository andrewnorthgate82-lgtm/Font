package com.promptsaz.app.data.remote

import com.promptsaz.app.domain.model.ChatTurn
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure mapping tests for the native Gemini wire format. */
class GeminiWireTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `system turns merge into systemInstruction and roles map to user and model`() {
        val request = GeminiWire.fromTurns(
            listOf(
                ChatTurn(role = "system", text = "سیستم"),
                ChatTurn(role = "system", text = "یادداشت دوم"),
                ChatTurn(role = "user", text = "سلام"),
                ChatTurn(role = "assistant", text = "درود"),
            ),
            maxOutputTokens = 4096,
        )

        assertEquals(2, request.systemInstruction?.parts?.size)
        assertEquals("سیستم", request.systemInstruction?.parts?.first()?.text)
        assertEquals("user", request.contents[0].role)
        assertEquals("model", request.contents[1].role)
        assertEquals(4096, request.generationConfig?.maxOutputTokens)
    }

    @Test
    fun `image data urls become inlineData parts`() {
        val request = GeminiWire.fromTurns(
            listOf(ChatTurn(role = "user", text = "ببین", imageDataUrl = "data:image/jpeg;base64,QUJDRA==")),
            maxOutputTokens = null,
        )

        // text + image together → two parts in one content
        val parts = request.contents.single().parts
        assertEquals(2, parts.size)
        assertEquals("ببین", parts.first { it.text != null }.text)
        val image = parts.first { it.inlineData != null }.inlineData
        assertEquals("image/jpeg", image?.mimeType)
        assertEquals("QUJDRA==", image?.data)
    }

    @Test
    fun `malformed data urls degrade to text only instead of crashing`() {
        val request = GeminiWire.fromTurns(
            listOf(ChatTurn(role = "user", text = "سلام", imageDataUrl = "not-a-data-url")),
            maxOutputTokens = null,
        )

        assertEquals(1, request.contents.single().parts.size)
        assertEquals("سلام", request.contents.single().parts.single().text)
    }

    @Test
    fun `splitDataUrl handles the shapes we produce`() {
        assertEquals("image/png" to "AAAA", GeminiWire.splitDataUrl("data:image/png;base64,AAAA"))
        assertNull(GeminiWire.splitDataUrl("https://example.com/img.png"))
        assertNull(GeminiWire.splitDataUrl("data:,"))
    }

    @Test
    fun `replyText extracts the first text part and tolerates empty replies`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"سلام"}]}}]}"""
        assertEquals("سلام", GeminiWire.replyText(body, json))
        assertNull(GeminiWire.replyText("""{"candidates":[]}""", json))
        assertNull(GeminiWire.replyText("not json", json))
    }

    @Test
    fun `parseModelNames strips the prefix and filters non-chat models`() {
        val body = """
            {"models":[
                {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent"]},
                {"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]},
                {"name":"models/no-methods-listed"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf("gemini-flash-latest", "no-methods-listed"),
            GeminiWire.parseModelNames(body, json),
        )
    }

    @Test
    fun `urls are built from the trimmed base`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            GeminiWire.generateContentUrl("https://generativelanguage.googleapis.com/v1beta/", "gemini-flash-latest"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models",
            GeminiWire.modelsUrl("https://generativelanguage.googleapis.com/v1beta/"),
        )
    }
}
