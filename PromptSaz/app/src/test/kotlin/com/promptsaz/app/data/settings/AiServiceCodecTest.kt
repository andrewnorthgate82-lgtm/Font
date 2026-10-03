package com.promptsaz.app.data.settings

import com.promptsaz.app.domain.model.AiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The services list is stored as JSON in DataStore; the codec must round-trip
 * it and the v1 single-service settings must become the first service.
 */
class AiServiceCodecTest {

    @Test
    fun `service lists round-trip through the codec`() {
        val services = listOf(
            AiService(id = "default", name = "codecraftapi.com", baseUrl = "https://codecraftapi.com/v1", model = "gemini-3.7-flash"),
            AiService(id = "svc-ab12", name = "OpenAI", baseUrl = "https://api.openai.com/v1", model = "gpt-4o-mini", imageModel = "dall-e-3"),
        )

        val decoded = AiServiceCodec.decode(AiServiceCodec.encode(services))

        assertEquals(services, decoded)
    }

    @Test
    fun `decode returns null for corrupt input instead of crashing`() {
        assertNull(AiServiceCodec.decode("not json at all {"))
    }

    @Test
    fun `v1 single-service settings become the default service`() {
        val service = AiServiceCodec.defaultFromLegacy(
            baseUrl = "https://codecraftapi.com/v1/",
            model = " gemini-3.7-flash ",
            imageModel = "",
        )

        assertEquals(AiService.LEGACY_DEFAULT_ID, service.id)
        // named after the host, trailing slash trimmed, model trimmed
        assertEquals("codecraftapi.com", service.name)
        assertEquals("https://codecraftapi.com/v1", service.baseUrl)
        assertEquals("gemini-3.7-flash", service.model)
        assertEquals("", service.imageModel)
    }

    @Test
    fun `host extraction handles www and junk`() {
        assertEquals("api.openai.com", AiServiceCodec.hostOf("https://api.openai.com/v1"))
        assertEquals("openrouter.ai", AiServiceCodec.hostOf("https://www.openrouter.ai/api/v1"))
        assertNull(AiServiceCodec.hostOf("not a url"))
    }

    @Test
    fun `legacy default id keeps its key readable through the per-service store`() {
        // the migrated service must be able to read the v1 key: id is stable
        assertTrue(AiServiceCodec.defaultFromLegacy("https://x.com/v1", "", "").id == "default")
    }
}
