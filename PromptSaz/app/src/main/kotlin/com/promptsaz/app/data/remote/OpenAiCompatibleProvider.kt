package com.promptsaz.app.data.remote

import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.di.IoDispatcher
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.provider.AiModePrompts
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
) : PromptProvider {

    override val id: String = ID
    override val displayNameFa: String = DISPLAY_NAME_FA

    // --- PromptProvider ---------------------------------------------------------

    override suspend fun isConfigured(): Boolean = withContext(ioDispatcher) {
        val settings = settingsRepository.settings.first()
        settings.aiBaseUrl.isNotBlank() && settings.aiModel.isNotBlank() && secureKeyStore.hasApiKey()
    }

    override suspend fun testConnection(): ProviderHealth = withContext(ioDispatcher) {
        // /models needs only key + base URL — NOT a model name. A user who has
        // just saved the key and wants to pick a model must be able to test.
        val config = readConnectionConfig()
            ?: return@withContext ProviderHealth.Failed(configGapFa(requireModel = false))
        when (val response = httpCall("GET", config.modelsUrl, config.key, body = null)) {
            is HttpOutcome.Success -> {
                val models = parseModels(response.body)
                if (response.code in 200..299) {
                    ProviderHealth.Ok(models)
                } else {
                    ProviderHealth.Failed(persianHttpError(response.code, response.body))
                }
            }
            is HttpOutcome.Failure -> ProviderHealth.Failed(response.messageFa)
        }
    }

    override suspend fun listModels(): Result<List<String>> = withContext(ioDispatcher) {
        val config = readConnectionConfig()
            ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = false)))
        when (val response = httpCall("GET", config.modelsUrl, config.key, body = null)) {
            is HttpOutcome.Success -> {
                if (response.code in 200..299) {
                    Result.success(parseModels(response.body))
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
    ): Result<ProviderGeneration> = withContext(ioDispatcher) {
        val config = readGenerationConfig()
            ?: return@withContext Result.failure(IllegalStateException(configGapFa(requireModel = true)))
        val request = ChatCompletionRequestDto(
            model = config.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = AiModePrompts.SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = buildUserPrompt(spec, kb)),
            ),
        )
        val body = json.encodeToString(ChatCompletionRequestDto.serializer(), request)
        when (val response = httpCall("POST", config.chatUrl, config.key, body)) {
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

    private data class ProviderConfig(val key: String, val model: String, val baseUrl: String) {
        val chatUrl: String get() = baseUrl + CHAT_COMPLETIONS_PATH
        val modelsUrl: String get() = baseUrl + MODELS_PATH
    }

    /** Key + base URL — enough for /models and the connection test. */
    private suspend fun readConnectionConfig(): ProviderConfig? {
        val settings = settingsRepository.settings.first()
        val key = secureKeyStore.getApiKey()
        val base = settings.aiBaseUrl.trim().trimEnd('/')
        if (key.isNullOrBlank() || base.isBlank()) return null
        return ProviderConfig(key = key, model = "", baseUrl = base)
    }

    /** Key + base URL + model — required only for generation. */
    private suspend fun readGenerationConfig(): ProviderConfig? {
        val connection = readConnectionConfig() ?: return null
        val model = settingsRepository.settings.first().aiModel.trim()
        if (model.isBlank()) return null
        return connection.copy(model = model)
    }

    /**
     * Names the FIRST missing piece in Persian so the user is never told the
     * key is missing when it is actually the model (or the base URL).
     */
    private suspend fun configGapFa(requireModel: Boolean): String {
        val settings = settingsRepository.settings.first()
        return missingConfigMessageFa(
            hasKey = secureKeyStore.hasApiKey(),
            hasBaseUrl = settings.aiBaseUrl.isNotBlank(),
            hasModel = settings.aiModel.isNotBlank(),
            requireModel = requireModel,
        ) ?: NO_KEY_FA
    }

    private fun httpCall(method: String, url: String, key: String, body: String?): HttpOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty(AUTH_HEADER, AUTH_PREFIX + key)
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
        val bodySnippet = body.trim().take(300)
        val reason = when (code) {
            401, 403 -> "کلید API نامعتبر است یا اجازه دسترسی ندارد."
            404 -> "نشانی سرور یا نام مدل پیدا نشد. نشانی پایه و نام مدل را بررسی کن."
            429 -> "سهمیه یا نرخ درخواست‌ها تمام شده است؛ کمی بعد دوباره امتحان کن."
            in 500..599 -> "سرور شخص ثالث خطا داده است؛ بعداً دوباره امتحان کن."
            else -> "درخواست رد شد."
        }
        return buildString {
            append(reason)
            append(" (کد HTTP: ")
            append(code.toPersianDigits())
            append(")")
            if (bodySnippet.isNotEmpty()) {
                append("\nپاسخ سرور: ")
                append(bodySnippet)
            }
        }
    }

    companion object {
        const val ID = "openai_compatible"
        const val DISPLAY_NAME_FA = "سازگار با OpenAI (Codecraft و مشابه)"

        // --- Single place to change endpoints/auth — per user requirement ---
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        const val MODELS_PATH = "/models"
        const val AUTH_HEADER = "Authorization"
        const val AUTH_PREFIX = "Bearer "

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000

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
        const val NO_BASE_URL_FA = "نشانی سرور (Base URL) خالی است؛ مثل https://codecraftapi.com/v1 واردش کن."
        const val NO_MODEL_FA = "نام مدل انتخاب نشده است. با دکمهٔ «دریافت فهرست مدل‌ها» یکی را انتخاب کن یا در کادر «نام مدل» بنویس."
        const val BAD_REPLY_FA = "پاسخ سرور قابل خواندن نبود. مدل دیگری را امتحان کن یا دوباره تلاش کن."
        const val FALLBACK_TITLE_FA = "پرامپت ساخته‌شده با هوش مصنوعی"
    }
}
