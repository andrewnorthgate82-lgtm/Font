package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.ImageGenerationDao
import com.promptsaz.app.data.db.ImageGenerationEntity
import com.promptsaz.app.data.db.toDomain
import com.promptsaz.app.data.files.ImageFileStore
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.domain.provider.AiModePrompts
import com.promptsaz.app.domain.provider.ChatAiProvider
import com.promptsaz.app.domain.repository.ImageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Image-studio flow: call the images endpoint, download/decode the result,
 * save it into app storage and record it in the history.
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val dao: ImageGenerationDao,
    private val provider: ChatAiProvider,
    private val imageStore: ImageFileStore,
) : ImageRepository {

    override fun history(): Flow<List<ImageGeneration>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun generate(
        prompt: String,
        model: String,
        size: String,
        serviceId: String?,
    ): Result<ImageGeneration> {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            return Result.failure(IllegalStateException(EMPTY_PROMPT_FA))
        }
        return provider.generateImage(model, trimmedPrompt, size, serviceId).mapCatching { generated ->
            val bytes = when (generated) {
                is GeneratedImage.FromUrl ->
                    provider.fetchImageBytes(generated.url).getOrElse { error ->
                        throw IllegalStateException(DOWNLOAD_FAILED_FA + " (${error.message ?: ""})")
                    }
                is GeneratedImage.FromBase64 ->
                    android.util.Base64.decode(generated.base64, android.util.Base64.DEFAULT)
            }
            if (bytes.isEmpty()) throw IllegalStateException(DOWNLOAD_FAILED_FA)
            val extension = when (generated) {
                is GeneratedImage.FromUrl ->
                    generated.url.substringAfterLast('.', "png").take(4).ifBlank { "png" }
                is GeneratedImage.FromBase64 -> "png"
            }
            val fileName = imageStore.saveGeneratedImage(bytes, extension)
            val entity = ImageGenerationEntity(
                prompt = trimmedPrompt,
                model = model,
                fileName = fileName,
                size = size,
                createdAt = System.currentTimeMillis(),
            )
            entity.copy(id = dao.insert(entity)).toDomain()
        }
    }

    override suspend fun generateImagePrompt(prompt: String, model: String, serviceId: String?): Result<String> {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) {
            return Result.failure(IllegalStateException(EMPTY_PROMPT_FA))
        }
        val turns = listOf(
            ChatTurn(role = "system", text = AiModePrompts.IMAGE_PROMPT_SYSTEM_FA),
            ChatTurn(role = "user", text = trimmedPrompt),
        )
        return provider.chat(model, turns, serviceId).mapCatching { reply ->
            reply.trim().ifBlank { throw IllegalStateException(BAD_PROMPT_REPLY_FA) }
        }
    }

    override fun readImage(fileName: String): ByteArray? = imageStore.readGeneratedImage(fileName)

    override suspend fun delete(generationId: Long) {
        dao.delete(generationId)
    }

    companion object {
        const val EMPTY_PROMPT_FA = "برای ساخت تصویر، اول توصیف تصویر دلخواهت را بنویس."
        const val BAD_PROMPT_REPLY_FA = "مدل پاسخ مناسبی برای پرامپت تصویر نداد؛ دوباره تلاش کن یا مدل دیگری را امتحان کن."
        const val DOWNLOAD_FAILED_FA = "تصویر ساخته شد اما دریافت آن از سرور ناموفق بود؛ دوباره تلاش کن."
    }
}
