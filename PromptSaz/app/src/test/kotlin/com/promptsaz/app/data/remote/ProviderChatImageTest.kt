package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.domain.model.TargetAi
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
 * Proves the new chat + image-generation chain against a real local HTTP
 * server: the request body carries the selected model, image turns travel as
 * OpenAI vision content parts, and the images endpoint is parsed for both
 * url and b64_json replies.
 */
class ProviderChatImageTest {

    private class FakeKeyStore(private val key: String?) : ApiKeyStore {
        override fun hasApiKey(): Boolean = !key.isNullOrBlank()
        override fun getApiKey(): String? = key
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
        override suspend fun setAiBaseUrl(url: String) { flow.value = flow.value.copy(aiBaseUrl = url) }
        override suspend fun setAiModel(model: String) { flow.value = flow.value.copy(aiModel = model) }
        override suspend fun setAiImageModel(model: String) { flow.value = flow.value.copy(aiImageModel = model) }
    }

    @Volatile private var chatBody: String? = null
    @Volatile private var chatPath: String? = null
    @Volatile private var imagesBody: String? = null
    @Volatile private var imagesAuth: String? = null
    @Volatile private var imageBytesServed: ByteArray = byteArrayOf(1, 2, 3, 4)

    private var server: HttpServer? = null
    private var provider: OpenAiCompatibleProvider? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server!!.createContext("/v1/chat/completions") { exchange ->
            chatPath = exchange.requestURI.path
            chatBody = exchange.requestBody.readBytes().decodeToString()
            val reply = """{"choices":[{"message":{"role":"assistant","content":"سلام! من دستیار پرامپت‌ساز هستم."}}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/v1/images/generations") { exchange ->
            imagesAuth = exchange.requestHeaders.getFirst("Authorization")
            imagesBody = exchange.requestBody.readBytes().decodeToString()
            val port = server!!.address.port
            val reply = """{"data":[{"url":"http://127.0.0.1:$port/generated-image"}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        server!!.createContext("/generated-image") { exchange ->
            exchange.sendResponseHeaders(200, imageBytesServed.size.toLong())
            exchange.responseBody.use { it.write(imageBytesServed) }
        }

        server!!.start()

        provider = OpenAiCompatibleProvider(
            settingsRepository = FakeSettingsRepository(
                AppSettings(
                    aiEnabled = true,
                    aiBaseUrl = "http://127.0.0.1:${server!!.address.port}/v1",
                    aiModel = "chat-model-selected",
                ),
            ),
            secureKeyStore = FakeKeyStore("test-key-123"),
            json = json,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `chat sends the selected model and history, and parses the reply`() = runBlocking {
        val result = provider!!.chat(
            model = "vision-model-x",
            turns = listOf(
                ChatTurn(role = "system", text = "system prompt"),
                ChatTurn(role = "user", text = "سلام"),
                ChatTurn(role = "assistant", text = "درود"),
                ChatTurn(role = "user", text = "چه خبر؟"),
            ),
        )

        assertTrue("chat failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals("سلام! من دستیار پرامپت‌ساز هستم.", result.getOrNull())
        val body = chatBody!!
        assertTrue("model not sent: $body", body.contains("\"model\":\"vision-model-x\""))
        assertTrue("history not sent: $body", body.contains("چه خبر؟") && body.contains("درود"))
        assertEquals("/v1/chat/completions", chatPath)
    }

    @Test
    fun `image turn becomes an OpenAI vision content part`() = runBlocking {
        val dataUrl = "data:image/jpeg;base64,QUJD"
        val result = provider!!.chat(
            model = "vision-model-x",
            turns = listOf(
                ChatTurn(role = "system", text = "system prompt"),
                ChatTurn(role = "user", text = "این تصویر را تحلیل کن", imageDataUrl = dataUrl),
            ),
        )

        assertTrue(result.isSuccess)
        val body = chatBody!!
        assertTrue("image_url part missing: $body", body.contains("\"type\":\"image_url\""))
        assertTrue("data url missing: $body", body.contains(dataUrl))
        assertTrue("text part missing: $body", body.contains("این تصویر را تحلیل کن"))
    }

    @Test
    fun `blank model fails in Persian without a request`() = runBlocking {
        val result = provider!!.chat(model = "  ", turns = listOf(ChatTurn("user", "سلام")))
        assertTrue(result.isFailure)
        assertEquals(
            OpenAiCompatibleProvider.NO_MODEL_FA,
            result.exceptionOrNull()?.message,
        )
        assertTrue("no HTTP call expected", chatBody == null)
    }

    @Test
    fun `image generation posts to the images endpoint with model and prompt`() = runBlocking {
        val result = provider!!.generateImage(
            model = "image-model-y",
            prompt = "a red fox in the snow",
            size = "1024x1024",
        )

        assertTrue("generation failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertTrue(result.getOrNull() is GeneratedImage.FromUrl)
        val body = imagesBody!!
        assertTrue("model not sent: $body", body.contains("\"model\":\"image-model-y\""))
        assertTrue("prompt not sent: $body", body.contains("a red fox in the snow"))
        assertTrue("bearer auth missing", imagesAuth == "Bearer test-key-123")

        // URL result → bytes can be fetched
        val bytes = provider!!.fetchImageBytes((result.getOrNull() as GeneratedImage.FromUrl).url)
        assertTrue(bytes.isSuccess)
        assertTrue(bytes.getOrNull()!!.contentEquals(imageBytesServed))
    }

    @Test
    fun `b64_json image replies are decoded`() = runBlocking {
        // swap the endpoint reply to b64_json (QUJD = "ABC")
        server!!.removeContext("/v1/images/generations")
        server!!.createContext("/v1/images/generations") { exchange ->
            exchange.requestBody.readBytes()
            val reply = """{"data":[{"b64_json":"QUJD"}]}"""
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = provider!!.generateImage("image-model-y", "test", "1024x1024")
        assertTrue(result.isSuccess)
        val generated = result.getOrNull()
        assertTrue("expected b64 result: $generated", generated is GeneratedImage.FromBase64)
        assertEquals("QUJD", (generated as GeneratedImage.FromBase64).base64)
    }
}
