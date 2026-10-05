package com.promptsaz.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 * REAL graphic test of the reported menu bug: the open menu was see-through,
 * its titles overlapping the background text. Renders the actual menu sheet
 * (Robolectric native graphics) over a pure-black "screen", captures the
 * pixels and checks the sheet is an opaque surface that hides what is behind
 * it — at its far edge, where only the sheet itself is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DrawerGraphicsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `menu sheet is opaque and hides the screen behind it`() {
        // No drawer-state, no animations — a purely static frame, so the
        // compose idle-sync cannot starve in Robolectric.
        compose.setContent {
            PromptSazTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                }
            }
        }

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val w = bitmap.width
        val h = bitmap.height
        val black = Color.Black.toArgb()

        // Rows inside the menu content (the sheet starts at the top; its
        // content — header + sections — is several hundred px tall).
        for (y in listOf(60, 120, 180, 240, 300)) {
            // rightmost non-black pixel of this row = the sheet's far edge
            var edgeX = -1
            for (x in w - 1 downTo 0) {
                if (bitmap.getPixel(x, y) != black) { edgeX = x; break }
            }
            assertTrue("no menu surface found at y=$y (edgeX=$edgeX)", edgeX > w / 3)

            // 8px inside the edge lies in the sheet's content padding — only
            // the sheet's own opaque color can be drawn there. With the old
            // see-through drawer this was the black screen behind it.
            val pixel = bitmap.getPixel(edgeX - 8, y)
            val alpha = (pixel ushr 24) and 0xFF
            assertTrue(
                "menu sheet looks see-through at y=$y (edge=$edgeX, pixel=$pixel)",
                alpha == 0xFF && pixel != black,
            )
        }
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
