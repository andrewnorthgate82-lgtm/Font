package com.promptsaz.app.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.ImageRepository
import com.promptsaz.app.domain.repository.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds the unified app menu: the recent history of every section (گفتگو /
 * تولید پرامپت / تولید تصویر) so the drawer can deep-link straight into an
 * old conversation, prompt or generated image — plus the multi-select actions
 * (delete / share) and the thumbnail reader of past generations.
 */
@HiltViewModel
class AppMenuViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val promptRepository: PromptRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    val conversations: StateFlow<List<ChatConversation>> =
        chatRepository.conversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prompts: StateFlow<List<ArchivedPrompt>> =
        promptRepository.observePrompts(query = "", domainId = null, favoritesOnly = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val generations: StateFlow<List<ImageGeneration>> =
        imageRepository.history()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- multi-select actions --------------------------------------------------

    fun deleteConversations(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { chatRepository.deleteConversation(it) } }
    }

    fun deletePrompts(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { promptRepository.delete(it) } }
    }

    fun deleteGenerations(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { imageRepository.delete(it) } }
    }

    /** Bytes of one saved generation — the drawer's thumbnail source. */
    fun readGenerationImage(fileName: String): ByteArray? = imageRepository.readImage(fileName)
}
