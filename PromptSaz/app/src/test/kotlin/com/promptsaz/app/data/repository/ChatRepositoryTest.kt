package com.promptsaz.app.data.repository

import com.promptsaz.app.data.db.ChatConversationEntity
import com.promptsaz.app.data.db.ChatDao
import com.promptsaz.app.data.db.ChatMessageEntity
import com.promptsaz.app.data.db.ImageGenerationDao
import com.promptsaz.app.data.db.ImageGenerationEntity
import com.promptsaz.app.data.files.ImageFileStore
import com.promptsaz.app.domain.model.ChatTurn
import com.promptsaz.app.domain.provider.ChatAiProvider
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.GeneratedImage
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.repository.SettingsRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the chat send flow: the user message (with its image) is persisted,
 * the turn history is built with the system prompt, only the newest message
 * carries its image as a data URL and older attachments degrade to a
 * placeholder — then the reply is persisted.
 */
class ChatRepositoryTest {

    // --- fakes -------------------------------------------------------------------

    private class FakeChatDao : ChatDao {
        val conversations = mutableListOf<ChatConversationEntity>()
        val messages = mutableListOf<ChatMessageEntity>()
        private var nextId = 1L

        override fun observeConversations(): Flow<List<ChatConversationEntity>> =
            MutableStateFlow(conversations.toList())

        override fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>> =
            MutableStateFlow(messages.filter { it.conversationId == conversationId })

        override suspend fun messages(conversationId: Long): List<ChatMessageEntity> =
            messages.filter { it.conversationId == conversationId }

        override suspend fun insertConversation(conversation: ChatConversationEntity): Long {
            val id = nextId++
            conversations.add(conversation.copy(id = id))
            return id
        }

        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            val id = nextId++
            messages.add(message.copy(id = id))
            return id
        }

        override suspend fun renameConversation(conversationId: Long, title: String, updatedAt: Long) {
            val index = conversations.indexOfFirst { it.id == conversationId }
            if (index >= 0) {
                conversations[index] = conversations[index].copy(title = title, updatedAt = updatedAt)
            }
        }

        override suspend fun touchConversation(conversationId: Long, updatedAt: Long) {
            val index = conversations.indexOfFirst { it.id == conversationId }
            if (index >= 0) {
                conversations[index] = conversations[index].copy(updatedAt = updatedAt)
            }
        }

        override suspend fun deleteMessagesOf(conversationId: Long) {
            messages.removeAll { it.conversationId == conversationId }
        }

        override suspend fun deleteConversation(conversationId: Long) {
            conversations.removeAll { it.id == conversationId }
        }
    }

    private class FakeProvider : ChatAiProvider {
        var lastModel: String? = null
        var lastTurns: List<ChatTurn> = emptyList()
        var reply: String = "پاسخ دستیار برای تست."

        override suspend fun chat(model: String, turns: List<ChatTurn>): Result<String> {
            lastModel = model
            lastTurns = turns
            return Result.success(reply)
        }

        override suspend fun generateImage(model: String, prompt: String, size: String): Result<GeneratedImage> =
            Result.failure(IllegalStateException("not used"))

        override suspend fun fetchImageBytes(url: String): Result<ByteArray> =
            Result.failure(IllegalStateException("not used"))
    }

    private class FakeImageStore : ImageFileStore {
        val saved = mutableListOf<ByteArray>()
        var nextName = 1

        override fun saveChatImage(bytes: ByteArray, extension: String): String = "img-${nextName++}.jpg"
        override fun saveGeneratedImage(bytes: ByteArray, extension: String): String = "gen-${nextName++}.png"
        override fun readChatImage(fileName: String): ByteArray? = saved.firstOrNull()
        override fun readGeneratedImage(fileName: String): ByteArray? = saved.firstOrNull()
        override fun deleteGeneratedImage(fileName: String) {}
        override fun compressForUpload(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray = bytes
        override fun toDataUrl(bytes: ByteArray): String =
            "data:image/jpeg;base64," + bytes.decodeToString()
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val flow = MutableStateFlow(AppSettings(aiModel = "selected-model"))
        override val settings: Flow<AppSettings> = flow
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDefaultDomain(domainId: String) {}
        override suspend fun setDefaultTarget(target: TargetAi) {}
        override suspend fun setDefaultLanguage(language: OutputLanguage) {}
        override suspend fun setDefaultDetail(level: DetailLevel) {}
        override suspend fun setAiEnabled(enabled: Boolean) {}
        override suspend fun setAiBaseUrl(url: String) {}
        override suspend fun setAiModel(model: String) { flow.value = flow.value.copy(aiModel = model) }
        override suspend fun setAiImageModel(model: String) { flow.value = flow.value.copy(aiImageModel = model) }
    }

    // NOTE: ImageGenerationDao is not part of this test; ChatDao fake above
    // implements only ChatDao. (Unused reference kept out on purpose.)
    @Suppress("unused")
    private class UnusedImageDao : ImageGenerationDao {
        override fun observeAll(): Flow<List<ImageGenerationEntity>> = MutableStateFlow(emptyList())
        override suspend fun insert(generation: ImageGenerationEntity): Long = 1L
        override suspend fun delete(generationId: Long) {}
    }

    private fun repository(
        dao: FakeChatDao = FakeChatDao(),
        provider: FakeProvider = FakeProvider(),
    ) = Triple(
        dao,
        provider,
        ChatRepositoryImpl(
            chatDao = dao,
            provider = provider,
            imageStore = FakeImageStore(),
            settingsRepository = FakeSettingsRepository(),
        ),
    )

    @Test
    fun `send persists user message and reply, and sends system prompt + model`() = runBlocking {
        val (dao, provider, repo) = repository()

        val result = repo.sendMessage(0L, "سلام، چه خبر؟", image = null, imageExtension = null)

        assertTrue("send failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        // conversation created + titled from the first message
        assertEquals(1, dao.conversations.size)
        assertEquals("سلام، چه خبر؟", dao.conversations.single().title)
        // user + assistant persisted
        val persisted = dao.messages.sortedBy { it.id }
        assertEquals(2, persisted.size)
        assertEquals("user", persisted[0].role)
        assertEquals("سلام، چه خبر؟", persisted[0].text)
        assertEquals("assistant", persisted[1].role)
        assertEquals("پاسخ دستیار برای تست.", persisted[1].text)
        // provider saw the selected model + system prompt
        assertEquals("selected-model", provider.lastModel)
        assertEquals("system", provider.lastTurns.first().role)
        assertTrue(provider.lastTurns.first().text.contains("پرامپت‌ساز"))
    }

    @Test
    fun `attached image is stored and travels as a data url on the newest turn only`() = runBlocking {
        val (dao, provider, repo) = repository()
        val imageBytes = "FAKEJPG".toByteArray()

        repo.sendMessage(0L, "این تصویر را تحلیل کن", ByteArrayInputStream(imageBytes), "jpg")
        val firstUserTurn = provider.lastTurns.last()
        assertEquals("user", firstUserTurn.role)
        assertNotNull(firstUserTurn.imageDataUrl)
        assertTrue(firstUserTurn.imageDataUrl!!.startsWith("data:image/jpeg;base64,"))
        // the image file was persisted on the message row
        assertNotNull(dao.messages.first { it.role == "user" }.imageFileName)

        // second send with another image: the FIRST image must degrade to a placeholder
        repo.sendMessage(dao.conversations.single().id, "حالا این یکی چی؟", ByteArrayInputStream("SECOND".toByteArray()), "jpg")

        val turns = provider.lastTurns
        val userTurns = turns.filter { it.role == "user" }
        assertEquals(2, userTurns.size)
        // oldest user turn: no image, placeholder text appended
        assertTrue("old image should not re-upload", userTurns[0].imageDataUrl == null)
        assertTrue(
            "placeholder expected on old turn: ${userTurns[0].text}",
            userTurns[0].text.contains(ChatRepositoryImpl.IMAGE_PLACEHOLDER_FA),
        )
        // newest user turn: carries the new data URL
        assertNotNull(userTurns[1].imageDataUrl)
        assertTrue(userTurns[1].imageDataUrl!!.contains("SECOND"))
        // history keeps growing in Room
        assertEquals(4, dao.messages.size)
    }

    @Test
    fun `provider failure keeps the user message and surfaces the persian error`() = runBlocking {
        val dao = FakeChatDao()
        val provider = object : FakeProvider() {
            override suspend fun chat(model: String, turns: List<ChatTurn>): Result<String> =
                Result.failure(IllegalStateException("کلید API نامعتبر است."))
        }
        val repo = ChatRepositoryImpl(dao, provider, FakeImageStore(), FakeSettingsRepository())

        val result = repo.sendMessage(0L, "سلام", null, null)

        assertTrue(result.isFailure)
        assertEquals("کلید API نامعتبر است.", result.exceptionOrNull()?.message)
        // the user's message is still there — retry is possible
        assertEquals(1, dao.messages.size)
        assertEquals("user", dao.messages.single().role)
        assertEquals("سلام", dao.messages.single().text)
    }
}
