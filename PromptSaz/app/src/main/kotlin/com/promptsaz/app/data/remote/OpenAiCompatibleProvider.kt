package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.di.IoDispatcher
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.AiService
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.provider.AiModePrompts
import com.promptsaz.app.domain.provider.ChatAiProvider
import com.promptsaz.app.domain.provider.ImageGenerationUnsupportedException
import com.promptsaz.app.domain.provider.PromptProvider
import com.promptsaz.app.domain.provider.ProviderGeneration
import com.promptsaz.app.domain.provider.ProviderHealth
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.util.toPersianDigits
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI-compatible provider (chat-completions format). Works with OpenAI,
 * Codecraft, OpenRouter, DeepSeek, Groq, Ollama and any compatible server —
 * the user sets the base URL in Settings.
 *
 * Uses java.net.HttpURLConnection + kotlinx.serialization: zero extra
 * dependencies, and the endpoints/auth scheme below are the single place to
 * change if a server ever deviates from the standard.
 */
@Singleton
class OpenAiCompatibleProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: ApiKeyStore,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PromptProvider, ChatAiProvider {

    override val id: String = ID
    override val displayNameFa: String = DISPLAY_NAME_FA

    // --- PromptProvider ---------------------------------------------------------

    override suspend fun isConfigured(serviceId: String?): Boolean = withContext(ioDispatcher) {
        val service = resolveService(serviceId)
        service != null &&
            service.baseUrl.isNotBlank() &&
            service.model.isNotBlank() &&
            secureKeyStore.hasApiKey(service.id)
    }

    override suspend fun testConnection(serviceId: String?): ProviderHealth = withContext(ioDispatcher) {
        // /models needs only key + base URL — NOT a model name. A user who has
        // just saved the key and wants to pick a model must be able to test.
        val config = readConnectionConfig(serviceId)
            ?: return@withContext ProviderHealth.Failed(configGapFa(requireModel = false, serviceId))
        if (config.isPixazo) {
            // No cheap listing endpoint exists; probing the public status route
            // with a fake request id tells us whether the key is accepted
            // (401/403 = rejected key, anything else = gateway reachable).
            val modelId = PixazoWire.parseModelId(config.baseUrl)
            val probeUrl = PixazoWire.statusUrl("", modelId + "_connection-test", config.baseUrl)
            return@withContext when (val response = httpCall("GET", probeUrl, config, body = null)) {
                is HttpOutcome.Success ->
                    if (response.code == 401 || response.code == 403) {
                        ProviderHealth.Failed(persianHttpError(response.code, response.body))
                    } else {
                        ProviderHealth.Ok(listOf(modelId))
                    }
                is HttpOutcome.Failure -> ProviderHealth.Failed(response.messageFa)
            }
        }
        val modelsUrl = if (config.isGemini) GeminiWire.modelsUrl(config.baseUrl) else config.modelsUrl
        when (val response = httpCall("GET", modelsUrl, config, body = null)) {
            is HttpOutcome.Success -> {
                val models = if (config.isGemini) {
                    GeminiWire.parseModelNames(response.body, json)
                } else {
                    parseModels(response.body)
                }
                if (response.code in 200..299) {
                    ProviderHealth.Ok(models)
                } else {
                    ProviderHealth.Failed(persianHttpError(response.code, response.body))
                }
            }
            is HttpOutcome.Failure -> ProviderHealth.Failed(response.messageFa)
        }
    }

    override suspend fun listModels(serviceId: String?): Result<List<String>> = withContext(ioDispatcher) {
        val config = readConnectionConfig(serviceId)
            ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = false, serviceId)))
        if (config.isPixazo) {
            // The model is baked into the service URL; surface it as the only pick.
            return@withContext Result.success(listOf(PixazoWire.parseModelId(config.baseUrl)))
        }
        val modelsUrl = if (config.isGemini) GeminiWire.modelsUrl(config.baseUrl) else config.modelsUrl
        when (val response = httpCall("GET", modelsUrl, config, body = null)) {
            is HttpOutcome.Success -> {
                if (response.code in 200..299) {
                    val models = if (config.isGemini) {
                        GeminiWire.parseModelNames(response.body, json)
                    } else {
                        parseModels(response.body)
                    }
                    Result.success(models)
                } else {
                    Result.failure(IllegalStateException(persianHttpError(response.code, response.body)))
                }
            }
            is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
        }
    }

    override suspend fun generatePrompt(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        serviceId: String?,
    ): Result<ProviderGeneration> = withContext(ioDispatcher) {
        val config = readGenerationConfig(serviceId)
            ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = true, serviceId)))
        if (config.isPixazo) {
            return@withContext Result.failure(IllegalStateException(PIXAZO_TEXT_ONLY_FA))
        }
        if (config.isGemini) {
            return@withContext geminiCall(
                config,
                model = config.model,
                turns = listOf(
                    ChatTurn(role = "system", text = AiModePrompts.SYSTEM_PROMPT),
                    ChatTurn(role = "user", text = buildUserPrompt(spec, kb)),
                ),
            ) { content -> parseGeneration(content) }
        }
        val request = ChatCompletionRequestDto(
            model = config.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = AiModePrompts.SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = buildUserPrompt(spec, kb)),
            ),
            maxTokens = DEFAULT_MAX_TOKENS,
        )
        when (
            val response = postChatWithTokenRetry(
                config,
                request,
                ChatCompletionRequestDto.serializer(),
            ) { req, cap -> req.copy(maxTokens = cap) }
        ) {
            is HttpOutcome.Success -> {
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException(persianHttpError(response.code, response.body)),
                    )
                }
                runCatching {
                    val parsed = json.decodeFromString(ChatCompletionResponseDto.serializer(), response.body)
                    val content = parsed.choices.firstOrNull()?.message?.content
                        ?: throw IOException(BAD_REPLY_FA)
                    parseGeneration(content)
                }
            }
            is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
        }
    }

    // --- ChatAiProvider: chat + image generation -----------------------------------

    /**
     * One chat round-trip. Text turns become plain string contents; turns with
     * an image become [text, image_url] part arrays (OpenAI vision format), so
     * multimodal models can read and analyze the attached picture.
     */
    override suspend fun chat(
        model: String,
        turns: List<ChatTurn>,
        serviceId: String?,
    ): Result<String> = withContext(ioDispatcher) {
        val config = readConnectionConfig(serviceId)
            ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = false, serviceId)))
        val selectedModel = model.trim()
        if (selectedModel.isBlank()) {
            return@withContext Result.failure(IllegalStateException(NO_MODEL_FA))
        }
        if (config.isPixazo) {
            return@withContext Result.failure(IllegalStateException(PIXAZO_TEXT_ONLY_FA))
        }
        if (config.isGemini) {
            return@withContext geminiCall(config, model = selectedModel, turns = turns) { it }
        }
        val request = ChatCompletionChatRequestDto(
            model = selectedModel,
            messages = turns.map { turn -> turn.toContentMessage() },
            maxTokens = DEFAULT_MAX_TOKENS,
        )
        when (
            val response = postChatWithTokenRetry(
                config,
                request,
                ChatCompletionChatRequestDto.serializer(),
            ) { req, cap -> req.copy(maxTokens = cap) }
        ) {
            is HttpOutcome.Success -> {
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException(persianHttpError(response.code, response.body)),
                    )
                }
                runCatching {
                    val parsed = json.decodeFromString(ChatCompletionResponseDto.serializer(), response.body)
                    parsed.choices.firstOrNull()?.message?.content
                        ?: throw IOException(BAD_REPLY_FA)
                }
            }
            is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
        }
    }

    /** Generates one image via the OpenAI-compatible images endpoint. */
    override suspend fun generateImage(
        model: String,
        prompt: String,
        size: String,
        serviceId: String?,
    ): Result<GeneratedImage> =
        withContext(ioDispatcher) {
            val config = readConnectionConfig(serviceId)
                ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = false, serviceId)))
            val selectedModel = model.trim()
            if (selectedModel.isBlank()) {
                return@withContext Result.failure(IllegalStateException(NO_MODEL_FA))
            }
            if (config.isPixazo) {
                return@withContext pixazoGenerate(config, prompt, size)
            }
            if (config.isGemini) {
                // Native Gemini image generation (nano-banana / gemini-*-image):
                // generateContent with TEXT+IMAGE modalities → inlineData part.
                return@withContext geminiGenerateImage(config, selectedModel, prompt)
            }
            val request = ImageGenerationRequestDto(model = selectedModel, prompt = prompt, size = size)
            val body = json.encodeToString(ImageGenerationRequestDto.serializer(), request)
            when (val response = httpCallWithRetry("POST", config.imagesUrl, config, body)) {
                is HttpOutcome.Success -> {
                    if (response.code == 404) {
                        // The service has no images endpoint at all (text-only
                        // proxies like Codecraft) — a distinct, actionable case.
                        return@withContext Result.failure(
                            ImageGenerationUnsupportedException(IMAGES_UNSUPPORTED_FA),
                        )
                    }
                    if (response.code !in 200..299) {
                        return@withContext Result.failure(
                            IllegalStateException(persianHttpError(response.code, response.body)),
                        )
                    }
                    runCatching {
                        val parsed = json.decodeFromString(ImageGenerationResponseDto.serializer(), response.body)
                        val image = parsed.data.firstOrNull()
                            ?: throw IOException(BAD_IMAGE_REPLY_FA)
                        when {
                            !image.url.isNullOrBlank() -> GeneratedImage.FromUrl(image.url)
                            !image.b64Json.isNullOrBlank() -> GeneratedImage.FromBase64(image.b64Json)
                            else -> throw IOException(BAD_IMAGE_REPLY_FA)
                        }
                    }
                }
                is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
            }
        }

    /**
     * Native Gemini image generation (e.g. gemini-2.5-flash-image «نانو‌بنانا»):
     * POST {base}/models/{model}:generateContent asking for TEXT+IMAGE output;
     * the first inlineData part carries the image bytes as base64. A text-only
     * reply means the model is not an image model → the تصویر tab's
     * image-prompt fallback kicks in with a precise Persian message.
     */
    private suspend fun geminiGenerateImage(
        config: ProviderConfig,
        model: String,
        prompt: String,
    ): Result<GeneratedImage> = withContext(ioDispatcher) {
        val request = GeminiWire.imageRequest(prompt)
        val body = json.encodeToString(GeminiWire.GeminiGenerateRequest.serializer(), request)
        when (val response = httpCallWithRetry("POST", GeminiWire.generateContentUrl(config.baseUrl, model), config, body)) {
            is HttpOutcome.Success -> {
                if (response.code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException(persianHttpError(response.code, response.body)),
                    )
                }
                runCatching {
                    val base64 = GeminiWire.imageBase64(response.body, json)
                        ?: throw ImageGenerationUnsupportedException(GEMINI_NO_IMAGE_FA)
                    GeneratedImage.FromBase64(base64)
                }
            }
            is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
        }
    }

    /** Downloads a generated image from its (pre-signed) URL. */
    override suspend fun fetchImageBytes(url: String): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                stream?.use { it.readBytes() }
                    ?: throw IOException(persianHttpError(code, ""))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun ChatTurn.toContentMessage(): ChatContentMessageDto {
        val content: JsonElement = if (imageDataUrl == null) {
            JsonPrimitive(text)
        } else {
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", imageDataUrl) })
                    },
                )
            }
        }
        return ChatContentMessageDto(role = role, content = content)
    }

    // --- user prompt construction -------------------------------------------------

    private fun buildUserPrompt(spec: PromptSpec, kb: DomainKnowledge?): String = buildString {
        appendLine("ایده کاربر (خام):")
        appendLine(spec.idea.trim())
        appendLine()
        appendLine("حوزه: ${kb?.nameFa ?: "عمومی"}")
        appendLine("هدف‌گذاری مدل مقصد: ${spec.targetAi.labelFa}")
        appendLine("زبان پرامپت: ${when (spec.outputLanguage) {
            OutputLanguage.PERSIAN -> "فارسی"
            OutputLanguage.ENGLISH -> "انگلیسی"
            OutputLanguage.SAME_AS_INPUT -> "همان زبان ایده کاربر"
        }}")
        appendLine("سطح جزئیات: ${spec.detailLevel.labelFa} (${spec.detailLevel.descriptionFa})")
        if (spec.mode == PromptMode.IMPROVE) {
            appendLine("حالت: بهبود پرامپت موجود — متن بالا را بازنویسی کن، نه اینکه پاسخش را بدهی.")
        }
        spec.answers.filter { !it.skipped }.forEach { answer ->
            appendLine("پاسخ کاربر به «${answer.questionFa}»: ${answer.value}")
        }
        spec.answers.filter { it.skipped }.forEach { answer ->
            appendLine("بی‌پاسخ مانده (به متغیر [${answer.questionId.replace('-', '_').uppercase()}] تبدیل شود): ${answer.questionFa}")
        }
        kb?.let { knowledge ->
            knowledge.personas.firstOrNull()?.let { persona ->
                appendLine("پیشنهاد نقش: ${persona.titleFa} — ${persona.expertiseFa}")
            }
            knowledge.outputStructures.firstOrNull()?.let { structure ->
                appendLine("پیشنهاد ساختار خروجی: ${structure.titleFa} — ${structure.templateFa}")
            }
            knowledge.guardrails.take(4).forEach { guardrail ->
                appendLine("نگه‌داشت: ${guardrail.doFa}؛ ${guardrail.dontFa}.")
            }
        }
        appendLine()
        appendLine("فقط همان JSON خواسته‌شده را برگردان.")
    }

    // --- HTTP core ------------------------------------------------------------------

    private sealed interface HttpOutcome {
        data class Success(val code: Int, val body: String) : HttpOutcome
        data class Failure(val messageFa: String) : HttpOutcome
    }

    private data class ProviderConfig(
        val key: String,
        val model: String,
        val baseUrl: String,
        val protocol: String = AiService.TYPE_OPENAI_COMPATIBLE,
    ) {
        val isGemini: Boolean get() = protocol == AiService.TYPE_GEMINI
        val isPixazo: Boolean get() = protocol == AiService.TYPE_PIXAZO
        val chatUrl: String get() = baseUrl + CHAT_COMPLETIONS_PATH
        val modelsUrl: String get() = baseUrl + MODELS_PATH
        val imagesUrl: String get() = baseUrl + IMAGES_PATH
    }

    /** Resolves a service by id; null id → the globally active service. */
    private suspend fun resolveService(serviceId: String?): AiService? {
        val settings = settingsRepository.settings.first()
        return settings.aiServices.firstOrNull { it.id == serviceId } ?: settings.activeService
    }

    /** Key + base URL of one service — enough for /models and the test. */
    private suspend fun readConnectionConfig(serviceId: String?): ProviderConfig? {
        val service = resolveService(serviceId) ?: return null
        val key = secureKeyStore.getApiKey(service.id)
        val base = service.baseUrl.trim().trimEnd('/')
        if (key.isNullOrBlank() || base.isBlank()) return null
        return ProviderConfig(key = key, model = service.model, baseUrl = base, protocol = service.type)
    }

    /**
     * One native Gemini round-trip (POST {base}/models/{model}:generateContent,
     * X-goog-api-key): system turns become systemInstruction, images become
     * inlineData parts; [map] turns the reply text into the caller's result.
     */
    private suspend fun <T> geminiCall(
        config: ProviderConfig,
        model: String,
        turns: List<ChatTurn>,
        map: (String) -> T,
    ): Result<T> = withContext(ioDispatcher) {
        val url = GeminiWire.generateContentUrl(config.baseUrl, model)
        val request = GeminiWire.fromTurns(turns, maxOutputTokens = DEFAULT_MAX_TOKENS)
        val body = json.encodeToString(GeminiWire.GeminiGenerateRequest.serializer(), request)
        when (val response = httpCallWithRetry("POST", url, config, body)) {
            is HttpOutcome.Success -> {
                if (response.code !in 200..299) {
                    Result.failure(IllegalStateException(persianHttpError(response.code, response.body)))
                } else {
                    runCatching {
                        val content = GeminiWire.replyText(response.body, json)
                            ?: throw IOException(BAD_REPLY_FA)
                        map(content)
                    }
                }
            }
            is HttpOutcome.Failure -> Result.failure(IllegalStateException(response.messageFa))
        }
    }

    /** Key + base URL + model of one service — required only for generation. */
    private suspend fun readGenerationConfig(serviceId: String?): ProviderConfig? {
        val connection = readConnectionConfig(serviceId) ?: return null
        if (connection.model.isBlank()) return null
        return connection
    }

    /**
     * Names the FIRST missing piece in Persian so the user is never told the
     * key is missing when it is actually the model (or the base URL).
     */
    private suspend fun configGapFa(requireModel: Boolean, serviceId: String? = null): String {
        val service = resolveService(serviceId)
        return missingConfigMessageFa(
            hasKey = service != null && secureKeyStore.hasApiKey(service.id),
            hasBaseUrl = !service?.baseUrl.isNullOrBlank(),
            hasModel = !service?.model.isNullOrBlank(),
            requireModel = requireModel,
        ) ?: NO_KEY_FA
    }

    // --- chat POST with token-cap fallbacks ----------------------------------------

    /**
     * POSTs a chat request with a sensible max_tokens cap and adapts to the
     * service's answer:
     *  - HTTP 402 "can only afford N" (credit-limited proxies like OpenRouter)
     *    → retries once with ~90% of the affordable cap.
     *  - HTTP 400 rejecting the max_tokens parameter (some newer models) →
     *    retries once without the parameter.
     */
    private suspend fun <T> postChatWithTokenRetry(
        config: ProviderConfig,
        request: T,
        serializer: KSerializer<T>,
        withMaxTokens: (T, Int?) -> T,
    ): HttpOutcome {
        var response = httpCallWithRetry("POST", config.chatUrl, config, encodeChatBody(request, serializer))
        if (response is HttpOutcome.Success && response.code == 402) {
            val affordable = AFFORD_REGEX.find(response.body)?.groupValues?.get(1)?.toIntOrNull()
            if (affordable != null && affordable >= MIN_AFFORDABLE_TOKENS) {
                val retry = withMaxTokens(
                    request,
                    (affordable * 9 / 10).coerceIn(MIN_AFFORDABLE_TOKENS, DEFAULT_MAX_TOKENS),
                )
                response = httpCallWithRetry("POST", config.chatUrl, config, encodeChatBody(retry, serializer))
            }
        }
        if (response is HttpOutcome.Success && response.code == 400 && response.body.contains("max_tokens")) {
            val retry = withMaxTokens(request, null)
            response = httpCallWithRetry("POST", config.chatUrl, config, encodeChatBody(retry, serializer))
        }
        return response
    }

    /** Encodes the request as JSON, dropping a null max_tokens (encodeDefaults would emit it). */
    private fun <T> encodeChatBody(request: T, serializer: KSerializer<T>): String {
        val element = json.encodeToJsonElement(serializer, request)
        val obj = element as? JsonObject ?: return json.encodeToString(serializer, request)
        return if (obj["max_tokens"] is JsonNull) {
            JsonObject(obj.filterKeys { it != "max_tokens" }).toString()
        } else {
            obj.toString()
        }
    }

    // --- Pixazo async image jobs ------------------------------------------------------

    /**
     * One Pixazo round-trip: submit {prompt, size} to {base}/text-to-image
     * (Ocp-Apim-Subscription-Key auth), then poll the job status until the
     * public image URL appears. The repository downloads that URL plainly.
     */
    private suspend fun pixazoGenerate(config: ProviderConfig, prompt: String, size: String): Result<GeneratedImage> =
        withContext(ioDispatcher) {
            val request = PixazoWire.PixazoSubmitRequest(prompt = prompt, size = size)
            val body = json.encodeToString(PixazoWire.PixazoSubmitRequest.serializer(), request)
            when (val submit = httpCall("POST", PixazoWire.textToImageUrl(config.baseUrl), config, body)) {
                is HttpOutcome.Failure -> Result.failure(IllegalStateException(submit.messageFa))
                is HttpOutcome.Success -> {
                    if (submit.code !in 200..299) {
                        return@withContext Result.failure(
                            IllegalStateException(persianHttpError(submit.code, submit.body)),
                        )
                    }
                    val job = runCatching {
                        json.decodeFromString(PixazoWire.PixazoSubmitResponse.serializer(), submit.body)
                    }.getOrNull()
                    if (job == null || job.requestId.isBlank()) {
                        return@withContext Result.failure(IllegalStateException(BAD_IMAGE_REPLY_FA))
                    }
                    pollPixazoJob(PixazoWire.statusUrl(job.pollingUrl, job.requestId, config.baseUrl), config)
                }
            }
        }

    /** Polls a Pixazo job until COMPLETED / FAILED or the attempt budget runs out. */
    private suspend fun pollPixazoJob(statusUrl: String, config: ProviderConfig): Result<GeneratedImage> {
        repeat(PIXAZO_MAX_POLLS) { attempt ->
            if (attempt > 0) delay(pixazoPollIntervalMs)
            when (val response = httpCall("GET", statusUrl, config, body = null)) {
                is HttpOutcome.Failure -> return Result.failure(IllegalStateException(response.messageFa))
                is HttpOutcome.Success -> {
                    if (response.code !in 200..299) {
                        return Result.failure(IllegalStateException(persianHttpError(response.code, response.body)))
                    }
                    val status = runCatching {
                        json.decodeFromString(PixazoWire.PixazoStatusResponse.serializer(), response.body)
                    }.getOrNull()
                    if (status != null) {
                        when {
                            PixazoWire.isCompleted(status.status) -> {
                                val url = status.output?.mediaUrl?.firstOrNull()
                                return if (url.isNullOrBlank()) {
                                    Result.failure(IllegalStateException(BAD_IMAGE_REPLY_FA))
                                } else {
                                    Result.success(GeneratedImage.FromUrl(url))
                                }
                            }
                            PixazoWire.isFailed(status.status) -> {
                                return Result.failure(
                                    IllegalStateException(
                                        PIXAZO_JOB_FAILED_FA +
                                            (status.error?.take(200)?.let { " ($it)" } ?: ""),
                                    ),
                                )
                            }
                            // QUEUED / PROCESSING → keep polling
                        }
                    }
                }
            }
        }
        return Result.failure(IllegalStateException(PIXAZO_TIMEOUT_FA))
    }

    /**
     * Generation calls go through this wrapper: on a temporary rate limit
     * (HTTP 429) or server hiccup (503) it waits and retries automatically —
     * up to [RATE_LIMIT_RETRIES] times — so a one-off quota burst (very common
     * on free Gemini keys) does not kill the whole generation. Probes and
     * model listings keep the plain [httpCall] (their quota is separate).
     */
    private suspend fun httpCallWithRetry(
        method: String,
        url: String,
        config: ProviderConfig,
        body: String?,
    ): HttpOutcome {
        var attempt = 0
        while (true) {
            val outcome = httpCall(method, url, config, body)
            val temporary = outcome is HttpOutcome.Success && (outcome.code == 429 || outcome.code == 503)
            if (!temporary || attempt >= RATE_LIMIT_RETRIES) return outcome
            val delays = rateLimitRetryDelaysMs
            delay(delays[attempt.coerceAtMost(delays.lastIndex)])
            attempt++
        }
    }

    private fun httpCall(method: String, url: String, config: ProviderConfig, body: String?): HttpOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                when {
                    config.isGemini -> setRequestProperty(GeminiWire.API_KEY_HEADER, config.key)
                    config.isPixazo -> setRequestProperty(PixazoWire.API_KEY_HEADER, config.key)
                    else -> setRequestProperty(AUTH_HEADER, AUTH_PREFIX + config.key)
                }
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpOutcome.Success(code, responseText)
        } catch (e: IOException) {
            HttpOutcome.Failure(
                "اتصال به سرور برقرار نشد. اینترنت، نشانی سرور و کلید را بررسی کن. " +
                    "(جزئیات فنی: ${e.message ?: "خطای شبکه"})",
            )
        } catch (e: Exception) {
            HttpOutcome.Failure(
                "خطای غیرمنتظره در ارتباط با سرور: " + (e.message ?: e.javaClass.simpleName),
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseModels(body: String): List<String> = runCatching {
        json.decodeFromString(ModelsResponseDto.serializer(), body).data.map { it.id }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /**
     * Extracts {"title": ..., "prompt": ...} from the model reply, tolerating
     * markdown fences and prose around the JSON. Falls back to using the whole
     * reply as the prompt when no JSON is found.
     */
    private fun parseGeneration(content: String): ProviderGeneration {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val candidate = cleaned.substring(jsonStart, jsonEnd + 1)
            runCatching {
                val reply = json.decodeFromString(GenerationReplyDto.serializer(), candidate)
                val prompt = reply.prompt?.trim().orEmpty()
                if (reply.error != null && prompt.isEmpty()) {
                    throw IllegalStateException(reply.error)
                }
                if (prompt.isNotEmpty()) {
                    return ProviderGeneration(
                        title = (reply.title?.trim()?.takeIf { it.isNotBlank() } ?: FALLBACK_TITLE_FA),
                        prompt = prompt,
                    )
                }
            }
        }
        if (cleaned.length > 80) {
            return ProviderGeneration(title = FALLBACK_TITLE_FA, prompt = cleaned)
        }
        throw IOException(BAD_REPLY_FA)
    }

    /** Persian error with the exact HTTP status and error body, per user requirement. */
    private fun persianHttpError(code: Int, body: String): String {
        val trimmed = body.trim()
        // Google & CDNs answer some blocks with an HTML page instead of JSON —
        // showing the raw soup helps nobody; keep only the <title> line.
        val isHtml = trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        val bodySnippet = (
            if (isHtml) htmlTitleOf(trimmed) else trimmed.take(if (code == 429) 120 else 300)
            ).orEmpty()
        val reason = when (code) {
            401, 403 ->
                if (isHtml) {
                    "سرور دسترسی را در همان ورودی مسدود کرد (به‌جای پاسخ API، صفحهٔ خطا برگرداند)."
                } else {
                    "کلید API نامعتبر است یا اجازه دسترسی ندارد."
                }
            402 -> "اعتبار (کردیت) حساب این سرویس برای این درخواست کافی نیست. حساب را در پنل سرویس شارژ کن، مدل رایگان‌تری انتخاب کن، یا در تنظیمات به سرویس دیگری برگرد."
            404 -> "نشانی سرور یا نام مدل پیدا نشد. نشانی پایه و نام مدل را بررسی کن."
            429 ->
                "سهمیه یا نرخ درخواست‌های این کلید موقتاً تمام شده است. " +
                    "(تست اتصال و فهرست مدل‌ها سهمیهٔ جدایی دارند؛ برای همین کار می‌کنند ولی ساخت پرامپت/تصویر نه.) " +
                    "چند دقیقه صبر کن و دوباره امتحان کن؛ " +
                    "اگر کلید رایگان گوگل است و سقف روزانه پر شده، فردا ریست می‌شود — " +
                    "مصرف را در aistudio.google.com/apikey ببین یا در تنظیمات، سرویس دیگری به این بخش وصل کن."
            in 500..599 -> "سرور شخص ثالث خطا داده است؛ بعداً دوباره امتحان کن."
            else -> "درخواست رد شد."
        }
        return buildString {
            append(reason)
            append(" (کد HTTP: ")
            append(code.toPersianDigits())
            append(")")
            if (code == 403 && isHtml) {
                append(
                    "\nراه‌حل‌های احتمالی (به ترتیب احتمال):" +
                        "\n۱) اگر کلیدت را جایی کپی یا اشتراک کرده‌ای، گوگل آن را سریع غیرفعال می‌کند؛ از aistudio.google.com/apikey یک کلید تازه بساز و کلید جدید را هیچ‌جا کپی نکن." +
                        "\n۲) اگر اینترنت فعلی گوشی از منطقهٔ پشتیبانی‌نشدهٔ گوگل می‌رود، با یک VPN کامل (نه فقط پراکسی مرورگر) امتحان کن." +
                        "\n۳) اگر کلیدت با AIza… شروع می‌شود و در AI Studio برچسب «Unrestricted» دارد، همان‌جا گزینهٔ Add restrictions را بزن." +
                        "\n۴) اگر کلیدت را از Google Cloud (Vertex) ساخته‌ای، در تنظیمات پیش‌تنظیم «Gemini (Vertex Express)» را امتحان کن.",
                )
            }
            if (bodySnippet.isNotEmpty()) {
                append("\nپاسخ سرور: ")
                append(bodySnippet)
            }
        }
    }

    /** "…<title>Error 403 (Forbidden)!!1</title>…" → "Error 403 (Forbidden)!!1". */
    private fun htmlTitleOf(html: String): String? =
        Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val ID = "openai_compatible"
        const val DISPLAY_NAME_FA = "سازگار با OpenAI (Codecraft و مشابه)"

        // --- Single place to change endpoints/auth — per user requirement ---
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val MODELS_PATH = "/models"
        const val IMAGES_PATH = "/images/generations"
        const val AUTH_HEADER = "Authorization"
        const val AUTH_PREFIX = "Bearer "

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000

        /** Reply cap sent as max_tokens on every chat request. */
        const val DEFAULT_MAX_TOKENS = 4096

        /** Below this affordable cap a retry is pointless — surface the error. */
        const val MIN_AFFORDABLE_TOKENS = 200

        /** "…can only afford 800…" from OpenRouter's 402 body. */
        val AFFORD_REGEX = Regex("""can only afford (\d+)""")

        // --- Pixazo -----------------------------------------------------------------
        /** Poll attempts for a Pixazo job before giving up (~5 min at the default interval). */
        const val PIXAZO_MAX_POLLS = 100

        /** Interval between Pixazo job polls — a hook lowered by unit tests. */
        var pixazoPollIntervalMs: Long = 3_000L

        /** Automatic retries of a 429/503 before giving up. */
        const val RATE_LIMIT_RETRIES = 2

        /** Backoff before each 429/503 retry — a hook lowered by unit tests. */
        var rateLimitRetryDelaysMs: List<Long> = listOf(2_000L, 5_000L)

        const val PIXAZO_TEXT_ONLY_FA =
            "این سرویس فقط ساخت تصویر دارد؛ برای گفتگو و تولید پرامپت، در تنظیمات یک سرویس گفتگو (مثل CodeCraft) را فعال کن."
        const val PIXAZO_JOB_FAILED_FA = "ساخت تصویر در سرور ناموفق بود؛ دوباره تلاش کن."
        const val PIXAZO_TIMEOUT_FA = "ساخت تصویر بیش از حد طول کشید؛ دوباره تلاش کن."

        /**
         * Pure picker for the first missing config piece (unit-tested).
         * Returns null when everything required is present.
         */
        fun missingConfigMessageFa(
            hasKey: Boolean,
            hasBaseUrl: Boolean,
            hasModel: Boolean,
            requireModel: Boolean,
        ): String? = when {
            !hasKey -> NO_KEY_FA
            !hasBaseUrl -> NO_BASE_URL_FA
            requireModel && !hasModel -> NO_MODEL_FA
            else -> null
        }

        const val NO_KEY_FA = "کلید API ذخیره نشده است. کلید را در تنظیمات وارد و دکمهٔ «ذخیره کلید» را بزن."
        const val NO_BASE_URL_FA =
            "نشانی سرور (Base URL) خالی است؛ در تنظیمات، سرویس را ویرایش کن و آدرس درست را وارد کن. " +
                "مثل https://codecraftapi.com/v1 یا https://api.openai.com/v1"
        const val NO_MODEL_FA = "نام مدل انتخاب نشده است. با دکمهٔ «دریافت فهرست مدل‌ها» یکی را انتخاب کن یا در کادر «نام مدل» بنویس."
        const val BAD_REPLY_FA = "پاسخ سرور قابل خواندن نبود. مدل دیگری را امتحان کن یا دوباره تلاش کن."
        const val BAD_IMAGE_REPLY_FA =
            "سرور تصویری برنگرداند. یک مدل ساخت تصویر (مثل dall-e یا flux) را انتخاب کن و دوباره امتحان کن."
        const val IMAGES_UNSUPPORTED_FA =
            "ساخت تصویر با این سرویس یا این مدل ممکن نیست (کد HTTP: ۴۰۴). " +
                "به احتمال زیاد سرویس شما صفحهٔ ساخت عکس ندارد یا مدل انتخاب‌شده فقط متن تولید می‌کند."

        const val GEMINI_NO_IMAGE_FA =
            "این مدل گوگل در پاسخ، تصویر برنگرداند؛ احتمالاً متن‌ساز است. " +
                "در انتخاب مدل، یک مدل تصویری (مثل gemini-2.5-flash-image) را انتخاب کن."

        const val FALLBACK_TITLE_FA = "پرامپت ساخته‌شده با هوش مصنوعی"
    }
}
