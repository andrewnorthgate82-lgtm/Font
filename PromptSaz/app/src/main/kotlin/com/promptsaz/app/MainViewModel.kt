package com.promptsaz.app

import androidx.lifecycle.ViewModel
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

/**
 * App-level ViewModel: exposes settings (theme) to the activity.
 * Kept in its own file — a @HiltViewModel sharing a file with an
 * @AndroidEntryPoint class breaks Hilt's KSP2 annotation resolution.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}
