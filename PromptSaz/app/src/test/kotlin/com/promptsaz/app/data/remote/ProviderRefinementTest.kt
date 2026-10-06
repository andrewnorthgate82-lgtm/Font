package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.provider.PromptRefinement
import com.promptsaz.app.domain.repository.SettingsRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
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
 * The «بهبود ساختار» wire contract: the HTTP request the provider sends must
 * carry BOTH the previous prompt and the evaluator's suggestions, so the model
 * rewrites its own output instead of starting from scratch.
 */
class ProviderRefinementTest {

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

    private val capturedBody = AtomicReference<String?>(null)
    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null

    @Before
    fun setUp() {
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(10L, 10L, 10L)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            capturedBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val reply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"{\"title\":\"بهبود\",\"prompt\":\"پرامپت بهبودیافته\"}"}]}}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
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
                    promptServiceId = "s1",
                ),
            ),
            secureKeyStore = FakeKeyStore().apply { keys["s1"] = "AIza-test-key-123" },
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(2_000L, 6_000L, 15_000L)
        server?.stop(0)
    }

    private val spec = PromptSpec(
        idea = "یک پست اینستاگرام برای کافه",
        domainId = "general",
        targetAi = TargetAi.ANY,
        outputLanguage = OutputLanguage.SAME_AS_INPUT,
        detailLevel = DetailLevel.EXPERT,
    )

    @Test
    fun `the request body carries the previous prompt and the suggestions`() = runBlocking {
        val refinement = PromptRefinement(
            previousPrompt = "پرامپت نسخهٔ قبلی که مدل ساخته بود",
            suggestionsFa = listOf("مخاطب هدف مشخص نشده", "نمونه خروجی ندارد", "لحن برند تعریف نشده"),
        )

        val result = provider!!.generatePrompt(spec, kb = null as DomainKnowledge?, serviceId = "s1", refinement = refinement)

        assertTrue("generatePrompt failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("پرامپت بهبودیافته", result.getOrNull()!!.prompt)

        val body = capturedBody.get().orEmpty()
        assertTrue("previous prompt not sent! body=$body", body.contains("پرامپت نسخهٔ قبلی که مدل ساخته بود"))
        assertTrue("suggestion 1 not sent!", body.contains("مخاطب هدف مشخص نشده"))
        assertTrue("suggestion 2 not sent!", body.contains("نمونه خروجی ندارد"))
        assertTrue("suggestion 3 not sent!", body.contains("لحن برند تعریف نشده"))
    }

    @Test
    fun `without refinement the request carries none of it (normal generation intact)`() = runBlocking {
        val result = provider!!.generatePrompt(spec, kb = null as DomainKnowledge?, serviceId = "s1")

        assertTrue(result.isSuccess)
        val body = capturedBody.get().orEmpty()
        assertTrue("idea must be sent", body.contains("یک پست اینستاگرام برای کافه"))
        assertTrue("refinement block must be absent", !body.contains("بهبود نسخهٔ قبلی"))
    }
}
