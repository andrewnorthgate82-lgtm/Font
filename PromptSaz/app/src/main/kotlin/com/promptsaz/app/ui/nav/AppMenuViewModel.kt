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

/**
 * Feeds the unified app menu: the recent history of every section (گفتگو /
 * تولید پرامپت / تولید تصویر) so the drawer can deep-link straight into an
 * old conversation, prompt or generated image.
 */
@HiltViewModel
class AppMenuViewModel @Inject constructor(
    chatRepository: ChatRepository,
    promptRepository: PromptRepository,
    imageRepository: ImageRepository,
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
}
