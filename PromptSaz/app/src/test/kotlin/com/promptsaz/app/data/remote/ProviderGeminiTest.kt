package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.provider.ImageGenerationUnsupportedException
import com.promptsaz.app.domain.provider.ProviderHealth
import com.promptsaz.app.domain.repository.SettingsRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the native Google Gemini chain (AI Studio services) against a real
 * local HTTP server: X-goog-api-key auth, {base}/models/{model}:generateContent
 * body shape (systemInstruction + contents + inlineData + maxOutputTokens),
 * reply parsing, the models endpoint and the image-unsupported fallback.
 */
class ProviderGeminiTest {

    private class FakeKeyStore : ApiKeyStore {
        val keys = mutableMapOf<String, String>()
        override fun hasApiKey(serviceId: String): Boolean = !keys[serviceId].isNullOrBlank()
        override fun getApiKey(serviceId: String): String? = keys[serviceId]
        override fun saveApiKey(serviceId: String, value: String) { keys[serviceId] = value }
        override fun clearApiKey(serviceId: String) { keys.remove(serviceId) }
    }

    private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDefaultDomain(domainId: String) {}
        override suspend fun setDefaultTarget(target: TargetAi) {}
        override suspend fun setDefaultLanguage(language: OutputLanguage) {}
        override suspend fun setDefaultDetail(level: DetailLevel) {}
        override suspend fun setAiEnabled(enabled: Boolean) {}
        override suspend fun setAiBaseUrl(url: String) {}
        override suspend fun setAiModel(model: String) {}
        override suspend fun setAiImageModel(model: String) {}
        override suspend fun addService(name: String, baseUrl: String, type: String): String = "svc-new"
        override suspend fun updateService(id: String, name: String, baseUrl: String, type: String) {}
        override suspend fun removeService(id: String) {}
        override suspend fun setActiveService(id: String) {}
        override suspend fun setModeService(modeId: String, serviceId: String) {}
        override suspend fun setServiceModel(serviceId: String, model: String, imageModel: Boolean) {}
    }

    @Volatile private var generatePath: String? = null
    @Volatile private var generateAuth: String? = null
    @Volatile private var generateBody: String? = null
    @Volatile private var modelsAuth: String? = null

    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            generatePath = exchange.requestURI.path
            generateAuth = exchange.requestHeaders.getFirst("X-goog-api-key")
            generateBody = exchange.requestBody.readBytes().decodeToString()
            val reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"پاسخ دستیار"}]}}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/v1beta/models") { exchange ->
            modelsAuth = exchange.requestHeaders.getFirst("X-goog-api-key")
            val reply = """
                {"models":[
                    {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent","countTokens"]},
                    {"name":"models/gemini-flash-latest-exp","supportedGenerationMethods":["generateContent"]},
                    {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
                ]}
            """.trimIndent()
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.start()

        val keyStore = FakeKeyStore().apply { keys["s1"] = "AIza-test-key-123" }
        provider = OpenAiCompatibleProvider(
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    aiEnabled = true,
                    aiServices = listOf(
                        AiService(
                            id = "s1",
                            name = "Google Gemini",
                            baseUrl = "http://127.0.0.1:${server!!.address.port}/v1beta",
                            type = AiService.TYPE_GEMINI,
                            model = "gemini-flash-latest",
                        ),
                    ),
                    activeServiceId = "s1",
                ),
            ),
            secureKeyStore = keyStore,
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `chat uses the native gemini format with the API key header and parses the reply`() = runBlocking {
        val dataUrl = "data:image/jpeg;base64," + "QUJDRA" // "ABCD"
        val result = provider!!.chat(
            model = "gemini-flash-latest",
            turns = listOf(
                ChatTurn(role = "system", text = "تو دستیار فارسی هستی."),
                ChatTurn(role = "user", text = "این عکس را تحلیل کن", imageDataUrl = dataUrl),
                ChatTurn(role = "assistant", text = "بله"),
                ChatTurn(role = "user", text = "ممنون"),
            ),
        )

        assertTrue("chat failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("پاسخ دستیار", result.getOrNull())

        // native endpoint + native auth header (no Bearer)
        assertEquals("/v1beta/models/gemini-flash-latest:generateContent", generatePath)
        assertEquals("AIza-test-key-123", generateAuth)

        val body = generateBody.orEmpty()
        assertTrue("systemInstruction missing: $body", body.contains("\"systemInstruction\""))
        assertTrue("system text missing: $body", body.contains("تو دستیار فارسی هستی."))
        assertTrue("image inlineData missing: $body", body.contains("\"inlineData\""))
        assertTrue("image mime missing: $body", body.contains("image/jpeg"))
        assertTrue("assistant must map to model role: $body", body.contains("\"role\":\"model\""))
        assertTrue("token cap missing: $body", body.contains("\"maxOutputTokens\":16384"))
    }

    @Test
    fun `listModels hits the gemini models endpoint and keeps only generateContent models`() = runBlocking {
        val result = provider!!.listModels()

        assertTrue("listModels failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(listOf("gemini-flash-latest", "gemini-flash-latest-exp"), result.getOrNull())
        assertEquals("AIza-test-key-123", modelsAuth)
    }

    @Test
    fun `connection test reports Ok with the gemini model list`() = runBlocking {
        val health = provider!!.testConnection()

        assertTrue("expected Ok, was $health", health is ProviderHealth.Ok)
        assertEquals(2, (health as ProviderHealth.Ok).models.size)
    }

    @Test
    fun `a 403 html page from google surfaces clean persian guidance instead of the raw html`() = runBlocking {
        server!!.removeContext("/v1beta/models/gemini-flash-latest:generateContent")
        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            exchange.requestBody.readBytes()
            val err = """
                <!DOCTYPE html>
                <html lang=en>
                <meta charset=utf-8>
                <title>Error 403 (Forbidden)!!1</title>
                <style>*{margin:0;padding:0}</style>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(403, err.size.toLong())
            exchange.responseBody.use { it.write(err) }
        }

        val result = provider!!.chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "سلام")),
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("should mention blocking: $message", message.contains("مسدود"))
        assertTrue("should guide about VPN: $message", message.contains("VPN"))
        assertTrue("should guide about Vertex preset: $message", message.contains("Vertex"))
        // only the title line survives — no raw html soup
        assertTrue("must not dump raw html: $message", !message.contains("<html"))
        assertTrue("title line kept: $message", message.contains("Error 403 (Forbidden)!!1"))
    }

    @Test
    fun `image generation is politely unsupported on gemini so the prompt fallback kicks in`() = runBlocking {
        val result = provider!!.generateImage("gemini-flash-latest", "a red fox", "1024x1024")

        assertTrue(result.isFailure)
        assertTrue(
            "expected unsupported: ${result.exceptionOrNull()}",
            result.exceptionOrNull() is ImageGenerationUnsupportedException,
        )
    }
}
