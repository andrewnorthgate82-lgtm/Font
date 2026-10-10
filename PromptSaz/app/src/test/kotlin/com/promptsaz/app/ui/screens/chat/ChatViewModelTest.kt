package com.promptsaz.app.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import com.promptsaz.app.data.settings.ApiKeyStore
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.TargetAi
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.model.UserAttachment
import com.promptsaz.app.domain.provider.ProviderRegistry
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the حافظه (app-menu memory) bug: tapping a saved
 * conversation must actually open it — both via the SavedStateHandle deep
 * link (fresh entry) and via an explicit openConversation call (reused entry).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private class FakeChatRepository : ChatRepository {
        val conversations = MutableStateFlow(
            listOf(
                ChatConversation(id = 5L, title = "پایگاه دانش", createdAt = 1L, updatedAt = 1L),
                ChatConversation(id = 6L, title = "گفتگوی دیگر", createdAt = 2L, updatedAt = 2L),
            ),
        )

        override fun conversations(): Flow<List<ChatConversation>> = conversations

        override fun messages(conversationId: Long): Flow<List<ChatMessage>> = flowOf(
            listOf(
                ChatMessage(id = conversationId * 100, conversationId = conversationId, role = "user", text = "پیام کاربر $conversationId", createdAt = 1L),
                ChatMessage(id = conversationId * 100 + 1, conversationId = conversationId, role = "assistant", text = "پیام دستیار $conversationId", createdAt = 2L),
            ),
        )

        override suspend fun createConversation(): Long = 7L
        override suspend fun autoTitle(conversationId: Long) {}
        override suspend fun deleteConversation(conversationId: Long) {}
        override suspend fun setFeedback(messageId: Long, feedback: Int) {}
        override suspend fun sendMessage(
            conversationId: Long,
            text: String,
            attachments: List<UserAttachment>,
        ): Result<Long> = Result.success(conversationId)
        override fun readImage(fileName: String): ByteArray? = null
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setDefaultDomain(domainId: String) {}
        override suspend fun setDefaultTarget(target: TargetAi) {}
        override suspend fun setDefaultLanguage(language: OutputLanguage) {}
        override suspend fun setDefaultDetail(level: DetailLevel) {}
        override suspend fun setAiEnabled(enabled: Boolean) {}
        override suspend fun setAiBaseUrl(url: String) {}
        override suspend fun setAiModel(model: String) {}
        override suspend fun setAiImageModel(model: String) {}
        override suspend fun addService(name: String, baseUrl: String, type: String): String = "svc"
        override suspend fun updateService(id: String, name: String, baseUrl: String, type: String) {}
        override suspend fun removeService(id: String) {}
        override suspend fun setActiveService(id: String) {}
        override suspend fun setModeService(modeId: String, serviceId: String) {}
        override suspend fun setServiceModel(serviceId: String, model: String, imageModel: Boolean) {}
    }

    private class FakeKeyStore : ApiKeyStore {
        override fun hasApiKey(serviceId: String): Boolean = false
        override fun getApiKey(serviceId: String): String? = null
        override fun saveApiKey(serviceId: String, value: String) {}
        override fun clearApiKey(serviceId: String) {}
    }

    private val repo = FakeChatRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ChatViewModel(
            chatRepository = repo,
            settingsRepository = FakeSettingsRepository(),
            providerRegistry = ProviderRegistry(emptySet()),
            keyStore = FakeKeyStore(),
            savedStateHandle = savedStateHandle,
        )

    @Test
    fun `a conversationId deep link opens that conversation`() {
        val vm = viewModel(SavedStateHandle(mapOf("conversationId" to 5L)))

        assertEquals(5L, vm.uiState.value.conversationId)
        assertEquals(2, vm.uiState.value.messages.size)
        assertEquals("پیام دستیار 5", vm.uiState.value.messages.last().text)
    }

    @Test
    fun `a zero or missing conversationId keeps a fresh conversation`() {
        val vm = viewModel(SavedStateHandle(mapOf("conversationId" to 0L)))

        assertEquals(0L, vm.uiState.value.conversationId)
        assertEquals(0, vm.uiState.value.messages.size)
    }

    @Test
    fun `openConversation switches the visible messages`() {
        val vm = viewModel()

        vm.openConversation(5L)
        assertEquals(listOf("پیام کاربر 5", "پیام دستیار 5"), vm.uiState.value.messages.map { it.text })

        // tapping ANOTHER حافظه must replace the messages, not append or freeze
        vm.openConversation(6L)
        assertEquals(2, vm.uiState.value.messages.size)
        assertEquals("پیام دستیار 6", vm.uiState.value.messages.last().text)
    }

    @Test
    fun `newConversation resets to a blank chat`() {
        val vm = viewModel()
        vm.openConversation(5L)

        vm.newConversation()

        assertEquals(0L, vm.uiState.value.conversationId)
        assertEquals(0, vm.uiState.value.messages.size)
    }
}
