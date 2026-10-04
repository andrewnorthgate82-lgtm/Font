package com.promptsaz.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptsaz.app.ui.nav.AppNavHost
import com.promptsaz.app.ui.theme.PromptSazTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Section requested by the home-screen widget / an app shortcut. */
    private val pendingSection = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingSection.value = sectionOf(intent)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Persian-only app: force RTL regardless of system language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                PromptSazTheme(themeMode = settings.themeMode) {
                    AppNavHost(
                        openSection = pendingSection.value,
                        onSectionHandled = { pendingSection.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingSection.value = sectionOf(intent)
    }

    private fun sectionOf(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_OPEN_SECTION)?.takeIf { it in SECTIONS }

    companion object {
        const val EXTRA_OPEN_SECTION = "open_section"
        const val SECTION_CHAT = "chat"
        const val SECTION_PROMPT_NEW = "prompt_new"
        const val SECTION_IMAGE = "image"
        private val SECTIONS = setOf(SECTION_CHAT, SECTION_PROMPT_NEW, SECTION_IMAGE)
    }
}
