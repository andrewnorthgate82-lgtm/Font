package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.provider.ImageGenerationUnsupportedException
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
 * Proves native Gemini image generation (nano-banana / gemini-2.5-flash-image)
 * against a real local HTTP server: the request goes to
 * {base}/models/{model}:generateContent with responseModalities [TEXT, IMAGE],
 * X-goog-api-key auth, and the reply's inlineData part is decoded as the image.
 * A text-only reply (non-image model) surfaces the Persian unsupported
 * fallback instead of a raw error.
 */
class ProviderGeminiImageTest {

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

    @Volatile private var imageAuth: String? = null
    @Volatile private var imageBody: String? = null
    @Volatile private var imagePath: String? = null

    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        // the image-capable model: replies with an inlineData part (base64 PNG)
        server!!.createContext("/v1beta/models/gemini-2.5-flash-image:generateContent") { exchange ->
            imagePath = exchange.requestURI.path
            imageAuth = exchange.requestHeaders.getFirst("X-goog-api-key")
            imageBody = exchange.requestBody.readBytes().decodeToString()
            val reply = """
                {"candidates":[{"content":{"role":"model","parts":[
                    {"inlineData":{"mimeType":"image/png","data":"RkFLRV9QTkdfQllURVM="}}
                ]}}]}
            """.trimIndent()
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        // a text-only model: replies with just text → must trigger the fallback
        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            exchange.requestBody.readBytes()
            val reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"توضیح متنی"}]}}]}"""
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
                            model = "gemini-2.5-flash-image",
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
    fun `nano-banana image generation uses native generateContent and decodes the inline part`() = runBlocking {
        val result = provider!!.generateImage(
            model = "gemini-2.5-flash-image",
            prompt = "گربهٔ نارنجی روی کاناپهٔ مخملی",
            size = "1024x1024",
        )

        assertTrue("generateImage failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        // native endpoint + native auth header (no Bearer, no /images/generations)
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", imagePath)
        assertEquals("AIza-test-key-123", imageAuth)

        // the request asked for image output and carried the prompt
        val body = imageBody.orEmpty()
        assertTrue("responseModalities missing: $body", body.contains("\"responseModalities\""))
        assertTrue("IMAGE modality missing: $body", body.contains("\"IMAGE\""))
        assertTrue("prompt missing: $body", body.contains("گربهٔ نارنجی روی کاناپهٔ مخملی"))

        // the base64 image bytes came back
        val generated = result.getOrNull()
        assertTrue("expected FromBase64, was $generated", generated is GeneratedImage.FromBase64)
        assertEquals("FAKE_PNG_BYTES", String((generated as GeneratedImage.FromBase64).decode()))
    }

    @Test
    fun `a text-only gemini model triggers the persian image-unsupported fallback`() = runBlocking {
        val result = provider!!.generateImage(
            model = "gemini-flash-latest",
            prompt = "گربهٔ نارنجی",
            size = "1024x1024",
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "expected ImageGenerationUnsupportedException, was $error",
            error is ImageGenerationUnsupportedException,
        )
        val message = error?.message.orEmpty()
        assertTrue("should name the image model: $message", message.contains("gemini-2.5-flash-image"))
    }
}

/** Decodes the base64 payload the way the تصویر tab's repository does (JVM-safe). */
private fun GeneratedImage.FromBase64.decode(): ByteArray =
    java.util.Base64.getMimeDecoder().decode(base64)
