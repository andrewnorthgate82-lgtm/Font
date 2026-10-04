package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
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
 * Proves the native Pixazo image chain against a real local HTTP server:
 * Ocp-Apim-Subscription-Key auth, {base}/text-to-image submission with
 * {prompt, size}, async job polling until COMPLETED and the public image URL.
 */
class ProviderPixazoTest {

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
    }

    @Volatile private var submitAuth: String? = null
    @Volatile private var submitBody: String? = null
    @Volatile private var statusCalls: Int = 0
    @Volatile private var statusBodies: MutableList<String> = mutableListOf()
    private val imageBytes = byteArrayOf(9, 8, 7, 6)

    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val defaultPollInterval = OpenAiCompatibleProvider.pixazoPollIntervalMs

    @Before
    fun setUp() {
        OpenAiCompatibleProvider.pixazoPollIntervalMs = 20L
        statusCalls = 0
        statusBodies = mutableListOf()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server!!.address.port

        server!!.createContext("/gpt-image-2-5-flare/v1/text-to-image") { exchange ->
            submitAuth = exchange.requestHeaders.getFirst(PixazoWire.API_KEY_HEADER)
            submitBody = exchange.requestBody.readBytes().decodeToString()
            val reply = """{"request_id":"req-1","status":"QUEUED",""" +
                """"polling_url":"http://127.0.0.1:$port/v2/requests/status/req-1"}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(202, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/v2/requests/status/req-1") { exchange ->
            val call = ++statusCalls
            statusBodies += call.toString()
            val reply = if (call == 1) {
                """{"request_id":"req-1","status":"PROCESSING"}"""
            } else {
                """{"request_id":"req-1","status":"COMPLETED","error":null,""" +
                    """"output":{"media_url":["http://127.0.0.1:$port/output.png"],"media_type":"image/png"}}"""
            }
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/output.png") { exchange ->
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }

        server!!.start()

        provider = OpenAiCompatibleProvider(
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    aiEnabled = true,
                    aiServices = listOf(
                        AiService(
                            id = "s1",
                            name = "Pixazo",
                            baseUrl = "http://127.0.0.1:$port/gpt-image-2-5-flare/v1",
                            type = AiService.TYPE_PIXAZO,
                            model = "gpt-image-2-5-flare",
                        ),
                    ),
                    activeServiceId = "s1",
                ),
            ),
            secureKeyStore = FakeKeyStore().apply { keys["s1"] = "pixazo-key-42" },
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        OpenAiCompatibleProvider.pixazoPollIntervalMs = defaultPollInterval
        server?.stop(0)
    }

    @Test
    fun `image generation submits the pixazo job, polls it and returns the public url`() = runBlocking {
        val result = provider!!.generateImage("gpt-image-2-5-flare", "گربه روی کاناپه", "1024x1024")

        assertTrue("generation failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val generated = result.getOrNull()
        assertTrue("expected url result: $generated", generated is GeneratedImage.FromUrl)

        // native auth header (Ocp-Apim-Subscription-Key, no Bearer)
        assertEquals("pixazo-key-42", submitAuth)
        // submit body carries prompt + size in the documented format
        val body = submitBody.orEmpty()
        assertTrue("prompt missing: $body", body.contains("\"prompt\":\"گربه روی کاناپه\""))
        assertTrue("size missing: $body", body.contains("\"size\":\"1024x1024\""))
        // the job was polled until COMPLETED (first PROCESSING, then image)
        assertTrue("expected 2 status calls, was $statusCalls", statusCalls >= 2)
        // the returned url downloads without auth
        val bytes = provider!!.fetchImageBytes((generated as GeneratedImage.FromUrl).url)
        assertTrue(bytes.isSuccess)
        assertTrue(bytes.getOrNull()!!.contentEquals(imageBytes))
    }

    @Test
    fun `a failed pixazo job surfaces the persian error with the server reason`() = runBlocking {
        server!!.removeContext("/v2/requests/status/req-1")
        server!!.createContext("/v2/requests/status/req-1") { exchange ->
            exchange.requestBody.readBytes()
            val reply = """{"request_id":"req-1","status":"FAILED","error":"content policy","output":null}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = provider!!.generateImage("gpt-image-2-5-flare", "test", "1024x1024")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("persian failure expected: $message", message.contains("ناموفق"))
        assertTrue("server reason expected: $message", message.contains("content policy"))
    }

    @Test
    fun `listModels surfaces the model parsed from the service url`() = runBlocking {
        val result = provider!!.listModels()

        assertTrue(result.isSuccess)
        assertEquals(listOf("gpt-image-2-5-flare"), result.getOrNull())
    }

    @Test
    fun `chat on a pixazo service guides the user to a chat service`() = runBlocking {
        val result = provider!!.chat(
            model = "gpt-image-2-5-flare",
            turns = listOf(ChatTurn(role = "user", text = "سلام")),
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("guidance expected: $message", message.contains("فقط ساخت تصویر"))
    }

    @Test
    fun `url helpers build the documented shapes`() {
        assertEquals(
            "https://gateway.pixazo.ai/gpt-image-2-5-flare/v1/text-to-image",
            PixazoWire.textToImageUrl("https://gateway.pixazo.ai/gpt-image-2-5-flare/v1/"),
        )
        assertEquals(
            "https://gateway.pixazo.ai/v2/requests/status/req-9",
            PixazoWire.statusUrl("", "req-9", "https://gateway.pixazo.ai/gpt-image-2-5-flare/v1"),
        )
        assertEquals(
            "http://poll.example/req-1",
            PixazoWire.statusUrl("http://poll.example/req-1", "req-1", "https://gateway.pixazo.ai/x/v1"),
        )
        assertEquals("gpt-image-2-5-flare", PixazoWire.parseModelId("https://gateway.pixazo.ai/gpt-image-2-5-flare/v1"))
        assertEquals("gpt-image-2", PixazoWire.parseModelId("https://gateway.pixazo.ai/gpt-image-2/v1/text-to-image"))
        assertTrue(PixazoWire.isCompleted("COMPLETED"))
        assertTrue(PixazoWire.isFailed("ERROR"))
    }
}
