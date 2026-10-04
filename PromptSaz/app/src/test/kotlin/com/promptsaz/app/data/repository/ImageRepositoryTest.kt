package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.ImageGenerationDao
import com.promptsaz.app.data.db.ImageGenerationEntity
import com.promptsaz.app.data.files.ImageFileStore
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.provider.ChatAiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the image-prompt fallback: services without an images endpoint can
 * still turn the user's description into a professional image prompt via the
 * chat endpoint, using the model selected in the تصویر tab.
 */
class ImageRepositoryTest {

    private class FakeDao : ImageGenerationDao {
        val entities = mutableListOf<ImageGenerationEntity>()
        override fun observeAll(): Flow<List<ImageGenerationEntity>> = MutableStateFlow(entities.toList())
        override suspend fun insert(generation: ImageGenerationEntity): Long {
            entities += generation
            return entities.size.toLong()
        }
        override suspend fun delete(generationId: Long) {
            entities.removeAll { it.id == generationId }
        }
    }

    private class FakeStore : ImageFileStore {
        override fun saveChatImage(bytes: ByteArray, extension: String): String = "chat.jpg"
        override fun saveGeneratedImage(bytes: ByteArray, extension: String): String = "gen.png"
        override fun readChatImage(fileName: String): ByteArray? = null
        override fun readGeneratedImage(fileName: String): ByteArray? = null
        override fun deleteGeneratedImage(fileName: String) {}
        override fun compressForUpload(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray = bytes
        override fun toDataUrl(bytes: ByteArray): String = "data:image/jpeg;base64,QUJD"
    }

    private open class FakeProvider : ChatAiProvider {
        var lastModel: String? = null
        var lastTurns: List<ChatTurn> = emptyList()
        var reply: String = "  cinematic photo, golden hour, warm tones  "

        override suspend fun chat(model: String, turns: List<ChatTurn>, serviceId: String?): Result<String> {
            lastModel = model
            lastTurns = turns
            return Result.success(reply)
        }

        override suspend fun generateImage(model: String, prompt: String, size: String, serviceId: String?): Result<GeneratedImage> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun fetchImageBytes(url: String): Result<ByteArray> =
            Result.failure(IllegalStateException("not used"))
    }

    @Test
    fun `generateImagePrompt sends the persona and the description to the selected model`() = runBlocking {
        val provider = FakeProvider()
        val repository = ImageRepositoryImpl(FakeDao(), provider, FakeStore())

        val result = repository.generateImagePrompt("گربهٔ نارنجی روی کاناپهٔ مخملی", "gemini-3.7-flash")

        assertTrue(result.isSuccess)
        // the reply is trimmed before being shown
        assertEquals("cinematic photo, golden hour, warm tones", result.getOrNull())
        // the model picked in the تصویر tab is the one that gets called
        assertEquals("gemini-3.7-flash", provider.lastModel)
        assertEquals(2, provider.lastTurns.size)
        assertEquals("system", provider.lastTurns.first().role)
        assertTrue("persona missing", provider.lastTurns.first().text.contains("Midjourney"))
        assertEquals("user", provider.lastTurns.last().role)
        assertTrue("description missing", provider.lastTurns.last().text.contains("گربه"))
    }

    @Test
    fun `generateImagePrompt rejects a blank description with the persian message`() = runBlocking {
        val repository = ImageRepositoryImpl(FakeDao(), FakeProvider(), FakeStore())

        val result = repository.generateImagePrompt("   ", "model-x")

        assertTrue(result.isFailure)
        assertEquals(ImageRepositoryImpl.EMPTY_PROMPT_FA, result.exceptionOrNull()?.message)
    }

    @Test
    fun `generateImagePrompt surfaces the provider failure untouched`() = runBlocking {
        val provider = object : FakeProvider() {
            override suspend fun chat(model: String, turns: List<ChatTurn>, serviceId: String?): Result<String> =
                Result.failure(IllegalStateException("کلید API نامعتبر است."))
        }
        val repository = ImageRepositoryImpl(FakeDao(), provider, FakeStore())

        val result = repository.generateImagePrompt("توصیف", "model-x")

        assertTrue(result.isFailure)
        assertEquals("کلید API نامعتبر است.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `generateImagePrompt rejects an empty model reply with the persian message`() = runBlocking {
        val provider = FakeProvider().apply { reply = "   " }
        val repository = ImageRepositoryImpl(FakeDao(), provider, FakeStore())

        val result = repository.generateImagePrompt("توصیف", "model-x")

        assertTrue(result.isFailure)
        assertEquals(ImageRepositoryImpl.BAD_PROMPT_REPLY_FA, result.exceptionOrNull()?.message)
    }
}
