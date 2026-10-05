package com.promptsaz.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.promptsaz.app.domain.model.ArchivedPrompt
import com.promptsaz.app.domain.model.ChatConversation
import com.promptsaz.app.domain.model.ChatMessage
import com.promptsaz.app.domain.model.ImageGeneration
import com.promptsaz.app.domain.model.ThemeMode
import com.promptsaz.app.domain.repository.ChatRepository
import com.promptsaz.app.domain.repository.ImageRepository
import com.promptsaz.app.domain.repository.PromptRepository
import com.promptsaz.app.ui.theme.PromptSazTheme
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * REAL graphic test: renders the opened app menu with Robolectric's native
 * graphics, captures the actual pixels and checks the drawer sheet is an
 * opaque surface that hides the screen behind it — the exact bug where the
 * menu titles overlapped the background text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DrawerGraphicsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openMenuWithBlackScreenBehind() {
        // Robolectric + the drawer's anchored-draggable keep scheduling frame
        // callbacks, which starves the default idle-sync. Screenshot recipe:
        // stop the auto clock, jump past every animation, capture as-is.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PromptSazTheme(themeMode = ThemeMode.DARK) {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    drawerContent = {
                        AppMenuDrawer(
                            conversations = emptyList(),
                            prompts = emptyList(),
                            generations = emptyList(),
                            currentRoute = null,
                            viewModel = AppMenuViewModel(NoChatRepo, NoPromptRepo, NoImageRepo),
                            onOpenChat = {},
                            onOpenConversation = {},
                            onOpenPromptTab = {},
                            onOpenPrompt = {},
                            onOpenArchive = {},
                            onOpenKb = {},
                            onOpenImage = {},
                            onOpenGeneration = {},
                            onOpenSettings = {},
                            onOpenAbout = {},
                            onDismiss = {},
                        )
                    },
                ) {
                    // the «screen behind»: a pure-black canvas — if the drawer
                    // were still see-through, its pixels would stay black.
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        // one jump past all slide/spring/settle animations
        compose.mainClock.advanceTimeBy(5_000)
    }

    @Test
    fun `menu sheet is opaque and hides the screen behind it`() {
        openMenuWithBlackScreenBehind()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val w = bitmap.width
        val h = bitmap.height
        val black = Color.Black.toArgb()

        // The drawer sheet owns the trailing ~88% of the screen. Its far edge
        // (inside the 16dp content padding, away from text and icons) must be
        // the sheet's own OPAQUE color at EVERY height — top to bottom.
        val failures = mutableListOf<String>()
        for (i in 1..9) {
            val y = h * i / 10
            val pixel = bitmap.getPixel(w - 4, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha != 0xFF || pixel == black) {
                failures += "y=$y pixel=$pixel"
            }
        }
        assertTrue(
            "drawer looks see-through at: ${failures.joinToString()}",
            failures.isEmpty(),
        )
    }
}

// ------------------------------------------------------------------ fakes ----

private object NoChatRepo : ChatRepository {
    override fun conversations(): Flow<List<ChatConversation>> = MutableStateFlow(emptyList())
    override fun messages(conversationId: Long): Flow<List<ChatMessage>> = MutableStateFlow(emptyList())
    override suspend fun createConversation(): Long = 0L
    override suspend fun autoTitle(conversationId: Long) {}
    override suspend fun deleteConversation(conversationId: Long) {}
    override suspend fun setFeedback(messageId: Long, feedback: Int) {}
    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        image: InputStream?,
        imageExtension: String?,
    ): Result<Long> = Result.failure(UnsupportedOperationException("not needed in this test"))
    override fun readImage(fileName: String): ByteArray? = null
}

private object NoPromptRepo : PromptRepository {
    override fun observePrompts(
        query: String,
        domainId: String?,
        favoritesOnly: Boolean,
    ): Flow<List<ArchivedPrompt>> = MutableStateFlow(emptyList())
    override fun observePrompt(id: Long): Flow<ArchivedPrompt?> = MutableStateFlow(null)
    override fun observeCount(): Flow<Int> = MutableStateFlow(0)
    override suspend fun getPrompt(id: Long): ArchivedPrompt? = null
    override suspend fun getAll(): List<ArchivedPrompt> = emptyList()
    override suspend fun save(prompt: ArchivedPrompt): Long = 0L
    override suspend fun insertAll(prompts: List<ArchivedPrompt>) {}
    override suspend fun update(prompt: ArchivedPrompt) {}
    override suspend fun delete(id: Long) {}
    override suspend fun toggleFavorite(id: Long) {}
    override suspend fun duplicate(id: Long): Long? = null
    override suspend fun allTags(): List<String> = emptyList()
}

private object NoImageRepo : ImageRepository {
    override fun history(): Flow<List<ImageGeneration>> = MutableStateFlow(emptyList())
    override suspend fun generate(
        prompt: String,
        model: String,
        size: String,
        serviceId: String?,
    ): Result<ImageGeneration> = Result.failure(UnsupportedOperationException("not needed in this test"))
    override fun readImage(fileName: String): ByteArray? = null
    override suspend fun delete(generationId: Long) {}
    override suspend fun generateImagePrompt(
        prompt: String,
        model: String,
        serviceId: String?,
    ): Result<String> = Result.failure(UnsupportedOperationException("not needed in this test"))
}
