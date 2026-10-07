package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.model.UserAttachment
import com.promptsaz.app.domain.repository.SettingsRepository
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
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
 * Wire contract of the any-type attachment system (هر نوع فایلی، چندتایی):
 * what ACTUALLY leaves the phone for each protocol — images inline, text
 * files as text, unsupported binaries honestly listed (OpenAI) or sent
 * inline (Gemini), reference images for image editing, and the size budget
 * enforced before any network call.
 */
class ProviderAttachmentsWireTest {

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

    private val geminiHits = AtomicInteger(0)
    private val chatHits = AtomicInteger(0)
    private val capturedBody = AtomicReference<String?>(null)

    private var server: HttpServer? = null

    @Before
    fun setUp() {
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(10L, 10L, 10L)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server!!.createContext("/v1beta/models/gemini-flash-latest:generateContent") { exchange ->
            geminiHits.incrementAndGet()
            capturedBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val wantsImage = exchange.requestURI.path.contains("image")
            val reply = if (wantsImage) {
                """{"candidates":[{"content":{"role":"model","parts":[{"inlineData":{"mimeType":"image/png","data":"aWNn"}}]}}]}"""
            } else {
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"پاسخ دستیار"}]}}]}"""
            }
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server!!.createContext("/v1/chat/completions") { exchange ->
            chatHits.incrementAndGet()
            capturedBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val reply = """{"choices":[{"message":{"role":"assistant","content":"پاسخ دستیار"}}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server!!.start()
    }

    @After
    fun tearDown() {
        OpenAiCompatibleProvider.rateLimitRetryDelaysMs = listOf(2_000L, 6_000L, 15_000L)
        server?.stop(0)
    }

    private fun provider(type: String): OpenAiCompatibleProvider =
        OpenAiCompatibleProvider(
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    aiEnabled = true,
                    aiServices = listOf(
                        AiService(
                            id = "s1",
                            name = "svc",
                            baseUrl = "http://127.0.0.1:${server!!.address.port}" +
                                if (type == AiService.TYPE_GEMINI) "/v1beta" else "/v1",
                            type = type,
                            model = "gemini-flash-latest",
                        ),
                    ),
                    activeServiceId = "s1",
                ),
            ),
            secureKeyStore = FakeKeyStore().apply { keys["s1"] = "test-key-123" },
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            ioDispatcher = Dispatchers.Unconfined,
        )

    private val png = UserAttachment("photo.png", "image/png", "PNGDATA".toByteArray())
    private val mp3 = UserAttachment("voice.mp3", "audio/mpeg", "AUDIODATA".toByteArray())
    private val markdown = UserAttachment("notes.md", "text/markdown", "این محتوای فایل مارک‌داون است.".toByteArray())

    @Test
    fun `gemini chat sends images and audio inline and text files as text`() = runBlocking {
        val result = provider(AiService.TYPE_GEMINI).chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "این‌ها را ببین", attachments = listOf(png, markdown, mp3))),
        )

        assertTrue("chat failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val body = capturedBody.get().orEmpty()
        // image + audio as inlineData with their exact mimes and base64 payloads
        assertTrue("png mime missing", body.contains("\"mimeType\":\"image/png\""))
        assertTrue("png payload missing", body.contains(Base64.getEncoder().encodeToString(png.bytes)))
        assertTrue("mp3 mime missing", body.contains("\"mimeType\":\"audio/mpeg\""))
        assertTrue("mp3 payload missing", body.contains(Base64.getEncoder().encodeToString(mp3.bytes)))
        // text-like file travels as TEXT with its name
        assertTrue("md filename missing", body.contains("notes.md"))
        assertTrue("md content missing", body.contains("این محتوای فایل مارک‌داون است."))
    }

    @Test
    fun `openai chat inlines images, embeds text files and lists unsupported binaries`() = runBlocking {
        val result = provider(AiService.TYPE_OPENAI_COMPATIBLE).chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "این‌ها را ببین", attachments = listOf(png, markdown, mp3))),
        )

        assertTrue("chat failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val body = capturedBody.get().orEmpty()
        // image as image_url data URL
        assertTrue("image_url part missing", body.contains("image_url"))
        assertTrue("png data url missing", body.contains("data:image/png;base64," + Base64.getEncoder().encodeToString(png.bytes)))
        // text file embedded
        assertTrue("md content missing", body.contains("این محتوای فایل مارک‌داون است."))
        // unsupported binary honestly listed, NOT sent
        assertTrue("mp3 should be listed as not sent", body.contains("voice.mp3"))
        assertTrue("mp3 must not travel inline", !body.contains("audio/mpeg"))
    }

    @Test
    fun `generatePrompt carries spec attachments to gemini`() = runBlocking {
        val spec = PromptSpec(
            idea = "یک پست اینستاگرام برای کافه",
            domainId = "general",
            targetAi = TargetAi.ANY,
            outputLanguage = OutputLanguage.SAME_AS_INPUT,
            detailLevel = DetailLevel.STANDARD,
            attachments = listOf(markdown, png),
        )

        val result = provider(AiService.TYPE_GEMINI)
            .generatePrompt(spec, kb = null, serviceId = "s1")

        assertTrue("generatePrompt failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val body = capturedBody.get().orEmpty()
        assertTrue("md content missing", body.contains("این محتوای فایل مارک‌داون است."))
        assertTrue("png mime missing", body.contains("\"mimeType\":\"image/png\""))
        assertTrue("idea missing", body.contains("یک پست اینستاگرام برای کافه"))
    }

    @Test
    fun `gemini image generation sends reference images for editing`() = runBlocking {
        val result = provider(AiService.TYPE_GEMINI).generateImage(
            model = "gemini-flash-latest",
            prompt = "این عکس را شب رنگین‌کمانی کن",
            size = "1024x1024",
            attachments = listOf(png),
        )

        assertTrue("generateImage failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val body = capturedBody.get().orEmpty()
        assertTrue("reference image missing", body.contains("\"mimeType\":\"image/png\""))
        assertTrue("prompt missing", body.contains("رنگین‌کمانی"))
    }

    @Test
    fun `over-budget attachments fail in Persian before any network call`() = runBlocking {
        val huge = listOf(
            UserAttachment("big1.bin", "application/octet-stream", ByteArray(10 * 1024 * 1024)),
            UserAttachment("big2.bin", "application/octet-stream", ByteArray(9 * 1024 * 1024)),
        )
        val before = geminiHits.get() + chatHits.get()

        val result = provider(AiService.TYPE_GEMINI).chat(
            model = "gemini-flash-latest",
            turns = listOf(ChatTurn(role = "user", text = "بفرست", attachments = huge)),
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue("should explain the 20MB budget: $message", message.contains("۲۰ مگابایت"))
        assertEquals("no request may leave the phone", before, geminiHits.get() + chatHits.get())
    }
}
