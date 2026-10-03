package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.provider.ProviderHealth
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.domain.model.TargetAi
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end check of the AI generation chain, per the user's request
 * («مطمئن شو بعد از انتخاب نام مدل موتور تولید به مدل انتخاب‌شده وصل می‌شود»):
 * a real local HTTP server receives the request and we assert that the model
 * the user selected in Settings is the model actually sent to the server.
 */
class ProviderGenerationChainTest {

    // --- fakes -------------------------------------------------------------------

    private class FakeKeyStore(private val key: String?) : ApiKeyStore {
        override fun hasApiKey(serviceId: String): Boolean = !key.isNullOrBlank()
        override fun getApiKey(serviceId: String): String? = key
        override fun saveApiKey(serviceId: String, value: String) {}
        override fun clearApiKey(serviceId: String) {}
    }

    private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow
        override suspend fun setThemeMode(mode: ThemeMode) { flow.value = flow.value.copy(themeMode = mode) }
        override suspend fun setDefaultDomain(domainId: String) { flow.value = flow.value.copy(defaultDomainId = domainId) }
        override suspend fun setDefaultTarget(target: TargetAi) { flow.value = flow.value.copy(defaultTargetAi = target) }
        override suspend fun setDefaultLanguage(language: OutputLanguage) { flow.value = flow.value.copy(defaultOutputLanguage = language) }
        override suspend fun setDefaultDetail(level: DetailLevel) { flow.value = flow.value.copy(defaultDetailLevel = level) }
        override suspend fun setAiEnabled(enabled: Boolean) { flow.value = flow.value.copy(aiEnabled = enabled) }
        override suspend fun setAiBaseUrl(url: String) { updateActiveService { it.copy(baseUrl = url) } }
        override suspend fun setAiModel(model: String) { updateActiveService { it.copy(model = model) } }
        override suspend fun setAiImageModel(model: String) { updateActiveService { it.copy(imageModel = model) } }
        override suspend fun addService(name: String, baseUrl: String): String = "svc-new"
        override suspend fun updateService(id: String, name: String, baseUrl: String) {}
        override suspend fun removeService(id: String) {}
        override suspend fun setActiveService(id: String) { flow.value = flow.value.copy(activeServiceId = id) }

        private fun updateActiveService(block: (AiService) -> AiService) {
            val current = flow.value
            flow.value = current.copy(
                aiServices = current.aiServices.map { if (it.id == current.activeServiceId) block(it) else it },
            )
        }
    }

    // --- captured request ----------------------------------------------------------

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val body: String,
    )

    @Volatile
    private var captured: CapturedRequest? = null
    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null
    private var settings: FakeSettingsRepository? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        // The chat-completions endpoint: capture method/path/auth/body, reply like a model.
        server!!.createContext("/v1/chat/completions") { exchange ->
            captured = CapturedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().decodeToString(),
            )
            val reply = """
                {"id":"x","object":"chat.completion","choices":[{"index":0,
                "message":{"role":"assistant","content":"{\"title\":\"پوستر دوره\",\"prompt\":\"متن کامل پرامپت برای تست زنجیرهٔ تولید که باید عیناً به کاربر برگردد.\"}"},
                "finish_reason":"stop"}],"usage":{"total_tokens":10}}
            """.trimIndent()
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/v1/models") { exchange ->
            captured = CapturedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().decodeToString(),
            )
            val reply = """{"object":"list","data":[{"id":"model-a"},{"id":"model-b"}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server!!.start()

        settings = FakeSettingsRepository(
            AppSettings(
                aiEnabled = true,
                aiServices = listOf(
                    AiService(
                        id = "s1",
                        name = "تست",
                        baseUrl = "http://127.0.0.1:${server!!.address.port}/v1",
                        model = "gpt-selected-by-user",
                    ),
                ),
                activeServiceId = "s1",
            ),
        )
        provider = OpenAiCompatibleProvider(
            settingsRepository = settings!!,
            secureKeyStore = FakeKeyStore("test-key-123"),
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun spec() = PromptSpec(
        idea = "پوستر دوره آموزشی هوش مصنوعی",
        domainId = "marketing",
        targetAi = TargetAi.CHATGPT,
        outputLanguage = OutputLanguage.PERSIAN,
        detailLevel = DetailLevel.STANDARD,
    )

    @Test
    fun `generation connects to the model selected in settings`() {
        val result = runBlocking { provider!!.generatePrompt(spec(), kb = null) }

        assertTrue("generation failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val request = captured
        assertTrue("no request reached the server", request != null)

        // The model the user picked in Settings is the model sent to the server.
        assertTrue(
            "selected model not in request body: ${request!!.body.take(200)}",
            request.body.contains("\"model\":\"gpt-selected-by-user\""),
        )
        // Standard OpenAI-compatible wire format: POST to {base}/chat/completions.
        assertEquals("POST", request.method)
        assertEquals("/v1/chat/completions", request.path)
        // Bearer auth with the stored key.
        assertEquals("Bearer test-key-123", request.authorization)
        // The model reply is parsed into the app's generation shape.
        assertEquals("پوستر دوره", result.getOrThrow().title)
        assertTrue(result.getOrThrow().prompt.contains("زنجیرهٔ تولید"))
    }

    @Test
    fun `generation requests carry a max_tokens cap`() {
        val result = runBlocking { provider!!.generatePrompt(spec(), kb = null) }

        assertTrue(result.isSuccess)
        assertTrue(
            "max_tokens missing: ${captured!!.body.take(200)}",
            captured!!.body.contains("\"max_tokens\":4096"),
        )
    }

    @Test
    fun `changing the selected model changes the request`() {
        runBlocking { settings!!.setAiModel("claude-other-model") }
        val result = runBlocking { provider!!.generatePrompt(spec(), kb = null) }
        assertTrue(result.isSuccess)
        assertTrue(
            "updated model not sent: ${captured!!.body.take(200)}",
            captured!!.body.contains("\"model\":\"claude-other-model\""),
        )
    }

    @Test
    fun `connection test hits the models endpoint without needing a model`() {
        runBlocking { settings!!.setAiModel("") } // user has NOT picked a model yet
        val health = runBlocking { provider!!.testConnection() }
        assertTrue("expected Ok, was $health", health is ProviderHealth.Ok)
        assertEquals(listOf("model-a", "model-b"), (health as ProviderHealth.Ok).models)
        assertEquals("Bearer test-key-123", captured!!.authorization)
    }

    @Test
    fun `http failure surfaces as a persian message with the code`() {
        server!!.removeContext("/v1/models")
        server!!.createContext("/v1/models") { exchange ->
            val bytes = "{\"error\":\"bad key\"}".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val health = runBlocking { provider!!.testConnection() }
        assertTrue("expected Failed, was $health", health is ProviderHealth.Failed)
        val message = (health as ProviderHealth.Failed).messageFa
        assertTrue("message should name the key problem: $message", message.contains("کلید"))
        assertTrue("message should carry the HTTP code in Persian digits: $message", message.contains("۴۰۱"))
    }
}
