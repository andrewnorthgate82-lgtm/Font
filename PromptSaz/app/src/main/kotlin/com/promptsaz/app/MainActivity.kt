package com.promptsaz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.ui.nav.AppNavHost
import com.promptsaz.app.ui.theme.PromptSazTheme
import dagger.hilt.android.AndroidEntryPoint

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
