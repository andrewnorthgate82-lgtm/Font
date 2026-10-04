package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.repository.SettingsRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
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
 * Proves the automatic 429 handling: a temporary rate limit (very common on
 * free Gemini keys) is retried with backoff instead of failing the whole
 * generation, and a persistent one surfaces the actionable Persian guidance —
 * including why /models still works while generateContent is limited.
 */
class ProviderRateLimitRetryTest {

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

    private val generateHits = AtomicInteger(0)

    /** how many 429s to answer before switching to success */
    @Volatile private var rateLimitedFirst = 2

    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        // fast retries in tests
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(10L, 10L)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            exchange.requestBody.readBytes()
            val hit = generateHits.incrementAndGet()
            if (hit <= rateLimitedFirst) {
                val err = """{"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details."}}"""
                val bytes = err.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(429, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                val reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"پاسخ دستیار"}]}}]}"""
                val bytes = reply.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server!!.start()

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
            secureKeyStore = FakeKeyStore().apply { keys["s1"] = "AIza-test-key-123" },
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(2_000L, 5_000L)
        server?.stop(0)
    }

    @Test
    fun `a temporary 429 is retried automatically and then succeeds`() = runBlocking {
        val result = provider!!.chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "سلام")),
        )

        assertTrue("chat failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("پاسخ دستیار", result.getOrNull())
        // two rate-limited answers + the successful third attempt
        assertEquals(3, generateHits.get())
    }

    @Test
    fun `a persistent 429 surfaces the actionable persian guidance`() = runBlocking {
        rateLimitedFirst = 100 // every attempt is rate-limited

        val result = provider!!.chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "سلام")),
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("should mention the separate models quota: $message", message.contains("سهمیهٔ جدایی"))
        assertTrue("should guide about the daily reset: $message", message.contains("ریست"))
        // 1 initial attempt + 2 automatic retries
        assertEquals(3, generateHits.get())
    }
}
