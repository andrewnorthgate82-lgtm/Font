package com.promptsaz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promptsaz.app.domain.model.AppSettings
import com.promptsaz.app.domain.repository.SettingsRepository
import com.promptsaz.app.ui.nav.AppNavHost
import com.promptsaz.app.ui.theme.PromptSazTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Persian-only app: force RTL regardless of system language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PromptSazTheme(themeMode = settings.themeMode) {
                    AppNavHost()
                }
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}
